package net.msrandom.stub

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlin.io.path.Path
import kotlin.reflect.KClass

const val CANT_RUN_COMMON = "throw UnsupportedOperationException(\"\"\"\nCommon modules are not runnable\n\"\"\")"
private val CONSTRUCTOR_LESS_KINDS = setOf(ClassKind.OBJECT, ClassKind.INTERFACE)

class StubProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private fun mapModifiers(modifiers: Iterable<Modifier>) = modifiers.mapNotNull {
        if (it == Modifier.EXPECT) {
            null
        } else {
            it.toKModifier()
        }
    } + KModifier.ACTUAL

    private fun mapAnnotations(annotations: Sequence<KSAnnotation>) = annotations.map {
        val builder = AnnotationSpec.builder(it.annotationType.resolve().toClassName())

        for (argument in it.arguments) {
            val value = argument.value

            if (value is KClass<*>) {
                builder.addMember("%T::class", value.qualifiedName!!)
            } else if (value is String) {
                builder.addMember("\"%s\"", value)
            } else {
                builder.addMember(value.toString())
            }
        }

        builder.build()
    }.toList()

    private fun fileSpec(
        declaration: KSDeclaration,
        declarations: MutableMap<KSName, FileSpec.Builder>,
    ): FileSpec.Builder? {
        if (Modifier.ACTUAL in declaration.modifiers) {
            return null
        }

        if (Modifier.EXPECT !in declaration.modifiers) {
            throw UnsupportedOperationException("Only expect members can be @Stub, but $declaration is not expect")
        }

        val info = declarations.computeIfAbsent(declaration.packageName) {
            val spec = FileSpec.builder(
                declaration.packageName.asString(),
                declaration.packageName.asString().replace('.', '-'),
            )

            spec
        }

        var inFileDeclaration: KSDeclaration? = declaration

        while (inFileDeclaration != null && inFileDeclaration.containingFile == null) {
            inFileDeclaration = inFileDeclaration.parentDeclaration
        }

        return info
    }

    private fun generate(type: KSClassDeclaration, declarations: MutableMap<KSName, FileSpec.Builder>) {
        val spec = fileSpec(type, declarations) ?: return

        val typeBuilder = when (type.classKind) {
            ClassKind.INTERFACE -> TypeSpec.interfaceBuilder(type.simpleName.asString())
            ClassKind.CLASS -> TypeSpec.classBuilder(type.simpleName.asString())
            ClassKind.ENUM_CLASS -> TypeSpec.enumBuilder(type.simpleName.asString())
            ClassKind.ENUM_ENTRY -> throw UnsupportedOperationException("Can not generate stub actual for $type")
            ClassKind.OBJECT -> TypeSpec.objectBuilder(type.simpleName.asString())
            ClassKind.ANNOTATION_CLASS -> TypeSpec.annotationBuilder(type.simpleName.asString())
        }

        typeBuilder.addModifiers(mapModifiers(type.modifiers))

        typeBuilder.addAnnotations(mapAnnotations(type.annotations))

        val canHaveConstructor = type.classKind !in CONSTRUCTOR_LESS_KINDS

        if (type.classKind == ClassKind.ENUM_CLASS) {
            for (declaration in type.declarations) {
                typeBuilder.addEnumConstant(declaration.simpleName.asString())
            }
        } else {
            if (canHaveConstructor) {
                typeBuilder.primaryConstructor(type.primaryConstructor?.let { functionSpec(it, true) })
            }

            typeBuilder.addFunctions(
                type.getDeclaredFunctions()
                    .run {
                        if (!canHaveConstructor) {
                            filterNot(KSFunctionDeclaration::isConstructor)
                        } else {
                            filterNot { it == type.primaryConstructor }
                        }
                    }
                    .map(::functionSpec)
                    .toList()
            )

            typeBuilder.addProperties(type.getDeclaredProperties().map(::propertySpec).toList())
        }

        // TODO Use Any::class.qualifiedName
        typeBuilder.addSuperinterfaces(
            type.superTypes.map(KSTypeReference::toTypeName).filterNot { it.toString().endsWith("Any") }.toList()
        )

        spec.addType(typeBuilder.build())
    }

    private fun functionSpec(function: KSFunctionDeclaration, primaryConstructor: Boolean = false): FunSpec {
        val builder = if (function.isConstructor()) {
            FunSpec.constructorBuilder()
        } else {
            val builder = FunSpec.builder(function.simpleName.asString())
                .addCode("return $CANT_RUN_COMMON")

            function.returnType?.toTypeName()?.let(builder::returns)
            builder
        }

        // Adding modifiers to primary constructor results in `public public constructor(...)`
        if (!primaryConstructor) {
            function.extensionReceiver?.toTypeName()?.let(builder::receiver)
            builder.addModifiers(mapModifiers(function.modifiers))
        }

        builder.addAnnotations(mapAnnotations(function.annotations))

        for (parameter in function.parameters) {
            builder.addParameter(parameter.name!!.asString(), parameter.type.toTypeName())
        }

        return builder.build()
    }

    private fun propertySpec(property: KSPropertyDeclaration): PropertySpec {
        val builder = PropertySpec.builder(
            property.simpleName.asString(),
            property.type.toTypeName(),
            mapModifiers(property.modifiers),
        )

        builder.addAnnotations(mapAnnotations(property.annotations))

        val receiver = property.extensionReceiver
        builder.receiver(receiver?.toTypeName())
        builder.mutable(property.isMutable)

        if (receiver != null) {
            val getBuilder = FunSpec.getterBuilder()
            getBuilder.addCode("return $CANT_RUN_COMMON")
            builder.getter(getBuilder.build())

            if (property.isMutable) {
                val setBuilder = FunSpec.setterBuilder()
                setBuilder.parameters.add(ParameterSpec.builder("value", property.type.toTypeName()).build())
                setBuilder.addCode("return $CANT_RUN_COMMON")
                builder.setter(setBuilder.build())
            }
        } else {
            builder.initializer(CANT_RUN_COMMON)
        }

        return builder.build()
    }

    private fun generate(
        function: KSFunctionDeclaration,
        declarations: MutableMap<KSName, FileSpec.Builder>,
    ) {
        val spec = fileSpec(function, declarations) ?: return

        spec.addFunction(functionSpec(function))
    }

    private fun generate(
        property: KSPropertyDeclaration,
        declarations: MutableMap<KSName, FileSpec.Builder>,
    ) {
        val spec = fileSpec(property, declarations) ?: return

        spec.addProperty(propertySpec(property))
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val directory = environment.options["actualStubDir"]

        if (directory == null) {
            return emptyList()
        }

        val directoryPath = Path(directory)

        val toStub = resolver.getSymbolsWithAnnotation(Stub::class.qualifiedName!!)

        val declarations = hashMapOf<KSName, FileSpec.Builder>()

        for (annotated in toStub) {
            when (annotated) {
                is KSClassDeclaration -> generate(annotated, declarations)
                is KSFunctionDeclaration -> generate(annotated, declarations)
                is KSPropertyDeclaration -> generate(annotated, declarations)
                is KSPropertyAccessor -> generate(annotated.receiver, declarations)
            }
        }

        for (specBuilder in declarations.values) {
            val spec = specBuilder.build()

            spec.writeTo(directoryPath)
        }

        return emptyList()
    }
}

@Suppress("unused")
@AutoService(SymbolProcessorProvider::class)
class StubProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment) =
        StubProcessor(environment)
}

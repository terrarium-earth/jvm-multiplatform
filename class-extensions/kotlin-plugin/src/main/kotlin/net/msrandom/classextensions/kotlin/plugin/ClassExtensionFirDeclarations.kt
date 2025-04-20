package net.msrandom.classextensions.kotlin.plugin

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.Context
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.lightTree.converter.LightTreeRawFirDeclarationBuilder
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.constructStarProjectedType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class ClassExtensionFirDeclarations(session: FirSession, extensionFinder: LazyExtensionFinder) :
    FirDeclarationGenerationExtension(session) {
    private val resolver = FirClassExtensionResolver(session, extensionFinder)
    private val compiledExtensions = hashMapOf<ClassId, List<CompiledExtensionInfo>>()

    @OptIn(SymbolInternals::class)
    private fun compiledExtensions(owner: FirClassSymbol<*>) = compiledExtensions.computeIfAbsent(owner.classId) {
        val extensions = resolver.getExtensions(owner)

        val firBuilderContext = Context<LighterASTNode>().apply {
            packageFqName = owner.classId.packageFqName
            className = owner.classId.asSingleFqName()
            dispatchReceiverTypesStack.push(owner.constructStarProjectedType())
        }

        val results = mutableListOf<CompiledExtensionInfo>()

        for (extension in extensions) {
            val info = extension.info

            val functions = mutableListOf<FirSimpleFunction>()
            val properties = mutableListOf<FirProperty>()
            val constructors = mutableListOf<FirConstructor>()
            val nestedClassifiers = mutableListOf<FirRegularClass>()

            val builder = LightTreeRawFirDeclarationBuilder(
                session,
                session.kotlinScopeProvider,
                info.treeStructure,
                firBuilderContext
            )

            for (function in info.functions) {
                val firFunction = builder.convertFunctionDeclaration(function) as FirSimpleFunction

                functions.add(firFunction)
            }

            for (property in info.properties) {
                val firProperty = builder.convertPropertyDeclaration(property) as FirProperty

                properties.add(firProperty)
            }

            results.add(
                CompiledExtensionInfo(
                    extension,
                    functions,
                    properties,
                    constructors,
                    nestedClassifiers,
                )
            )
        }

        results
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        for (info in compiledExtensions(owner)) {
            val classifier = info.nestedClassifiers.firstOrNull { it.name == name } ?: continue

            return GeneratedDeclarationResolver.resolve(owner, info.context, classifier, session).symbol
        }

        return null
    }

    @OptIn(SymbolInternals::class)
    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val compiled = compiledExtensions(context!!.owner)

        return compiled.flatMap { extension ->
            extension.functions.mapNotNull {
                if (it.name == callableId.callableName) {
                    GeneratedDeclarationResolver.resolve(context.owner, extension.context, it, session).symbol
                } else {
                    null
                }
            }
        }
    }

    @OptIn(SymbolInternals::class)
    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirPropertySymbol> {
        val compiled = compiledExtensions(context!!.owner)

        return compiled.flatMap { extension ->
            extension.properties.mapNotNull {
                if (it.name == callableId.callableName) {
                    GeneratedDeclarationResolver.resolve(context.owner, extension.context, it, session).symbol
                } else {
                    null
                }
            }
        }
    }

    @OptIn(SymbolInternals::class)
    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        val compiled = compiledExtensions(context.owner)

        return compiled.flatMap { extension ->
            extension.constructors.map {
                GeneratedDeclarationResolver.resolve(context.owner, extension.context, it, session).symbol
            }
        }
    }

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        val compiledExtensions = compiledExtensions(context.owner)

        val names = compiledExtensions.flatMap { extension ->
            extension.functions.map { it.name } + extension.properties.map { it.name } + listOfNotNull(
                SpecialNames.INIT.takeIf { extension.constructors.isNotEmpty() })
        }

        return names.toSet()
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        return compiledExtensions(classSymbol).flatMapTo(hashSetOf()) {
            it.nestedClassifiers.map {
                it.name
            }
        }
    }

    internal object Key : GeneratedDeclarationKey()
}

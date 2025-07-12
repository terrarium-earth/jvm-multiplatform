package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureWriter

internal object SignatureIntersector {
    private fun intersectTypeArguments(a: TypeArgument, b: TypeArgument): TypeArgument? {
        if (a == TypeArgument.Unbounded) {
            return b
        } else if (b == TypeArgument.Unbounded) {
            return a
        }

        a as TypeArgument.Bounded
        b as TypeArgument.Bounded

        val type = intersectTypeSignatures(a.type, b.type)

        if (type == null) {
            return null
        }

        val variance = if (a.variance == b.variance) {
            a.variance
        } else {
            TypeArgument.Bounded.Variance.Invariant
        }

        return TypeArgument.Bounded(type, variance)
    }

    private fun intersectTypeArgumentLists(a: List<TypeArgument>, b: List<TypeArgument>): List<TypeArgument>? {
        if (a.size != b.size) {
            return null
        }

        return a.zip(b) { a, b ->
            val argument = intersectTypeArguments(a, b)

            if (argument == null) {
                return null
            }

            argument
        }
    }

    private fun intersectClassTypeSignatures(
        a: TypeSignature.Reference.Class,
        b: TypeSignature.Reference.Class,
    ): TypeSignature.Reference.Class? {
        if (a.base.name != b.base.name) {
            return null
        }

        if (a.innerClasses.size != b.innerClasses.size) {
            return null
        }

        for ((a, b) in a.innerClasses.zip(b.innerClasses)) {
            if (a.name != b.name) {
                return null
            }
        }

        // All names match, intersect type arguments
        val baseTypeArguments = intersectTypeArgumentLists(a.base.typeArguments, b.base.typeArguments) ?: return null

        val innerClasses = a.innerClasses.zip(b.innerClasses) { a, b ->
            val typeArguments = intersectTypeArgumentLists(a.typeArguments, b.typeArguments)

            if (typeArguments == null) {
                return null
            }

            ClassNameSegment(a.name, typeArguments)
        }

        return TypeSignature.Reference.Class(ClassNameSegment(a.base.name, baseTypeArguments), innerClasses)
    }

    private fun intersectGenericTypeSignatures(a: TypeSignature.Reference, b: TypeSignature.Reference): TypeSignature.Reference? {
        if (a::class != b::class) {
            return null
        }

        return when (a) {
            is TypeSignature.Reference.Array -> {
                val base =
                    intersectTypeSignatures(a.typeSignature, (b as TypeSignature.Reference.Array).typeSignature) ?: return null

                TypeSignature.Reference.Array(base)
            }

            is TypeSignature.Reference.Class -> intersectClassTypeSignatures(a, b as TypeSignature.Reference.Class)
            is TypeSignature.Reference.TypeVariable -> {
                // We add the 'a' names generally in formal type parameters, thus we can assume a name will reference ones from the 'a' names
                // TODO This should eventually just be based on indices rather than names
                a
            }
        }
    }

    private fun intersectTypeSignatures(a: TypeSignature, b: TypeSignature): TypeSignature? {
        if (a::class != b::class) {
            return null
        }

        return when (a) {
            is TypeSignature.Primitive -> {
                if (a.identifier == (b as TypeSignature.Primitive).identifier) {
                    a
                } else {
                    null
                }
            }

            is TypeSignature.Reference -> intersectGenericTypeSignatures(a, b as TypeSignature.Reference)
        }
    }

    private fun intersectTypeParameterLists(a: List<FormalTypeParameter>, b: List<FormalTypeParameter>): List<FormalTypeParameter>? {
        if (a.size != b.size) {
            // Not 1:1
            val emptyA = a.isEmpty()
            val emptyB = b.isEmpty()

            if (emptyA || emptyB) {
                // TODO Filter parameters to only include ones with bounds available in the target classpath
                return if (emptyA) {
                    b
                } else {
                    a
                }
            }

            return null
        } else {
            return a.zip(b) { a, b ->
                val classBound = if (a.classBound != null && b.classBound != null) {
                    intersectTypeSignatures(a.classBound, b.classBound)
                } else {
                    null
                }

                val interfaceBounds = a.interfaceBounds.zip(b.interfaceBounds, ::intersectTypeSignatures).filterNotNull()

                FormalTypeParameter(a.name, classBound, interfaceBounds)
            }
        }
    }

    private fun intersectClassSignatures(a: ClassSignature, b: ClassSignature): ClassSignature? {
        val typeParameters = intersectTypeParameterLists(a.typeParameters, b.typeParameters) ?: return null

        val superClass = intersectClassTypeSignatures(a.superClass, b.superClass)
            ?: TypeSignature.Reference.Class(ClassNameSegment("java/lang/Object"))

        val superInterfaces = a.superInterfaces.zip(b.superInterfaces, ::intersectClassTypeSignatures).filterNotNull()

        return ClassSignature(typeParameters, superClass, superInterfaces)
    }

    private fun intersectMethodSignatures(a: MethodSignature, b: MethodSignature): MethodSignature? {
        val typeParameters = intersectTypeParameterLists(a.typeParameters, b.typeParameters) ?: return null

        val parameterTypes = a.parameterTypes.zip(b.parameterTypes) { a, b ->
            intersectTypeSignatures(a, b) ?: return null
        }

        val returnType = intersectTypeSignatures(a.returnType, b.returnType) ?: return null

        val exceptionTypes = a.exceptionTypes.zip(b.exceptionTypes, ::intersectGenericTypeSignatures).filterNotNull()

        return MethodSignature(typeParameters, parameterTypes, returnType, exceptionTypes)
    }

    fun intersectClassSignatures(a: String?, b: String?): String? {
        if (a == null || b == null) {
            return null
        }

        val a = parseClassSignature(a)
        val b = parseClassSignature(b)

        val signature = intersectClassSignatures(a, b) ?: return null

        return write(signature)
    }

    fun intersectMethodSignature(a: String?, b: String?): String? {
        if (a == null || b == null) {
            return null
        }

        val a = parseMethodSignature(a)
        val b = parseMethodSignature(b)

        val signature = intersectMethodSignatures(a, b) ?: return null

        return write(signature)
    }

    fun intersectFieldSignature(a: String?, b: String?): String? {
        if (a == null || b == null) {
            return null
        }

        val a = parseTypeSignature(a)
        val b = parseTypeSignature(b)

        val signature = intersectTypeSignatures(a, b) ?: return null

        return write(signature)
    }

    private fun write(signature: Signature): String {
        val writer = SignatureWriter()

        signature.accept(writer)

        return writer.toString()
    }
}

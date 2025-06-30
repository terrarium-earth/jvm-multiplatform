package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureVisitor

data class MethodSignature(
    val typeParameters: List<FormalTypeParameter>,
    val parameterTypes: List<TypeSignature>,
    val returnType: TypeSignature,
    val exceptionTypes: List<TypeSignature.Reference>,
): Signature {
    override fun accept(visitor: SignatureVisitor) {
        for (typeParameter in typeParameters) {
            typeParameter.accept(visitor)
        }

        for (parameterType in parameterTypes) {
            val visitor = visitor.visitParameterType()

            parameterType.accept(visitor)
        }

        val visitor = visitor.visitReturnType()

        returnType.accept(visitor.visitSuperclass())

        for (exceptionType in exceptionTypes) {
            val visitor = visitor.visitExceptionType()

            exceptionType.accept(visitor)
        }
    }
}

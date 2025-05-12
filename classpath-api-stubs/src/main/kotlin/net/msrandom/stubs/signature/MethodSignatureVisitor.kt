package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureReader

class MethodSignatureVisitor(api: Int) : GenericSignatureVisitor(api) {
    private val parameters = mutableListOf<TypeSignature>()
    private lateinit var returnType: TypeSignature
    private val exceptionTypes = mutableListOf<TypeSignature.Field>()

    override fun visitParameterType() = TypeSignatureVisitor(api) {
        endFormalTypeParameter()

        parameters.add(it)
    }

    override fun visitReturnType() = TypeSignatureVisitor(api) {
        endFormalTypeParameter()

        returnType = it
    }

    override fun visitExceptionType() = TypeSignatureVisitor(api) {
        exceptionTypes.add(it as TypeSignature.Field)
    }

    fun build() = MethodTypeSignature(formalTypeParameters, parameters, returnType, exceptionTypes)
}

fun parseMethodSignature(api: Int, signature: String): MethodTypeSignature {
    val visitor = MethodSignatureVisitor(api)

    SignatureReader(signature).accept(visitor)

    return visitor.build()
}

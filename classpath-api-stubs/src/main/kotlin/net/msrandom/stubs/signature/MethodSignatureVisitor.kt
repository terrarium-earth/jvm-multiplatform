package net.msrandom.stubs.signature

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureReader

class MethodSignatureVisitor(api: Int) : ParameterizedSignatureVisitor(api) {
    private val parameters = mutableListOf<TypeSignature>()
    private lateinit var returnType: TypeSignature
    private val exceptionTypes = mutableListOf<TypeSignature.Reference>()

    override fun visitParameterType() = TypeSignatureVisitor(api) {
        endFormalTypeParameter()

        parameters.add(it)
    }

    override fun visitReturnType() = TypeSignatureVisitor(api) {
        endFormalTypeParameter()

        returnType = it
    }

    override fun visitExceptionType() = TypeSignatureVisitor(api) {
        exceptionTypes.add(it as TypeSignature.Reference)
    }

    fun build() = MethodSignature(formalTypeParameters, parameters, returnType, exceptionTypes)
}

fun parseMethodSignature(signature: String): MethodSignature {
    val visitor = MethodSignatureVisitor(Opcodes.ASM6)

    SignatureReader(signature).accept(visitor)

    return visitor.build()
}

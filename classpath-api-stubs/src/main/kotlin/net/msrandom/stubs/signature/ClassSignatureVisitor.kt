package net.msrandom.stubs.signature

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

class ClassSignatureVisitor(api: Int) : ParameterizedSignatureVisitor(api) {
    private lateinit var superClass: TypeSignature.Reference.Class
    private val superInterfaces = mutableListOf<TypeSignature.Reference.Class>()

    override fun visitSuperclass(): SignatureVisitor {
        endFormalTypeParameter()

        return TypeSignatureVisitor(api) {
            superClass = it as TypeSignature.Reference.Class
        }
    }

    override fun visitInterface() = TypeSignatureVisitor(api) {
        superInterfaces.add(it as TypeSignature.Reference.Class)
    }

    fun build() = ClassSignature(formalTypeParameters, superClass, superInterfaces)
}

fun parseClassSignature(signature: String): ClassSignature {
    val visitor = ClassSignatureVisitor(Opcodes.ASM6)

    SignatureReader(signature).accept(visitor)

    return visitor.build()
}

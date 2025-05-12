package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

class ClassSignatureVisitor(api: Int) : GenericSignatureVisitor(api) {
    private lateinit var superClass: TypeSignature.Field.Class
    private val superInterfaces = mutableListOf<TypeSignature.Field.Class>()

    override fun visitSuperclass(): SignatureVisitor {
        endFormalTypeParameter()

        return TypeSignatureVisitor(api) {
            superClass = it as TypeSignature.Field.Class
        }
    }

    override fun visitInterface() = TypeSignatureVisitor(api) {
        superInterfaces.add(it as TypeSignature.Field.Class)
    }

    fun build() = ClassSignature(formalTypeParameters, superClass, superInterfaces)
}

fun parseClassSignature(api: Int, signature: String): ClassSignature {
    val visitor = ClassSignatureVisitor(api)

    SignatureReader(signature).accept(visitor)

    return visitor.build()
}

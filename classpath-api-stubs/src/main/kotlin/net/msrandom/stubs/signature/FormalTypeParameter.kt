package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureVisitor

data class FormalTypeParameter(
    val name: String,
    val classBound: TypeSignature?,
    val interfaceBounds: List<TypeSignature>,
) : Signature {
    override fun accept(visitor: SignatureVisitor) {
        visitor.visitFormalTypeParameter(name)

        val classBound = classBound ?: if (interfaceBounds.isEmpty()) {
            TypeSignature.Reference.Class(ClassNameSegment("java/lang/Object"))
        } else {
            null
        }

        classBound?.accept(visitor.visitClassBound())

        for (interfaceBound in interfaceBounds) {
            val visitor = visitor.visitInterfaceBound()

            interfaceBound.accept(visitor)
        }
    }
}

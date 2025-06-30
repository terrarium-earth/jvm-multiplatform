package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureVisitor

data class FormalTypeParameter(
    val name: String,
    val classBound: TypeSignature?,
    val interfaceBounds: List<TypeSignature>,
) : Signature {
    override fun accept(visitor: SignatureVisitor) {
        visitor.visitFormalTypeParameter(name)

        if (classBound != null) {
            val visitor = visitor.visitClassBound()

            classBound.accept(visitor)
        }

        for (interfaceBound in interfaceBounds) {
            val visitor = visitor.visitInterfaceBound()

            interfaceBound.accept(visitor)
        }
    }
}

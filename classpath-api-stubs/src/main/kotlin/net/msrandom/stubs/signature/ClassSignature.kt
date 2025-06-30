package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureVisitor

data class ClassSignature(
    val typeParameters: List<FormalTypeParameter>,
    val superClass: TypeSignature.Reference.Class,
    val superInterfaces: List<TypeSignature.Reference.Class>,
) : Signature {
    override fun accept(visitor: SignatureVisitor) {
        for (typeParameter in typeParameters) {
            typeParameter.accept(visitor)
        }

        superClass.accept(visitor.visitSuperclass())

        for (superInterface in superInterfaces) {
            superInterface.accept(visitor)
        }
    }
}

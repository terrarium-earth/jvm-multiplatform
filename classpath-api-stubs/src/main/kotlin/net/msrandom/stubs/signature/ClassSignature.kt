package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureVisitor

fun List<FormalTypeParameter>.accept(visitor: SignatureVisitor) {
    for (typeParameter in this) {
        typeParameter.accept(visitor)
    }
}

data class ClassSignature(
    val typeParameters: List<FormalTypeParameter>,
    val superClass: TypeSignature.Reference.Class,
    val superInterfaces: List<TypeSignature.Reference.Class>,
) : Signature {
    override fun accept(visitor: SignatureVisitor) {
        typeParameters.accept(visitor)

        superClass.accept(visitor.visitSuperclass())

        for (superInterface in superInterfaces) {
            superInterface.accept(visitor)
        }
    }
}

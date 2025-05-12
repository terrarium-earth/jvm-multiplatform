package net.msrandom.stubs.signature

data class ClassSignature(
    val typeParameters: List<FormalTypeParameter>,
    val superClass: TypeSignature.Field.Class,
    val superInterfaces: List<TypeSignature.Field.Class>,
)
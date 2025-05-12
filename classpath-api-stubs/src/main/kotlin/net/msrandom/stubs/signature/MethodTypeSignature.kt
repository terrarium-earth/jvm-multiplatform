package net.msrandom.stubs.signature

data class MethodTypeSignature(
    val formalTypeParams: List<FormalTypeParameter>,
    val parameterTypes: List<TypeSignature>,
    val returnType: TypeSignature,
    val exceptionTypes: List<TypeSignature.Field>,
)

package net.msrandom.stubs.signature

data class FormalTypeParameter(
    val name: String,
    val classBound: TypeSignature?,
    val interfaceBounds: List<TypeSignature>,
)

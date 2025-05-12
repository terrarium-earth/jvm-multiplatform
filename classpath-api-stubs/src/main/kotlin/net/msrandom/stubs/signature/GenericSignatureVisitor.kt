package net.msrandom.stubs.signature

import org.objectweb.asm.signature.SignatureVisitor

abstract class GenericSignatureVisitor(api: Int) : SignatureVisitor(api) {
    private var currentFormalTypeParameter: String? = null
    private var formalTypeParameterClass: TypeSignature? = null
    private var formalTypeParameterInterfaces = mutableListOf<TypeSignature>()

    protected val formalTypeParameters = mutableListOf<FormalTypeParameter>()

    protected fun endFormalTypeParameter() {
        if (currentFormalTypeParameter != null) {
            formalTypeParameters.add(FormalTypeParameter(currentFormalTypeParameter!!, formalTypeParameterClass, formalTypeParameterInterfaces.toList()))

            currentFormalTypeParameter = null
            formalTypeParameterClass = null
            formalTypeParameterInterfaces.clear()
        }
    }

    override fun visitFormalTypeParameter(name: String) {
        endFormalTypeParameter()

        currentFormalTypeParameter = name
    }

    override fun visitClassBound() = TypeSignatureVisitor(api) {
        formalTypeParameterClass = it
    }

    override fun visitInterfaceBound() = TypeSignatureVisitor(api) {
        formalTypeParameterInterfaces.add(it)
    }
}

package net.msrandom.stubs.signature

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

class TypeSignatureVisitor(api: Int, private val callback: (TypeSignature) -> Unit) : SignatureVisitor(api) {
    private var baseClassSegment: ClassNameSegment? = null
    private var innerClassSegments = mutableListOf<ClassNameSegment>()

    private var currentClassName: String? = null
    private var isInnerClass = false
    private var typeArguments = mutableListOf<TypeArgument>()

    override fun visitBaseType(descriptor: Char) {
        callback(TypeSignature.Primitive(descriptor))
    }

    override fun visitArrayType() = TypeSignatureVisitor(api) {
        callback(TypeSignature.Reference.Array(it))
    }

    override fun visitTypeVariable(name: String) {
        callback(TypeSignature.Reference.TypeVariable(name))
    }

    override fun visitClassType(name: String) {
        currentClassName = name
    }

    override fun visitInnerClassType(name: String) {
        if (!isInnerClass) {
            baseClassSegment = ClassNameSegment(currentClassName!!, typeArguments.toList())
        } else if (currentClassName != null) {
            innerClassSegments.add(ClassNameSegment(currentClassName!!, typeArguments.toList()))
        }

        typeArguments.clear()
        currentClassName = name
        isInnerClass = true
    }

    override fun visitTypeArgument() {
        typeArguments.add(TypeArgument.Unbounded)
    }

    override fun visitTypeArgument(wildcard: Char) = TypeSignatureVisitor(api) {
        typeArguments.add(TypeArgument.Bounded(it, TypeArgument.Bounded.Variance.fromCharacter(wildcard)))
    }

    override fun visitEnd() {
        if (isInnerClass) {
            innerClassSegments.add(ClassNameSegment(currentClassName!!, typeArguments.toList()))
        } else if (currentClassName != null) {
            baseClassSegment = ClassNameSegment(currentClassName!!, typeArguments.toList())
        }

        callback(TypeSignature.Reference.Class(baseClassSegment!!, innerClassSegments))
    }
}

fun parseTypeSignature(signature: String): TypeSignature {
    lateinit var type: TypeSignature

    SignatureReader(signature).acceptType(TypeSignatureVisitor(Opcodes.ASM6) { type = it })

    return type
}

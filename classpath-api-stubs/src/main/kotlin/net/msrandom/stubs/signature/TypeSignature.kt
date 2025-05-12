package net.msrandom.stubs.signature;

import org.objectweb.asm.signature.SignatureVisitor

sealed interface TypeSignature {
    fun accept(visitor: SignatureVisitor)

    data class Base(val identifier: Char) : TypeSignature {
        override fun accept(visitor: SignatureVisitor) {
            visitor.visitBaseType(identifier)
        }
    }

    sealed interface Field : TypeSignature {
        data class Array(val typeSignature: TypeSignature) : Field {
            override fun accept(visitor: SignatureVisitor) {
                visitor.visitArrayType()

                typeSignature.accept(visitor)
            }
        }

        data class TypeVariable(val name: String) : Field {
            override fun accept(visitor: SignatureVisitor) {
                visitor.visitTypeVariable(name)
            }
        }

        data class Class(val base: ClassNameSegment, val innerClasses: List<ClassNameSegment>) : Field {
            override fun accept(visitor: SignatureVisitor) {
                visitor.visitClassType(base.name)

                for (argument in base.typeArguments) {
                    argument.accept(visitor)
                }

                for (inner in innerClasses) {
                    visitor.visitInnerClassType(inner.name)

                    for (argument in inner.typeArguments) {
                        argument.accept(visitor)
                    }
                }

                visitor.visitEnd()
            }
        }
    }
}

sealed interface TypeArgument {
    fun accept(visitor: SignatureVisitor)

    object Unbounded : TypeArgument {
        override fun accept(visitor: SignatureVisitor) {
            visitor.visitTypeArgument()
        }
    }

    data class Bounded(val type: TypeSignature, val variance: Variance) : TypeArgument {
        override fun accept(visitor: SignatureVisitor) {
            visitor.visitTypeArgument(variance.wildcard)
            type.accept(visitor)
        }

        enum class Variance(val wildcard: Char) {
            Covariant(SignatureVisitor.EXTENDS),
            Contravariant(SignatureVisitor.SUPER),
            Invariant(SignatureVisitor.INSTANCEOF);

            companion object {
                fun fromCharacter(wildcard: Char) = entries.first { it.wildcard == wildcard }
            }
        }
    }
}

data class ClassNameSegment(val name: String, val typeArguments: List<TypeArgument>)

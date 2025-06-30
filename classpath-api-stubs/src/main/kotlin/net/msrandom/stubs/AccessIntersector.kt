package net.msrandom.stubs

import org.objectweb.asm.Opcodes

internal object AccessIntersector {
    private const val VISIBILITY_MASK = Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED

    // Choose lower visibility of the two
    internal fun intersect(
        a: Int,
        b: Int,
    ): Int {
        val visibilityA = a and VISIBILITY_MASK
        val visibilityB = b and VISIBILITY_MASK

        fun visibilityOrdinal(value: Int) =
            when (value) {
                Opcodes.ACC_PRIVATE -> 0
                Opcodes.ACC_PROTECTED -> 2
                Opcodes.ACC_PUBLIC -> 3
                else -> 1
            }

        val visibility =
            if (visibilityOrdinal(visibilityA) > visibilityOrdinal(visibilityB)) {
                visibilityB
            } else {
                visibilityA
            }

        val deprecated = a and Opcodes.ACC_DEPRECATED

        val base = if (deprecated != (b and Opcodes.ACC_DEPRECATED)) {
            // One is potentially deprecated while the other isn't, choose the not deprecated one
            if (deprecated == 0) {
                a
            } else {
                b
            }
        } else {
            a
        }

        return (base and VISIBILITY_MASK.inv()) or visibility
    }
}

package net.msrandom.stubs

import net.msrandom.stubs.StubGenerator.ClasspathLoader
import org.objectweb.asm.tree.ClassNode

internal object CommonSuperClassFinder {
    internal fun findCommonSuper(
        nodeA: ClassNode,
        nodeB: ClassNode,
        classpathA: ClasspathLoader,
        classpathB: ClasspathLoader,
    ): String? {
        var superNameA = nodeA.superName
        var superNameB = nodeB.superName

        val visitedSuperNamesA = mutableListOf<String?>(superNameA)
        val visitedSuperNamesB = hashSetOf<String?>(superNameB)

        while (true) {
            for (name in visitedSuperNamesA) {
                if (name in visitedSuperNamesB) {
                    return name
                }
            }

            if (superNameA != null) {
                val superA = classpathA.entry("$superNameA.class")

                if (superA != null) {
                    superNameA = superA.superName

                    if (superNameA in visitedSuperNamesB) {
                        return superNameA
                        break
                    }

                    visitedSuperNamesA.add(superNameA)
                } else {
                    return superNameA
                    break
                }
            }

            if (superNameB != null) {
                val superB = classpathB.entry("$superNameB.class")

                if (superB != null) {
                    superNameB = superB.superName

                    if (superNameB in visitedSuperNamesA) {
                        return superNameB
                    }

                    visitedSuperNamesB.add(superNameB)
                } else {
                    return superNameB
                }
            }
        }
    }
}

package net.msrandom.stubs

import net.msrandom.stubs.StubGenerator.ClasspathLoader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.math.min

object ClassNodeIntersector {
    private const val VISIBILITY_MASK = Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED

    // Choose lower visibility of the two
    private fun accessIntersection(
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

    private fun findCommonSuper(
        nodeA: ClassNode,
        nodeB: ClassNode,
        classpathA: ClasspathLoader,
        classpathB: ClasspathLoader
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

    private fun intersectAnnotations(annotationsA: List<AnnotationNode>?, annotationsB: List<AnnotationNode>?): List<AnnotationNode>? {
        if (annotationsA == null || annotationsB == null) {
            return null
        }

        val matchingAnnotations = annotationsA.filter { annotation ->
            annotationsB.any { it.desc == annotation.desc }
        }

        return matchingAnnotations
    }

    internal fun intersectClassNodes(
        nodeA: ClassNode,
        nodeB: ClassNode,
        classpathA: ClasspathLoader,
        classpathB: ClasspathLoader,
    ): ClassNode {
        val node = ClassNode()

        node.version = min(nodeA.version, nodeB.version)
        node.access = nodeA.access
        node.name = nodeA.name
        node.signature = nodeB.signature?.let { nodeA.signature?.commonPrefixWith(it) }

        node.superName = findCommonSuper(nodeA, nodeB, classpathA, classpathB)

        node.interfaces = nodeA.interfaces.intersect(nodeB.interfaces.toSet()).toList()

        node.innerClasses =
            nodeA.innerClasses.mapNotNull { innerClass ->
                val inner = nodeB.innerClasses.firstOrNull {
                    it.name == innerClass.name
                }

                if (inner == null) {
                    null
                } else {
                    InnerClassNode(
                        innerClass.name,
                        innerClass.outerName,
                        innerClass.innerName,
                        accessIntersection(inner.access, innerClass.access),
                    )
                }
            }

        node.outerClass = nodeA.outerClass
        node.outerMethod = nodeA.outerMethod
        node.outerMethodDesc = nodeA.outerMethodDesc

        node.visibleAnnotations = intersectAnnotations(nodeA.visibleAnnotations, nodeB.visibleAnnotations)
        node.invisibleAnnotations = intersectAnnotations(nodeA.invisibleAnnotations, nodeB.invisibleAnnotations)

        node.fields =
            nodeA.fields.mapNotNull { fieldA ->
                nodeB.fields.firstOrNull { it.name == fieldA.name && it.desc == fieldA.desc }?.let { fieldB ->
                    FieldNode(accessIntersection(fieldB.access, fieldA.access), fieldB.name, fieldB.desc, fieldB.signature, null).also {
                        it.visibleAnnotations = intersectAnnotations(fieldA.visibleAnnotations, fieldB.visibleAnnotations)
                        it.invisibleAnnotations = intersectAnnotations(fieldA.invisibleAnnotations, fieldB.invisibleAnnotations)
                    }
                }
            }

        node.methods =
            nodeA.methods.mapNotNull { methodA ->
                nodeB.methods.firstOrNull { it.name == methodA.name && it.desc == methodA.desc }?.let { methodB ->
                    // Should we use `visit` for supporting all the fields in the method node?
                    MethodNode(
                        accessIntersection(methodB.access, methodA.access),
                        methodB.name,
                        methodB.desc,
                        methodB.signature,
                        methodB.exceptions.toTypedArray().intersect(methodA.exceptions.toSet()).toTypedArray(),
                    ).also {
                        it.visibleAnnotations = intersectAnnotations(methodA.visibleAnnotations, methodB.visibleAnnotations)
                        it.invisibleAnnotations = intersectAnnotations(methodA.invisibleAnnotations, methodB.invisibleAnnotations)

                        if (methodA.annotationDefault != null) {
                            it.annotationDefault = methodA.annotationDefault
                        }
                    }
                }
            }

        return node
    }
}

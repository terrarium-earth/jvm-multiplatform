package net.msrandom.stubs

import net.msrandom.stubs.StubGenerator.ClasspathLoader
import net.msrandom.stubs.signature.SignatureIntersector
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.MethodNode
import kotlin.math.min

object ClassNodeIntersector {
    private fun intersectAnnotations(
        annotationsA: List<AnnotationNode>?,
        annotationsB: List<AnnotationNode>?,
    ): List<AnnotationNode>? {
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

        node.signature = SignatureIntersector.intersectClassSignatures(nodeA.signature, nodeB.signature)

        node.superName = CommonSuperClassFinder.findCommonSuper(nodeA, nodeB, classpathA, classpathB)
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
                        AccessIntersector.intersect(inner.access, innerClass.access),
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
                    val access = AccessIntersector.intersect(fieldB.access, fieldA.access)

                    val signature = SignatureIntersector.intersectFieldSignature(fieldA.signature, fieldB.signature)

                    FieldNode(access, fieldB.name, fieldB.desc, signature, null).also {
                        it.visibleAnnotations =
                            intersectAnnotations(fieldA.visibleAnnotations, fieldB.visibleAnnotations)
                        it.invisibleAnnotations =
                            intersectAnnotations(fieldA.invisibleAnnotations, fieldB.invisibleAnnotations)
                    }
                }
            }

        node.methods =
            nodeA.methods.mapNotNull { methodA ->
                nodeB.methods.firstOrNull { it.name == methodA.name && it.desc == methodA.desc }?.let { methodB ->
                    val access = AccessIntersector.intersect(methodB.access, methodA.access)

                    val signature = SignatureIntersector.intersectMethodSignature(methodA.signature, methodB.signature)

                    val visibleAnnotations =
                        intersectAnnotations(methodA.visibleAnnotations, methodB.visibleAnnotations)
                    val invisibleAnnotations =
                        intersectAnnotations(methodA.invisibleAnnotations, methodB.invisibleAnnotations)

                    val method = MethodNode(
                        access,
                        methodB.name,
                        methodB.desc,
                        signature,
                        methodB.exceptions.toTypedArray().intersect(methodA.exceptions.toSet()).toTypedArray(),
                    )

                    method.visibleAnnotations = visibleAnnotations
                    method.invisibleAnnotations = invisibleAnnotations

                    // TODO This is not fully correct
                    method.annotationDefault = methodA.annotationDefault

                    method
                }
            }

        return node
    }
}

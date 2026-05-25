package net.msrandom.stubs

import net.msrandom.stubs.StubGenerator.ClasspathLoader
import net.msrandom.stubs.signature.SignatureIntersector
import org.objectweb.asm.Type
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import kotlin.math.min

object ClassNodeIntersector {
    private fun areMethodsEqual(nodeA: MethodNode, nodeB: MethodNode): Boolean {
        fun isMeaningful(node: AbstractInsnNode) = node !is LineNumberNode && node !is FrameNode

        val listA = nodeA.instructions.iterator().asSequence().filter(::isMeaningful).toList()
        val listB = nodeB.instructions.iterator().asSequence().filter(::isMeaningful).toList()

        if (listA.size != listB.size) return false

        val labelsA = listA.mapIndexedNotNull { index, node -> if (node is LabelNode) node to index else null }.toMap()
        val labelsB = listB.mapIndexedNotNull { index, node -> if (node is LabelNode) node to index else null }.toMap()

        for (i in listA.indices) {
            val a = listA[i]
            val b = listB[i]

            if (a.opcode != b.opcode) return false

            val equal = when (a) {
                is IntInsnNode -> a.operand == (b as IntInsnNode).operand
                is VarInsnNode -> a.`var` == (b as VarInsnNode).`var`
                is TypeInsnNode -> a.desc == (b as TypeInsnNode).desc
                is FieldInsnNode -> {
                    b as FieldInsnNode
                    a.owner == b.owner && a.name == b.name && a.desc == b.desc
                }
                is MethodInsnNode -> {
                    b as MethodInsnNode
                    a.owner == b.owner && a.name == b.name && a.desc == b.desc && a.itf == b.itf
                }
                is InvokeDynamicInsnNode -> {
                    b as InvokeDynamicInsnNode
                    a.name == b.name && a.desc == b.desc && a.bsm == b.bsm && java.util.Arrays.equals(a.bsmArgs, b.bsmArgs)
                }
                is JumpInsnNode -> {
                    b as JumpInsnNode
                    labelsA[a.label] == labelsB[b.label]
                }
                is LabelNode -> true
                is LdcInsnNode -> a.cst == (b as LdcInsnNode).cst
                is IincInsnNode -> {
                    b as IincInsnNode
                    a.`var` == b.`var` && a.incr == b.incr
                }
                is TableSwitchInsnNode -> {
                    b as TableSwitchInsnNode
                    a.min == b.min && a.max == b.max &&
                            labelsA[a.dflt] == labelsB[b.dflt] &&
                            a.labels.size == b.labels.size &&
                            a.labels.indices.all { k -> labelsA[a.labels[k]] == labelsB[b.labels[k]] }
                }
                is LookupSwitchInsnNode -> {
                    b as LookupSwitchInsnNode
                    labelsA[a.dflt] == labelsB[b.dflt] &&
                    a.keys == b.keys &&
                    a.labels.size == b.labels.size &&
                    a.labels.indices.all { k -> labelsA[a.labels[k]] == labelsB[b.labels[k]] }
                }
                is MultiANewArrayInsnNode -> {
                    b as MultiANewArrayInsnNode
                    a.desc == b.desc && a.dims == b.dims
                }
                else -> true
            }

            if (!equal) return false
        }

        return true
    }

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
        preserveMethodBodies: Boolean,
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

                    if (methodB.parameters != null) {
                        method.parameters = methodB.parameters.map { ParameterNode(it.name, it.access) }
                    }

                    if (method.parameters == null && methodB.localVariables != null) {
                        val isStatic = (methodB.access and Opcodes.ACC_STATIC) != 0
                        val argTypes = Type.getArgumentTypes(methodB.desc)
                        val parameters = ArrayList<ParameterNode>(argTypes.size)
                        var slot = if (isStatic) 0 else 1

                        for (type in argTypes) {
                            // Find local variable for this slot that likely represents the parameter
                            // We prioritize variables that start early in the method
                            val match = methodB.localVariables.filter { it.index == slot }.minByOrNull {
                                // We can't easily get label offset without a method analysis, 
                                // but usually parameters are first in the list or we can pick the one with "smallest" scope start?
                                // Actually, parameters are usually defined at the start.
                                // Let's just pick the first one we find for now, or refine if needed.
                                // A better heuristic might be needed if slots are reused.
                                // But for parameters, they are usually valid from the start label.
                                0 // Placeholder as we can't compare LabelNodes easily without index
                            } ?: methodB.localVariables.firstOrNull { it.index == slot }

                            if (match != null) {
                                parameters.add(ParameterNode(match.name, 0))
                            }
                            
                            slot += type.size
                        }

                        if (parameters.size == argTypes.size) {
                            method.parameters = parameters
                        }
                    }

                    if (preserveMethodBodies && areMethodsEqual(methodA, methodB)) {
                        methodB.instructions.accept(method)
                        method.tryCatchBlocks = methodB.tryCatchBlocks
                        method.localVariables = methodB.localVariables
                        method.visibleLocalVariableAnnotations = methodB.visibleLocalVariableAnnotations
                        method.invisibleLocalVariableAnnotations = methodB.invisibleLocalVariableAnnotations
                        method.maxStack = methodB.maxStack
                        method.maxLocals = methodB.maxLocals
                    }

                    method
                }
            }

        return node
    }
}

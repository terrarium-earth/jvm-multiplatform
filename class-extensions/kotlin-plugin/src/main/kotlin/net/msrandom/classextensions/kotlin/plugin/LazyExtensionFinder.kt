package net.msrandom.classextensions.kotlin.plugin

import net.msrandom.classextensions.ClassExtension
import net.msrandom.classextensions.ExtensionInject
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.config.HmppCliModuleStructure
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.lightTree.LightTree2Fir
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.modules.Module
import org.jetbrains.kotlin.psi.stubs.elements.KtStubElementTypes
import org.jetbrains.kotlin.util.getChildren
import kotlin.io.path.Path
import kotlin.io.path.readText

class ClassExtensionInfo(
    val treeStructure: FlyweightCapableTreeStructure<LighterASTNode>,
    val targetTypeName: String,
    val modifiers: List<LighterASTNode>,
    val superList: List<String>,
    val functions: List<LighterASTNode>,
    val properties: List<LighterASTNode>,
    val constructors: List<LighterASTNode>,
    val nestedClassifiers: List<LighterASTNode>,
)

class CompiledExtensionInfo(
    val functions: List<FirSimpleFunction>,
    val properties: List<FirProperty>,
    val constructors: List<FirConstructor>,
    val nestedClassifiers: List<FirRegularClass>,
)

/**
 * The idea of this class is simple; when a base class is getting compiled(to FIR),
 *  - find and compile the extensions(to FIR) early,
 *  - and prevent them from getting recompiled later
 *   - this probably involves removing them entirely from the files
 */
class LazyExtensionFinder(private val module: Module, private val moduleStructure: HmppCliModuleStructure) {
    fun getAllExtensionClasses(): List<ClassExtensionInfo> {
        val extensionTrees = extensionSourceFiles().mapNotNull {
            val code = Path(it).readText()

            if (ClassExtension::class.java.simpleName !in code) {
                return@mapNotNull null
            }

            LightTree2Fir.buildLightTree(code, null)
        }

        return extensionTrees.flatMap {
            getExtensionClasses(it)
        }
    }

    fun getAnnotation(
        modifiers: List<LighterASTNode>,
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
        annotation: Class<out Annotation>,
    ): LighterASTNode? {
        return modifiers.firstOrNull {
            if (it.tokenType != KtStubElementTypes.ANNOTATION_ENTRY) {
                return@firstOrNull false
            }

            val annotationInvocationNode =
                it.getChildren(tree).first { it.tokenType == KtStubElementTypes.CONSTRUCTOR_CALLEE }

            // TODO Type aliases and import aliases are not considered.
            //  We can potentially partially resolve the file(imports, relevant declarations, etc) but probably not worth it
            annotation.simpleName in annotationInvocationNode.toString()
        }
    }

    fun isInject(node: LighterASTNode, tree: FlyweightCapableTreeStructure<LighterASTNode>): Boolean {
        val modifiers =
            node.getChildren(tree).firstOrNull { it.tokenType == KtStubElementTypes.MODIFIER_LIST }?.getChildren(tree)
                ?: emptyList()

        return getAnnotation(modifiers, tree, ExtensionInject::class.java) != null
    }

    private fun getExtensionClasses(tree: FlyweightCapableTreeStructure<LighterASTNode>): List<ClassExtensionInfo> {
        return tree.root.getChildren(tree).mapNotNull {
            if (it.tokenType == KtStubElementTypes.CLASS) {
                extractExtensionInfo(it, tree)
            } else {
                null
            }
        }
    }

    private fun extractExtensionInfo(
        node: LighterASTNode,
        tree: FlyweightCapableTreeStructure<LighterASTNode>,
    ): ClassExtensionInfo? {
        val nodeChildren = node.getChildren(tree)

        val primaryConstructor =
            nodeChildren.filter { it.tokenType == KtStubElementTypes.PRIMARY_CONSTRUCTOR && isInject(it, tree) }

        val modifiers = nodeChildren.first { it.tokenType == KtStubElementTypes.MODIFIER_LIST }.getChildren(tree)

        val declarations =
            nodeChildren.firstOrNull { it.tokenType == KtStubElementTypes.CLASS_BODY }?.getChildren(tree) ?: emptyList()

        val superList =
            nodeChildren.firstOrNull { it.tokenType == KtStubElementTypes.SUPER_TYPE_LIST }?.getChildren(tree)
                ?: emptyList()

        val extensionAnnotation = getAnnotation(modifiers, tree, ClassExtension::class.java)

        if (extensionAnnotation == null) {
            return null
        }

        val argumentList =
            extensionAnnotation.getChildren(tree).firstOrNull { it.tokenType == KtStubElementTypes.VALUE_ARGUMENT_LIST }

        if (argumentList == null) {
            throw IllegalArgumentException("@ClassExtension with no arguments")
        }

        val targetArgument =
            argumentList.getChildren(tree).firstOrNull { it.tokenType == KtStubElementTypes.VALUE_ARGUMENT }

        if (targetArgument == null) {
            throw IllegalArgumentException("@ClassExtension requires a type argument for the class to extend")
        }

        val targetArgumentValue = targetArgument.toString()

        if (!targetArgumentValue.endsWith("::class")) {
            throw IllegalArgumentException("Incorrect type literal $targetArgumentValue")
        }

        return ClassExtensionInfo(
            tree,
            targetArgumentValue.substring(0, targetArgumentValue.length - "::class".length),
            modifiers.filter { it != extensionAnnotation },
            superList.filter { it.tokenType == KtStubElementTypes.SUPER_TYPE_ENTRY }.map(LighterASTNode::toString),

            declarations.filter { it.tokenType == KtStubElementTypes.FUNCTION && isInject(it, tree) },
            declarations.filter { it.tokenType == KtStubElementTypes.PROPERTY && isInject(it, tree) },

            declarations.filter {
                it.tokenType == KtStubElementTypes.SECONDARY_CONSTRUCTOR && isInject(
                    it,
                    tree
                )
            } + primaryConstructor,

            declarations.filter {
                it.tokenType == KtStubElementTypes.OBJECT_DECLARATION || it.tokenType == KtStubElementTypes.CLASS && isInject(
                    it,
                    tree
                )
            },
        )
    }

    private fun extensionSourceFiles(): List<String> {
        // Modules that aren't on the bottom of the dependency graph are the relevant ones
        val relevantModules = moduleStructure.modules - moduleStructure.dependenciesMap.values.flatten()

        return relevantModules.flatMap {
            it.sources
        }.filter {
            it in module.getSourceFiles()
        }
    }
}

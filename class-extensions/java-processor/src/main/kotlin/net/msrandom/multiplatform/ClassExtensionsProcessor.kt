package net.msrandom.multiplatform

import com.sun.source.tree.ImportTree
import com.sun.tools.javac.api.JavacTrees
import com.sun.tools.javac.processing.JavacProcessingEnvironment
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.util.Context
import net.msrandom.classextensions.ClassExtension
import net.msrandom.classextensions.ExtensionInject
import net.msrandom.multiplatform.bootstrap.BootstappedProcessor
import net.msrandom.multiplatform.bootstrap.PlatformHelper
import javax.annotation.processing.*
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType

private val CLASS_EXTENSION_ANNOTATION_NAME = "@${ClassExtension::class.simpleName}"
private val EXTENSION_INJECT_ANNOTATION_NAME = "@${ExtensionInject::class.simpleName}"

@Suppress("unused")
class ClassExtensionsProcessor(
    processingEnvironment: ProcessingEnvironment,
    private val platformHelper: PlatformHelper,
    options: Map<String, String>,
) : BootstappedProcessor {
    private val context: Context
    private val trees: JavacTrees

    init {
        processingEnvironment as JavacProcessingEnvironment

        context = processingEnvironment.context
        trees = JavacTrees.instance(context)
    }

    override fun process(roundEnvironment: RoundEnvironment) {
        val extensions = roundEnvironment.getElementsAnnotatedWith(ClassExtension::class.java)
        val injections = roundEnvironment.getElementsAnnotatedWith(ExtensionInject::class.java)

        for (element in injections) {
            val parent = element.enclosingElement

            if (parent !is TypeElement || parent.annotationMirrors.none { it.annotationType.toString() == ClassExtension::class.qualifiedName }) {
                throw UnsupportedOperationException("Element $element annotated with $EXTENSION_INJECT_ANNOTATION_NAME but parent $parent is not annotated with $CLASS_EXTENSION_ANNOTATION_NAME or is not a type")
            }
        }

        for (extension in extensions) {
            if (extension !is TypeElement) {
                throw UnsupportedOperationException("Element $extension was annotated with $CLASS_EXTENSION_ANNOTATION_NAME but was not a type")
            }

            val annotation = extension.annotationMirrors.first {
                it.annotationType.toString() == ClassExtension::class.qualifiedName
            }

            val annotationValue = annotation.elementValues.entries.first { (key, _) ->
                key.simpleName.contentEquals(ClassExtension::value.name)
            }.value

            val base = (annotationValue.value as DeclaredType).asElement() as TypeElement

            val baseTree = trees.getTree(base)
            val extensionTree = trees.getTree(extension)
            val baseCompilationUnit = trees.getPath(base).compilationUnit as JCTree.JCCompilationUnit
            val extensionCompilationUnit = trees.getPath(extension).compilationUnit as JCTree.JCCompilationUnit

            val injectedMembers = extensionTree.members.filter { memberTree ->
                if (memberTree is JCTree.JCMethodDecl) {
                    memberTree.modifiers.filterAnnotation<ExtensionInject>()
                    memberTree.sym in injections
                } else {
                    memberTree as JCTree.JCVariableDecl

                    memberTree.modifiers.filterAnnotation<ExtensionInject>()
                    memberTree.sym in injections
                }
            }

            baseTree.implementing = JavaCompilerList.from((baseTree.implementing + extensionTree.implementing).distinct())
            baseTree.defs = JavaCompilerList.from(baseTree.members + injectedMembers)

            val imports = (baseCompilationUnit.imports + extensionCompilationUnit.imports).filterNot {
                (it as ImportTree).qualifiedIdentifier.toString().startsWith(ClassExtension::class.java.getPackage().name)
            }

            val unitDefs =
                (listOf(baseCompilationUnit.defs.head) + imports + baseCompilationUnit.typeDecls)

            baseCompilationUnit.defs = JavaCompilerList.from(unitDefs)
            extensionCompilationUnit.defs = JavaCompilerList.nil()
        }
    }
}

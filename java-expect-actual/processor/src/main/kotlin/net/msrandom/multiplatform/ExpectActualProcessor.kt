package net.msrandom.multiplatform

import com.sun.source.tree.ImportTree
import com.sun.source.tree.MethodTree
import com.sun.source.tree.Tree
import com.sun.source.tree.VariableTree
import com.sun.tools.javac.api.JavacTrees
import com.sun.tools.javac.processing.JavacProcessingEnvironment
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit
import com.sun.tools.javac.tree.JCTree.JCMethodDecl
import com.sun.tools.javac.tree.JCTree.JCVariableDecl
import com.sun.tools.javac.util.Context
import net.msrandom.multiplatform.annotations.Actual
import net.msrandom.multiplatform.annotations.Expect
import net.msrandom.multiplatform.bootstrap.BootstappedProcessor
import net.msrandom.multiplatform.bootstrap.GENERATE_EXPECT_STUBS_OPTION
import net.msrandom.multiplatform.bootstrap.PlatformHelper
import javax.annotation.processing.*
import javax.lang.model.element.*

private val EXPECT_ANNOTATION_NAME = "@${Expect::class.simpleName}"
private val ACTUAL_SUFFIX = Actual::class.simpleName!!
private val ACTUAL_ANNOTATION_NAME = "@$ACTUAL_SUFFIX"

// Loaded reflectively after com.sun.tools.javac packages are exported
@Suppress("unused")
class ExpectActualProcessor(
    private val processingEnvironment: ProcessingEnvironment,
    private val platformHelper: PlatformHelper,
    options: Map<String, String>,
) : BootstappedProcessor {
    private val context: Context
    private val trees: JavacTrees
    private val generateStubs = GENERATE_EXPECT_STUBS_OPTION in options

    init {
        processingEnvironment as JavacProcessingEnvironment

        context = processingEnvironment.context
        trees = JavacTrees.instance(context)
    }

    private inline fun <T> getActual(implementations: List<T>, name: () -> CharSequence) = when {
        implementations.isEmpty() -> if (generateStubs) {
            null
        } else {
            throw IllegalArgumentException("${name()} includes $EXPECT_ANNOTATION_NAME with no $ACTUAL_ANNOTATION_NAME")
        }

        implementations.size > 1 -> throw UnsupportedOperationException("${name()} includes $EXPECT_ANNOTATION_NAME has more than one $ACTUAL_ANNOTATION_NAME")
        else -> implementations.first()
    }

    private fun checkField(expect: VariableElement) {
        if ((trees.getTree(expect) as VariableTree).initializer != null) {
            throw UnsupportedOperationException("$EXPECT_ANNOTATION_NAME field ${(expect.enclosingElement as TypeElement).qualifiedName}.${expect.simpleName} has initializer")
        }
    }

    private fun checkMethod(expect: ExecutableElement) {
        if (trees.getTree(expect).getBody() != null) {
            throw UnsupportedOperationException("$EXPECT_ANNOTATION_NAME method ${(expect.enclosingElement as TypeElement).qualifiedName}.${expect.simpleName} has body")
        }
    }

    override fun process(roundEnvironment: RoundEnvironment) {
        val allExpected = roundEnvironment.getElementsAnnotatedWith(Expect::class.java)
        val allActual = roundEnvironment.getElementsAnnotatedWith(Actual::class.java)

        val implementations = allExpected.associateWith { expect ->
            if (expect is TypeElement) {
                // class, enum, record, interface, annotation

                val implementations = allActual.filter { actual ->
                    actual is TypeElement && actual.qualifiedName contentEquals expect.qualifiedName.toString() + ACTUAL_SUFFIX
                }

                val actual = getActual(implementations, expect::getQualifiedName) ?: return@associateWith null

                if (actual.kind != expect.kind) {
                    throw UnsupportedOperationException("$ACTUAL_ANNOTATION_NAME type is a different kind from its $EXPECT_ANNOTATION_NAME, expected ${expect.kind} but found ${actual.kind}")
                }

                if (actual.modifiers != expect.modifiers) {
                    throw UnsupportedOperationException("$ACTUAL_ANNOTATION_NAME type has different modifiers from its $EXPECT_ANNOTATION_NAME, expected ${expect.modifiers} but found ${actual.modifiers}")
                }

                actual
            } else if (expect is ExecutableElement && expect.kind == ElementKind.METHOD) {
                val expectOwner = expect.enclosingElement as TypeElement
                val expectOwnerName = expectOwner.qualifiedName

                val implementations = allActual.filterIsInstance<ExecutableElement>().filter { actual ->
                    val actualOwner = actual.enclosingElement as TypeElement
                    val actualOwnerName = actualOwner.qualifiedName

                    if (!actualOwnerName.endsWith(ACTUAL_SUFFIX)) {
                        throw IllegalArgumentException("$actualOwnerName does not end with $ACTUAL_SUFFIX")
                    }

                    actual.kind == ElementKind.METHOD &&
                            actualOwnerName contentEquals expectOwnerName.toString() + ACTUAL_SUFFIX &&
                            actual.simpleName == expect.simpleName &&
                            actual.parameters.map(VariableElement::asType).zip(expect.parameters.map(VariableElement::asType)).all { (a, b) -> a.toString() == b.toString() }
                }

                getActual(implementations) {
                    "$expectOwnerName.${expect.simpleName}"
                }
            } else if (expect is VariableElement && expect.kind == ElementKind.FIELD) {
                val expectOwner = expect.enclosingElement as TypeElement
                val expectOwnerName = expectOwner.qualifiedName

                val implementations = allActual.filterIsInstance<VariableElement>().filter { actual ->
                    val actualOwner = actual.enclosingElement as TypeElement
                    val actualOwnerName = actualOwner.qualifiedName

                    if (!actualOwnerName.endsWith(ACTUAL_SUFFIX)) {
                        throw IllegalArgumentException("$actualOwnerName does not end with $ACTUAL_SUFFIX")
                    }

                    actual.kind == ElementKind.FIELD &&
                            actualOwnerName contentEquals expectOwnerName.toString() + ACTUAL_SUFFIX &&
                            actual.simpleName == expect.simpleName
                }

                val actual = getActual(implementations) {
                    "$expectOwnerName.${expect.simpleName}"
                } ?: return@associateWith null

                if (actual.asType().toString() != expect.asType().toString()) {
                    throw UnsupportedOperationException("$EXPECT_ANNOTATION_NAME field has differing type from $ACTUAL_ANNOTATION_NAME, expected ${expect.asType()} but found ${actual.asType()}")
                }

                actual
            } else {
                throw UnsupportedOperationException("Found $EXPECT_ANNOTATION_NAME on element of unsupported kind ${expect.kind}")
            }
        }

        allActual.firstOrNull {
            it !in implementations.values &&
                    (it.enclosingElement !is TypeElement || it.enclosingElement.getAnnotation(Actual::class.java) == null)
        }?.let {
            val ownerName = (it.enclosingElement as? TypeElement)?.qualifiedName?.let {
                "$it."
            } ?: ""

            throw IllegalArgumentException("$ownerName${it.simpleName} includes an $ACTUAL_ANNOTATION_NAME without a corresponding $EXPECT_ANNOTATION_NAME")
        }

        fun clearActual(unit: JCCompilationUnit) {
            // We don't want to keep the @Actual files
            unit.defs = JavaCompilerList.nil()
        }

        fun clearActualParent(element: Element) {
            val unit = trees.getPath(element.enclosingElement)?.compilationUnit as? JCCompilationUnit

            unit?.let(::clearActual)
        }

        val handledClasses = hashSetOf<TypeElement>()

        for ((expect, actual) in implementations.entries) {
            when (expect) {
                is ExecutableElement -> {
                    checkMethod(expect)

                    val expectTree = trees.getTree(expect)

                    if (actual == null) {
                        stub(expectTree, context)

                        continue
                    }

                    require(actual is ExecutableElement)

                    val actualTree = trees.getTree(actual)

                    expectTree.body = actualTree.getBody()

                    expectTree.modifiers.filterAnnotation<Expect>()

                    val expectOwner = expect.enclosingElement

                    if (expectOwner !in handledClasses) {
                        val expectCompilationUnit = trees.getPath(expectOwner).compilationUnit as JCCompilationUnit
                        val actualCompilationUnit = trees.getPath(actual.enclosingElement).compilationUnit as JCCompilationUnit

                        val imports = (expectCompilationUnit.imports + actualCompilationUnit.imports).filterNot {
                            it.kind == Tree.Kind.IMPORT && (it as ImportTree).qualifiedIdentifier.toString().startsWith(Expect::class.java.getPackage().name)
                        }

                        val unitDefs =
                            (listOf(expectCompilationUnit.defs.head) + imports + expectCompilationUnit.typeDecls)

                        expectCompilationUnit.defs = JavaCompilerList.from(unitDefs)

                        clearActualParent(actual)

                        handledClasses.add(expectOwner as TypeElement)
                    }
                }

                is VariableElement -> {
                    checkField(expect)

                    val expectTree = trees.getTree(expect) as JCVariableDecl

                    if (actual == null) {
                        stub(expectTree, context)

                        continue
                    }

                    require(actual is VariableElement)

                    val actualTree = trees.getTree(actual) as JCVariableDecl

                    expectTree.init = actualTree.initializer

                    expectTree.modifiers.filterAnnotation<Expect>()

                    val expectOwner = expect.enclosingElement

                    if (expectOwner !in handledClasses) {
                        val expectCompilationUnit = trees.getPath(expectOwner).compilationUnit as JCCompilationUnit
                        val actualCompilationUnit = trees.getPath(actual.enclosingElement).compilationUnit as JCCompilationUnit

                        val imports = (expectCompilationUnit.imports + actualCompilationUnit.imports).filterNot {
                            it.kind == Tree.Kind.IMPORT && (it as ImportTree).qualifiedIdentifier.toString().startsWith(Expect::class.java.getPackage().name)
                        }

                        val unitDefs =
                            (listOf(expectCompilationUnit.defs.head) + imports + expectCompilationUnit.typeDecls)

                        expectCompilationUnit.defs = JavaCompilerList.from(unitDefs)

                        clearActualParent(actual)

                        handledClasses.add(expectOwner as TypeElement)
                    }
                }

                is TypeElement -> {
                    val expectTree = trees.getTree(expect)

                    if (actual == null) {
                        for (member in expectTree.members) {
                            if (member is VariableTree) {
                                stub(member as JCVariableDecl, context)
                            } else if (member is MethodTree) {
                                stub(member as JCMethodDecl, context)
                            }
                        }

                        continue
                    }

                    require(actual is TypeElement)

                    val actualTree = trees.getTree(actual)

                    val expectCompilationUnit = trees.getPath(expect).compilationUnit as JCCompilationUnit
                    val actualCompilationUnit = trees.getPath(actual).compilationUnit as JCCompilationUnit

                    expectTree.prepareClass(processingEnvironment, platformHelper.elementRemover)

                    for (member in expect.enclosedElements) {
                        if (member.getAnnotation(Expect::class.java) != null) {
                            throw UnsupportedOperationException("${expect.qualifiedName}.${member.simpleName} has $EXPECT_ANNOTATION_NAME while owned by $EXPECT_ANNOTATION_NAME type")
                        }

                        if (!platformHelper.isGenerated(processingEnvironment, member)) {
                            if (member is VariableElement) {
                                checkField(member)
                            } else if (member is ExecutableElement) {
                                checkMethod(member)
                            }
                        }
                    }

                    val members = actualTree.members.map {
                        it.clone<Actual>(context)
                    }

                    val imports = (expectCompilationUnit.imports + actualCompilationUnit.imports).filterNot {
                        (it as ImportTree).qualifiedIdentifier.toString().startsWith(Expect::class.java.getPackage().name)
                    }

                    val unitDefs =
                        (listOf(expectCompilationUnit.defs.head) + imports + expectCompilationUnit.typeDecls)

                    expectCompilationUnit.defs = JavaCompilerList.from(unitDefs)

                    clearActual(actualCompilationUnit)

                    expectTree.pos = actualTree.pos
                    expectTree.modifiers.pos = actualTree.modifiers.pos

                    expectTree.modifiers.filterAnnotation<Expect>()

                    expectTree.typarams = actualTree.typeParameters
                    expectTree.extending = actualTree.extendsClause
                    expectTree.implementing = actualTree.implementsClause
                    expectTree.defs = JavaCompilerList.from(members)

                    expectCompilationUnit.finalizeClass(context)
                }
            }
        }
    }
}

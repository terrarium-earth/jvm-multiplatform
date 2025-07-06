package net.msrandom.multiplatform

import com.sun.source.tree.PrimitiveTypeTree
import com.sun.source.tree.Tree
import com.sun.tools.javac.code.TypeTag
import com.sun.tools.javac.comp.Enter
import com.sun.tools.javac.tree.JCTree
import com.sun.tools.javac.tree.JCTree.*
import com.sun.tools.javac.tree.TreeMaker
import com.sun.tools.javac.util.Context
import com.sun.tools.javac.util.List
import net.msrandom.multiplatform.bootstrap.ElementRemover
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.type.TypeKind

typealias JavaCompilerList<T> = List<T>

fun typeDefault(expression: Tree, maker: TreeMaker): JCExpression = if (expression is PrimitiveTypeTree) {
    val defaultValue = when (expression.primitiveTypeKind) {
        TypeKind.BOOLEAN -> false
        TypeKind.BYTE -> 0.toByte()
        TypeKind.SHORT -> 0.toShort()
        TypeKind.INT -> 0
        TypeKind.LONG -> 0L
        TypeKind.CHAR -> 0.toChar()
        TypeKind.FLOAT -> 0f
        TypeKind.DOUBLE -> 0.0
        else -> throw UnsupportedOperationException()
    }

    maker.Literal(defaultValue)
} else {
    maker.Literal(TypeTag.BOT, null)
}

fun stub(tree: JCVariableDecl, context: Context) {
    if (tree.init != null) return

    val treeMaker = TreeMaker.instance(context)

    tree.init = typeDefault(tree.getType(), treeMaker)
}

inline fun <reified A : Annotation> JCModifiers.filterAnnotation() = filterAnnotation(A::class.java)

fun JCModifiers.filterAnnotation(annotationToSkip: Class<out Annotation>) {
    annotations.firstOrNull { annotationToSkip.name.equals(it.type?.toString()) }?.let {
        annotations = JavaCompilerList.filter(annotations, it)
    }
}

inline fun <reified A : Annotation> JCTree.clone(context: Context) = clone(context, A::class.java)

fun JCTree.clone(context: Context, annotationToSkip: Class<out Annotation>): JCTree {
    val treeMaker = TreeMaker.instance(context)

    return when (this) {
        is JCMethodDecl -> {
            treeMaker.MethodDef(
                modifiers,
                getName(),
                returnType as JCExpression?,
                typeParameters,
                receiverParameter,
                parameters,
                throws,
                getBody(),
                getDefaultValue() as JCExpression?,
            ).also {
                it.pos = pos
                it.mods.pos = mods.pos

                it.mods.annotations.firstOrNull { annotation -> annotationToSkip.name.equals(annotation.type?.toString()) }?.let { annotation ->
                    it.mods.annotations = JavaCompilerList.filter(it.mods.annotations, annotation)
                }
            }
        }

        is JCVariableDecl -> {
            treeMaker.VarDef(
                modifiers,
                getName(),
                getType() as JCExpression?,
                initializer,
            ).also {
                it.pos = pos
                it.mods.pos = mods.pos

                it.mods.annotations.firstOrNull { annotation -> annotationToSkip.equals(annotation.type?.toString()) }?.let { annotation ->
                    it.mods.annotations = JavaCompilerList.filter(it.mods.annotations, annotation)
                }
            }
        }

        else -> {
            throw UnsupportedOperationException("Attempting to clone $kind")
        }
    }
}

fun stub(tree: JCMethodDecl, context: Context) {
    if (tree.body != null) return

    val treeMaker = TreeMaker.instance(context)
    val returnType = tree.returnType

    val statements: JavaCompilerList<JCStatement> =
        if (returnType == null || returnType is PrimitiveTypeTree && returnType.primitiveTypeKind == TypeKind.VOID) {
            JavaCompilerList.nil()
        } else {
            JavaCompilerList.of(treeMaker.Return(typeDefault(tree.returnType, treeMaker)))
        }

    tree.body = treeMaker.Block(0, statements)
}

fun JCClassDecl.prepareClass(processingEnvironment: ProcessingEnvironment, elementRemover: ElementRemover) {
    elementRemover(processingEnvironment, sym)

    sym = null
}

fun JCCompilationUnit.finalizeClass(context: Context) {
    Enter.instance(context).complete(JavaCompilerList.of(this), null)
}

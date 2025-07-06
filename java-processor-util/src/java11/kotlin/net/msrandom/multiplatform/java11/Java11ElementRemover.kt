@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package net.msrandom.multiplatform.java11

import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.comp.Check
import com.sun.tools.javac.processing.JavacProcessingEnvironment
import net.msrandom.multiplatform.bootstrap.ElementRemover
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement

object Java11ElementRemover : ElementRemover {
    override fun invoke(processingEnvironment: ProcessingEnvironment, element: TypeElement) {
        Check.instance((processingEnvironment as JavacProcessingEnvironment).context).removeCompiled(element as Symbol.ClassSymbol)
    }
}

package net.msrandom.multiplatform.java8

import com.sun.tools.javac.code.Flags
import com.sun.tools.javac.code.Symbol
import net.msrandom.multiplatform.bootstrap.ElementRemover
import net.msrandom.multiplatform.bootstrap.PlatformHelper
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element

object Java8PlatformHelper : PlatformHelper {
    override val elementRemover: ElementRemover
        get() = Java8ElementRemover

    override fun addExports(processorClass: Class<*>) {}

    override fun isGenerated(processingEnvironment: ProcessingEnvironment, element: Element) =
        (element as Symbol).flags() and (Flags.GENERATEDCONSTR or Flags.MANDATED.toLong()) > 0
}

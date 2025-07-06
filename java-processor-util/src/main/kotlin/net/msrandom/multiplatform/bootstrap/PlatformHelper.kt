package net.msrandom.multiplatform.bootstrap

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

typealias ElementRemover = (ProcessingEnvironment, TypeElement) -> Unit

interface PlatformHelper {
    val elementRemover: ElementRemover

    fun addExports(processorClass: Class<*>)

    fun isGenerated(processingEnvironment: ProcessingEnvironment, element: Element): Boolean
}

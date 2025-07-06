package net.msrandom.multiplatform.bootstrap

import com.google.auto.service.AutoService
import net.msrandom.multiplatform.annotations.Actual
import net.msrandom.multiplatform.annotations.Expect
import javax.annotation.processing.Completion
import javax.annotation.processing.Processor
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import kotlin.reflect.KClass

internal const val GENERATE_EXPECT_STUBS_OPTION = "generateExpectStubs"

@AutoService(Processor::class)
class ExpectActualProcessorBootstrap : AnnotationProcessorBootstrapBase() {
    override val processorClass: Class<*> = Class.forName("net.msrandom.multiplatform.ExpectActualProcessor")

    override fun getSupportedOptions() =
        setOf(GENERATE_EXPECT_STUBS_OPTION)

    override fun getSupportedAnnotationTypes() = arrayOf(Expect::class, Actual::class)
        .map(KClass<*>::qualifiedName)
        .toSet()

    override fun getCompletions(element: Element, annotation: AnnotationMirror, member: ExecutableElement, userText: String) =
        emptyList<Completion>()
}

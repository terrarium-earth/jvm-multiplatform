package net.msrandom.multiplatform.bootstrap

import com.google.auto.service.AutoService
import net.msrandom.classextensions.ClassExtension
import net.msrandom.classextensions.ExtensionInject
import javax.annotation.processing.Completion
import javax.annotation.processing.Processor
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import kotlin.reflect.KClass

@AutoService(Processor::class)
class ClassExtensionsProcessorBootstrap : AnnotationProcessorBootstrapBase() {
    override val processorClass: Class<*> = Class.forName("net.msrandom.multiplatform.ClassExtensionsProcessor")

    override fun getSupportedOptions() =
        emptySet<String>()

    override fun getSupportedAnnotationTypes() = arrayOf(ClassExtension::class, ExtensionInject::class)
        .map(KClass<*>::qualifiedName)
        .toSet()

    override fun getCompletions(element: Element, annotation: AnnotationMirror, member: ExecutableElement, userText: String) =
        emptyList<Completion>()
}

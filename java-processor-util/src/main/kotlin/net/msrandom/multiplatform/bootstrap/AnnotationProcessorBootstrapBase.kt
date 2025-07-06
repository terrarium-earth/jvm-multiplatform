package net.msrandom.multiplatform.bootstrap

import net.msrandom.multiplatform.java8.Java8PlatformHelper
import java.lang.reflect.Constructor
import java.util.*
import javax.annotation.processing.Completion
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

abstract class AnnotationProcessorBootstrapBase : Processor {
    abstract val processorClass: Class<*>

    private lateinit var processor: BootstappedProcessor

    // Compatible with any version higher than at least 8
    override fun getSupportedSourceVersion(): SourceVersion =
        SourceVersion.latestSupported().coerceAtLeast(SourceVersion.RELEASE_8)

    override fun init(processingEnv: ProcessingEnvironment) {
        val exporterIterator = ServiceLoader
            .load(PlatformHelper::class.java, javaClass.classLoader)
            .iterator()

        val platformHelper = if (exporterIterator.hasNext()) {
            val platformHelper = exporterIterator.next()

            require(!exporterIterator.hasNext())

            platformHelper
        } else {
            Java8PlatformHelper
        }

        platformHelper.addExports(processorClass)

        val processorConstructor: Constructor<*> = processorClass.getConstructor(ProcessingEnvironment::class.java, PlatformHelper::class.java, Map::class.java)

        processor = processorConstructor.newInstance(processingEnv, platformHelper, processingEnv.options) as BootstappedProcessor
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
        processor.process(roundEnvironment)

        return true
    }
}

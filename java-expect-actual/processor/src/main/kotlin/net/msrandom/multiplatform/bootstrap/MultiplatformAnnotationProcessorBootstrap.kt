package net.msrandom.multiplatform.bootstrap

import com.google.auto.service.AutoService
import net.msrandom.multiplatform.annotations.Actual
import net.msrandom.multiplatform.annotations.Expect
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
import kotlin.reflect.KClass

private const val GENERATE_EXPECT_STUBS_OPTION = "generateExpectStubs"

private val processorClass = Class.forName("net.msrandom.multiplatform.MultiplatformAnnotationProcessor")
private val processorConstructor: Constructor<*> = processorClass.getConstructor(ProcessingEnvironment::class.java, PlatformHelper::class.java, Boolean::class.java)
private val process = processorClass.getDeclaredMethod("process", RoundEnvironment::class.java)

@AutoService(Processor::class)
class MultiplatformAnnotationProcessorBootstrap : Processor {
    private lateinit var processor: Any

    override fun getSupportedOptions() =
        setOf(GENERATE_EXPECT_STUBS_OPTION)

    override fun getSupportedAnnotationTypes() = arrayOf(Expect::class, Actual::class)
        .map(KClass<*>::qualifiedName)
        .toSet()

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

        processor = processorConstructor.newInstance(processingEnv, platformHelper, GENERATE_EXPECT_STUBS_OPTION in processingEnv.options)
    }

    override fun process(annotations: MutableSet<out TypeElement>, roundEnvironment: RoundEnvironment): Boolean {
        process(processor, roundEnvironment)

        return true
    }

    override fun getCompletions(element: Element, annotation: AnnotationMirror, member: ExecutableElement, userText: String) =
        emptyList<Completion>()
}
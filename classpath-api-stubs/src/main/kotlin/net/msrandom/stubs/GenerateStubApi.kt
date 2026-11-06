package net.msrandom.stubs

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively

@CacheableTask
abstract class GenerateStubApi @Inject constructor(
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {
    abstract val classpaths: ListProperty<List<ResolvedArtifact>>
        @Nested get

    abstract val excludes: ListProperty<String>
        @Input get

    abstract val apiFileName: Property<String>
        @Input get

    abstract val outputDirectory: DirectoryProperty
        @OutputDirectory get

    init {
        apiFileName.convention("api-stub.jar")
        outputDirectory.convention(project.layout.dir(project.provider { temporaryDir }))
    }

    @TaskAction
    fun generateStub() {
        val outputDirectory = outputDirectory.asFile.get().toPath()

        @OptIn(ExperimentalPathApi::class)
        outputDirectory.deleteRecursively()
        outputDirectory.createDirectories()

        val apiFile = this.outputDirectory.file(apiFileName.get())

        val workQueue = workerExecutor.noIsolation()

        workQueue.submit(StubGenerationWork::class.java) {
            classpaths.set(this@GenerateStubApi.classpaths)
            excludes.set(this@GenerateStubApi.excludes)
            this.apiFile.value(apiFile)
            this.outputDirectory.value(this@GenerateStubApi.outputDirectory)
        }
    }

    interface StubGenerationParameters : WorkParameters {
        val classpaths: ListProperty<List<ResolvedArtifact>>
        val excludes: ListProperty<String>
        val apiFile: RegularFileProperty
        val outputDirectory: DirectoryProperty
    }

    abstract class StubGenerationWork : WorkAction<StubGenerationParameters> {
        override fun execute() {
            val params = parameters
            val apiFile = params.apiFile.get().asFile.toPath()
            val outputDir = params.outputDirectory.get().asFile.toPath()

            val extras = StubGenerator.generateStub(
                params.classpaths.get(),
                params.excludes.get(),
                apiFile
            )

            extras.parallelStream().forEach { artifact ->
                val directory = outputDir.resolve(
                    artifact.componentId.get().replace(':', '_')
                )

                directory.createDirectories()

                val path = artifact.file.asFile.get()
                path.toPath().copyTo(
                    directory.resolve(path.name),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        }
    }

    interface ResolvedArtifact {
        val componentId: Property<String>
            @Optional
            @Input
            get

        val type: Property<Type>
            @Optional
            @Input
            get

        val moduleVersion: Property<String>
            @Optional
            @Input
            get

        val moduleId: Property<ModuleIdentifier>
            @Optional
            @Input
            get

        val file: RegularFileProperty
            @CompileClasspath
            @InputFile
            get

        fun setComponent(component: ComponentIdentifier) {
            componentId.set(component.toString())

            when (component) {
                is ModuleComponentIdentifier -> {
                    type.set(Type.Module)
                    moduleId.set(component.moduleIdentifier)
                    moduleVersion.set(component.version)
                }
                is ProjectComponentIdentifier -> {
                    type.set(Type.Project)
                }
            }
        }

        enum class Type {
            Module,
            Project,
        }
    }
}
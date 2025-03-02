package net.msrandom.stubs

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

abstract class GenerateStubApi : DefaultTask() {
    abstract val classpaths: ListProperty<Classpath>
        @Nested get

    abstract val apiFileName: Property<String>
        @Input get

    abstract val outputDirectory: DirectoryProperty
        @OutputDirectory get

    abstract val objectFactory: ObjectFactory
        @Inject get

    init {
        apiFileName.convention("api-stub.jar")
        outputDirectory.convention(project.layout.dir(project.provider { temporaryDir }))
    }

    fun classpath(configuration: Configuration, vararg extraFiles: Any) {
        val classpath = objectFactory.newInstance(Classpath::class.java).also {
            it.artifacts.set(configuration.incoming.artifacts)
            it.extraFiles.from(extraFiles)
        }

        classpaths.add(classpath)
    }

    fun classpath(configuration: Provider<Configuration>, vararg extraFiles: Any) {
        val classpath = objectFactory.newInstance(Classpath::class.java).also {
            it.artifacts.set(configuration.map { it.incoming.artifacts })
            it.extraFiles.from(extraFiles)
        }

        classpaths.add(classpath)
    }

    @TaskAction
    fun generateStub() {
        val outputDirectory = outputDirectory.asFile.get().toPath()
        val apiFile = outputDirectory.resolve(apiFileName.get())

        val extras = StubGenerator.generateStub(classpaths.get(), apiFile)

        for (artifact in extras) {
            val directory = outputDirectory.resolve(artifact.id.componentIdentifier.displayName.replace(':', '_'))

            directory.createDirectories()

            artifact.file.toPath().copyTo(directory.resolve(artifact.file.name), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    interface Classpath {
        val artifacts: Property<ArtifactCollection>
            @Internal get

        val extraFiles: ConfigurableFileCollection
            @InputFiles get
    }
}

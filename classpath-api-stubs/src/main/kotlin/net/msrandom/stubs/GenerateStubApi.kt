package net.msrandom.stubs

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

@CacheableTask
abstract class GenerateStubApi : DefaultTask() {
    abstract val classpaths: ListProperty<Classpath>
        @Nested get

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
            @CompileClasspath
            @InputFiles
            get
    }
}

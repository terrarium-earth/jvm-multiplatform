package net.msrandom.stubs

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

@CacheableTask
abstract class GenerateStubApi : DefaultTask() {
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
        val apiFile = outputDirectory.resolve(apiFileName.get())

        val extras = StubGenerator.generateStub(classpaths.get(), excludes.get(), apiFile)

        for (artifact in extras) {
            val directory = outputDirectory.resolve(artifact.id.get().displayName.replace(':', '_'))

            directory.createDirectories()

            val path = artifact.file.asFile.get()

            path.toPath().copyTo(directory.resolve(path.name), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    interface ResolvedArtifact {
        val id: Property<ComponentIdentifier>
            @Optional
            @Input
            get

        val file: RegularFileProperty
            @CompileClasspath
            @InputFile
            get
    }
}

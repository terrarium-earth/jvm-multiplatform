package net.msrandom.stubs

import net.msrandom.stubs.ClassNodeIntersector.intersectClassNodes
import org.gradle.internal.impldep.kotlinx.coroutines.Dispatchers
import org.gradle.internal.impldep.kotlinx.coroutines.launch
import org.gradle.internal.impldep.kotlinx.coroutines.runBlocking
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipInputStream
import kotlin.io.path.*

object StubGenerator {

    private fun createFileIntersection(
        streams: List<Pair<ClasspathLoader, ClassNode>>,
        output: Path,
    ) {
        output.parent?.createDirectories()

        val (_, node) = streams.reduce { (classpathA, nodeA), (classpathB, nodeB) ->
            classpathB to intersectClassNodes(nodeA, nodeB, classpathA, classpathB)
        }

        val writer = ClassWriter(0)

        node.accept(writer)

        output.writeBytes(writer.toByteArray())
    }

    private fun getClassEntries(stream: ZipInputStream) = sequence {
        while (true) {
            val entry = stream.nextEntry

            if (entry == null) {
                break
            }

            if (entry.name.endsWith(".class")) {
                yield(entry.name)
            }
        }
    }

    fun generateStub(
        classpaths: Iterable<List<GenerateStubApi.ResolvedArtifact>>,
        extraExcludes: List<String>,
        output: Path
    ): List<GenerateStubApi.ResolvedArtifact> {
        val exclude = listOf(
            "org.jetbrains",
            "org.apache",
            "org.codehaus",
            "org.ow2",
            "org.lwjgl",
            "com.google",
            "net.java",
            "ca.weblite",
            "com.ibm",
            "org.scala-lang",
            "org.clojure",
            "io.netty",
            "org.slf4j",
            "org.lz4",
            "org.joml",
            "net.sf",
            "it.unimi",
            "commons-",
            "com.github",
            "org.antlr",
            "org.openjdk",
            "net.minecrell",
            "org.jline",
            "net.jodah",
            "org.checkerframework",
            "org.spongepowered",
            "net.fabricmc:sponge",
        ) + extraExcludes

        val classpaths = classpaths.map { artifacts ->
            val (excluded, included) = artifacts.partition {
                val type = it.type.orNull
                val id = it.componentId.orNull ?: return@partition false

                type === GenerateStubApi.ResolvedArtifact.Type.Module && exclude.any(id::startsWith)
            }

            ClasspathLoader(
                included.map { it.file.asFile.get() },
                excluded,
            )
        }

        val first = classpaths.first()
        val rest = classpaths.subList(1, classpaths.size)

        val entries = first.intersectionIncluded.asSequence().flatMap {
            getClassEntries(ZipInputStream(it.inputStream()))
        }

        val filteredEntries = entries.filter { entry ->
            rest.all {
                it.hasEntry(entry)
            }
        }

        output.deleteIfExists()
        FileSystems.newFileSystem(URI.create("jar:${output.toUri()}"), mapOf("create" to true.toString()))
            .use { fileSystem ->
                val manifestPath = fileSystem.getPath(JarFile.MANIFEST_NAME)

                manifestPath.parent.createDirectory()

                manifestPath.outputStream().use(Manifest()::write)

                runBlocking(Dispatchers.IO) {
                    filteredEntries.map { entry ->
                        launch {
                            val streams = classpaths.map {
                                it to it.entry(entry)!!
                            }

                            val path = fileSystem.getPath(entry)

                            synchronized(fileSystem) {
                                path.parent?.createDirectories()
                            }

                            createFileIntersection(streams, path)
                        }
                    }.forEach { it.join() }
                }
            }

        val intersectedExcludedArtifacts = classpaths.map { it.intersectionExcluded }.reduce { a, b ->
            val artifactsA = a.groupBy { it.moduleId.get() }

            val artifactsB = b.groupBy { it.moduleId.get() }

            val intersections = artifactsA.keys.intersect(artifactsB.keys)

            intersections.flatMap { id ->
                val relevantArtifactsA = artifactsA[id] ?: return@flatMap emptyList()
                val relevantArtifactsB = artifactsB[id] ?: return@flatMap emptyList()

                listOf(relevantArtifactsA, relevantArtifactsB).minBy {
                    it[0].moduleVersion.get()
                }
            }
        }

        for (classpath in classpaths) {
            classpath.close()
        }

        return intersectedExcludedArtifacts
    }

    internal class ClasspathLoader(
        val intersectionIncluded: List<File>,
        val intersectionExcluded: List<GenerateStubApi.ResolvedArtifact>,
        // Cache bytecode to avoid repeated JAR reads
        private val bytecodeCache: ConcurrentHashMap<String, ByteArray?> = ConcurrentHashMap(),
    ) : AutoCloseable {
        private val allFiles = intersectionIncluded + intersectionExcluded.map { it.file.asFile.get() }

        // Use JarFile for thread-safe access
        private val jarFiles = allFiles.map { JarFile(it) }

        fun hasEntry(name: String): Boolean {
            return jarFiles.any { it.getEntry(name) != null }
        }

        fun entry(name: String): ClassNode? {
            val bytecode = bytecodeCache.computeIfAbsent(name) {
                for (jar in jarFiles) {
                    val entry = jar.getEntry(name) ?: continue
                    return@computeIfAbsent jar.getInputStream(entry).use { it.readBytes() }
                }
                null
            } ?: return null

            // Parse fresh ClassNode from bytecode to avoid shared mutable state
            return ClassNode().apply {
                ClassReader(bytecode).accept(this, 0)
            }
        }

        override fun close() {
            jarFiles.forEach { it.close() }
        }
    }
}
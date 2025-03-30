package net.msrandom.stubs

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.MethodNode
import java.io.Closeable
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.math.min

object StubGenerator {
    private const val VISIBILITY_MASK = Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED

    // Choose lower visibility of the two
    private fun accessIntersection(
        a: Int,
        b: Int,
    ): Int {
        val visibilityA = a and VISIBILITY_MASK
        val visibilityB = b and VISIBILITY_MASK

        fun visibilityOrdinal(value: Int) =
            when (value) {
                Opcodes.ACC_PRIVATE -> 0
                Opcodes.ACC_PROTECTED -> 2
                Opcodes.ACC_PUBLIC -> 3
                else -> 1
            }

        val visibility =
            if (visibilityOrdinal(visibilityA) > visibilityOrdinal(visibilityB)) {
                visibilityB
            } else {
                visibilityA
            }

        val deprecated = a and Opcodes.ACC_DEPRECATED

        val base = if (deprecated != (b and Opcodes.ACC_DEPRECATED)) {
            // One is potentially deprecated while the other isn't, choose the not deprecated one
            if (deprecated == 0) {
                a
            } else {
                b
            }
        } else {
            a
        }

        return (base and VISIBILITY_MASK.inv()) or visibility
    }

    private fun findCommonSuper(
        nodeA: ClassNode,
        nodeB: ClassNode,
        classpathA: ClasspathLoader,
        classpathB: ClasspathLoader
    ): String? {
        var superNameA = nodeA.superName
        var superNameB = nodeB.superName

        val visitedSuperNamesA = mutableListOf<String?>(superNameA)
        val visitedSuperNamesB = hashSetOf<String?>(superNameB)

        while (true) {
            for (name in visitedSuperNamesA) {
                if (name in visitedSuperNamesB) {
                    return name
                }
            }

            if (superNameA != null) {
                val superA = classpathA.entry("$superNameA.class")

                if (superA != null) {
                    superNameA = superA.superName

                    if (superNameA in visitedSuperNamesB) {
                        return superNameA
                        break
                    }

                    visitedSuperNamesA.add(superNameA)
                } else {
                    return superNameA
                    break
                }
            }

            if (superNameB != null) {
                val superB = classpathB.entry("$superNameB.class")

                if (superB != null) {
                    superNameB = superB.superName

                    if (superNameB in visitedSuperNamesA) {
                        return superNameB
                    }

                    visitedSuperNamesB.add(superNameB)
                } else {
                    return superNameB
                }
            }
        }
    }

    private fun classIntersection(
        nodeA: ClassNode,
        nodeB: ClassNode,
        classpathA: ClasspathLoader,
        classpathB: ClasspathLoader,
    ): ClassNode {
        val node = ClassNode()

        node.version = min(nodeA.version, nodeB.version)
        node.access = nodeA.access
        node.name = nodeA.name
        node.signature = nodeB.signature?.let { nodeA.signature?.commonPrefixWith(it) }

        node.superName = findCommonSuper(nodeA, nodeB, classpathA, classpathB)

        node.interfaces = nodeA.interfaces.intersect(nodeB.interfaces.toSet()).toList()

        node.innerClasses =
            nodeA.innerClasses.mapNotNull { innerClass ->
                val inner = nodeB.innerClasses.firstOrNull {
                    it.name == innerClass.name
                }

                if (inner == null) {
                    null
                } else {
                    InnerClassNode(
                        innerClass.name,
                        innerClass.outerName,
                        innerClass.innerName,
                        accessIntersection(inner.access, innerClass.access),
                    )
                }
            }

        node.outerClass = nodeA.outerClass
        node.outerMethod = nodeA.outerMethod
        node.outerMethodDesc = nodeA.outerMethodDesc

        node.fields =
            nodeA.fields.mapNotNull { field ->
                nodeB.fields.firstOrNull { it.name == field.name && it.desc == field.desc }?.let {
                    FieldNode(accessIntersection(it.access, field.access), it.name, it.desc, it.signature, null)
                }
            }

        node.methods =
            nodeA.methods.mapNotNull { methodA ->
                nodeB.methods.firstOrNull { it.name == methodA.name && it.desc == methodA.desc }?.let { methodB ->
                    // Should we use `visit` for supporting all the fields in the method node?
                    MethodNode(
                        accessIntersection(methodB.access, methodA.access),
                        methodB.name,
                        methodB.desc,
                        methodB.signature,
                        methodB.exceptions.toTypedArray().intersect(methodA.exceptions.toSet()).toTypedArray(),
                    ).also {
                        if (methodA.annotationDefault != null) {
                            it.annotationDefault = methodA.annotationDefault
                        }
                    }
                }
            }

        return node
    }

    private fun createFileIntersection(
        streams: List<Pair<ClasspathLoader, ClassNode>>,
        output: Path,
    ) {
        output.parent?.createDirectories()

        val (_, node) = streams.reduce { (classpathA, nodeA), (classpathB, nodeB) ->
            classpathB to classIntersection(nodeA, nodeB, classpathA, classpathB)
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

    fun generateStub(classpaths: Iterable<GenerateStubApi.Classpath>, output: Path): List<ResolvedArtifactResult> {
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
        )

        val classpaths = classpaths.map {
            val artifacts = it.artifacts.get()

            assert(artifacts.none { it is ProjectComponentIdentifier })

            val (excluded, included) = artifacts.partition {
                val id = it.id.componentIdentifier

                id is ModuleComponentIdentifier && exclude.any(id.group::startsWith)
            }

            ClasspathLoader(
                included.map(ResolvedArtifactResult::getFile) + it.extraFiles,
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
                it.loader.getResource(entry) != null
            }
        }

        output.deleteIfExists()
        FileSystems.newFileSystem(URI.create("jar:${output.toUri()}"), mapOf("create" to true.toString()))
            .use { fileSystem ->
                val manifestPath = fileSystem.getPath(JarFile.MANIFEST_NAME)

                manifestPath.parent.createDirectory()

                manifestPath.outputStream().use(Manifest()::write)

                for (entry in filteredEntries) {
                    val streams = classpaths.map {
                        it to it.entry(entry)!!
                    }

                    val path = fileSystem.getPath(entry)

                    path.parent?.createDirectories()

                    createFileIntersection(streams, path)
                }
            }

        val intersectedExcludedArtifacts = classpaths.map { it.intersectionExcluded }.reduce { a, b ->
            val artifactsA = a.groupBy {
                (it.id.componentIdentifier as ModuleComponentIdentifier).moduleIdentifier
            }

            val artifactsB = b.groupBy {
                (it.id.componentIdentifier as ModuleComponentIdentifier).moduleIdentifier
            }

            val intersections = artifactsA.keys.intersect(artifactsB.keys)

            intersections.flatMap { id ->
                val relevantArtifactsA = artifactsA[id] ?: return@flatMap emptyList()
                val relevantArtifactsB = artifactsB[id] ?: return@flatMap emptyList()

                listOf(relevantArtifactsA, relevantArtifactsB).minBy {
                    (it[0].id.componentIdentifier as ModuleComponentIdentifier).version
                }
            }
        }

        for (classpath in classpaths) {
            // TODO Make this exception safe
            classpath.close()
        }

        return intersectedExcludedArtifacts
    }

    private class ClasspathLoader(
        val intersectionIncluded: List<File>,
        val intersectionExcluded: List<ResolvedArtifactResult>,
        private val cache: MutableMap<String, ClassNode?> = hashMapOf(),
    ) : AutoCloseable {
        val loader = URLClassLoader(
            (intersectionIncluded + intersectionExcluded.map(ResolvedArtifactResult::getFile))
                .map { it.toURI().toURL() }
                .toTypedArray(),
        )

        fun entry(name: String) = cache.computeIfAbsent(name) {
            loader.getResourceAsStream(name)?.let { stream ->
                ClassNode().apply {
                    stream.use(::ClassReader).accept(this, 0)
                }
            }
        }

        override fun close() = loader.close()
    }
}

@file:OptIn(InternalKotlinGradlePluginApi::class)

package net.msrandom.virtualsourcesets

import com.google.devtools.ksp.gradle.KspTaskJvm
import net.msrandom.virtualsourcesets.model.VirtualSourceSetModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.K2MultiplatformStructure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject
import kotlin.collections.any
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

@Suppress("UNCHECKED_CAST")
private val commonSourceSet = KotlinCompile::class.memberProperties
    .first { it.name == "commonSourceSet" }
    .apply { isAccessible = true } as KProperty1<KotlinCompile, ConfigurableFileCollection>

private const val KOTLIN_JVM = "org.jetbrains.kotlin.jvm"

private val KotlinCompile.isK2: Provider<Boolean>
    get() = compilerOptions.languageVersion
        .orElse(KotlinVersion.DEFAULT)
        .map { it >= KotlinVersion.KOTLIN_2_0 }

fun <T> KotlinCompile.addK2Argument(property: HasMultipleValues<T>, value: () -> T) {
    property.addAll(isK2.map {
        if (it) {
            listOf(value())
        } else {
            emptyList()
        }
    })
}

@Suppress("unused")
open class JavaVirtualSourceSetsPlugin @Inject constructor(private val modelBuilderRegistry: ToolingModelBuilderRegistry) :
    Plugin<Project> {
    private fun Project.extend(base: String, dependency: String) = project.configurations.findByName(dependency)?.let {
        project.configurations.findByName(base)?.extendsFrom(it)
    }

    private fun SourceSet.addJavaCommonSources(dependency: SourceSet, task: JavaCompile) {
        task.source(dependency.java)

        dependency.extensions.getByType(SourceSetStaticLinkageInfo::class.java).links.all {
            addJavaCommonSources(it, task)
        }
    }

    private fun SourceSet.addCommonResources(dependency: SourceSet, task: ProcessResources) {
        task.from(dependency.resources)

        dependency.extensions.getByType(SourceSetStaticLinkageInfo::class.java).links.all {
            addCommonResources(it, task)
        }
    }

    private fun SourceSet.setupKotlinStubs(
        kotlin: KotlinJvmExtension,
        taskContainer: TaskContainer,
        buildDirectory: DirectoryProperty,
        fileOperations: FileOperations,
    ) {
        val compilation = kotlin.target.compilations.getByName(name)
        val kspTaskName = compilation.compileKotlinTaskName.replace("compile", "ksp")

        val stubsDirectory = buildDirectory.dir("generated").map {
            it.dir("ksp").dir(kotlin.target.name).dir(name).dir("stubs")
        }

        fun configureTask(compileTask: KotlinCompile) {
            compileTask.multiPlatformEnabled.set(true)

            val kotlinSourceSet = kotlin.sourceSets.getByName(name)

            compileTask.addK2Argument(compileTask.multiplatformStructure.fragments) {
                K2MultiplatformStructure.Fragment(kotlinSourceSet.name, kotlinSourceSet.kotlin.asFileTree)
            }

            compileTask.addK2Argument(compileTask.multiplatformStructure.fragments) {
                K2MultiplatformStructure.Fragment("stub-implementation", fileOperations.fileTree(stubsDirectory))
            }

            compileTask.addK2Argument(compileTask.multiplatformStructure.refinesEdges) {
                K2MultiplatformStructure.RefinesEdge(kotlinSourceSet.name, "stub-implementation")
            }

            commonSourceSet.get(compileTask).from(stubsDirectory)
            compileTask.source(stubsDirectory)
        }

        taskContainer.withType(KspTaskJvm::class.java).named(kspTaskName::equals).all {
            println("${it.name}")
            // Ksp task exists, setup stub directory to be used if needed

            it.options.add(SubpluginOption("actualStubDir", lazy { stubsDirectory.get().toString() }))

            configureTask(it)
        }

        compilation.compileTaskProvider.configure {
            configureTask(it as KotlinCompile)
        }
    }

    private fun SourceSet.addKotlinCommonSources(
        kotlin: KotlinJvmExtension,
        providerFactory: ProviderFactory,
        dependency: SourceSet,
        info: SourceSetStaticLinkageInfo,
        compileTask: KotlinCompile,
        taskContainer: TaskContainer,
        buildDirectory: DirectoryProperty,
        fileOperations: FileOperations,
    ) {
        val kotlinSourceSet = kotlin.sourceSets.getByName(name)
        val kotlinDependency = kotlin.sourceSets.getByName(dependency.name)

        fun addFragment(sourceSet: KotlinSourceSet) {
            if (compileTask.multiplatformStructure.fragments.get().any { it.fragmentName == sourceSet.name }) {
                return
            }

            compileTask.addK2Argument(compileTask.multiplatformStructure.fragments) {
                K2MultiplatformStructure.Fragment(sourceSet.name, sourceSet.kotlin.asFileTree)
            }
        }

        compileTask.addK2Argument(compileTask.multiplatformStructure.refinesEdges) {
            K2MultiplatformStructure.RefinesEdge(kotlinSourceSet.name, kotlinDependency.name)
        }

        addFragment(kotlinSourceSet)
        addFragment(kotlinDependency)

        val emptyList: Provider<List<K2MultiplatformStructure.RefinesEdge>> = providerFactory.provider { emptyList() }

        val weakLinks = info.weakTreeLinks(dependency)

        compileTask.multiplatformStructure.refinesEdges.addAll(compileTask.isK2.flatMap {
            if (it) {
                weakLinks.map {
                    it.map { to ->
                        K2MultiplatformStructure.RefinesEdge(kotlinDependency.name, to.name)
                    }
                }
            } else {
                emptyList
            }
        })

        commonSourceSet.get(compileTask).from(kotlinDependency.kotlin)
        compileTask.source(kotlinDependency.kotlin)

        dependency.extensions.getByType(SourceSetStaticLinkageInfo::class.java).links.all {
            it.setupKotlinStubs(kotlin, taskContainer, buildDirectory, fileOperations)

            dependency.addKotlinCommonSources(kotlin, providerFactory, it, info, compileTask, taskContainer, buildDirectory, fileOperations)
        }
    }

    private fun SourceSet.addDependency(dependency: SourceSet, info: SourceSetStaticLinkageInfo, project: Project) {
        if (System.getProperty("idea.sync.active")?.toBoolean() == true) {
            // TODO Temporary until an intellij plugin is complete
            compileClasspath += dependency.output
        }

        project.extend(apiConfigurationName, dependency.apiConfigurationName)
        project.extend(compileOnlyApiConfigurationName, dependency.compileOnlyApiConfigurationName)
        project.extend(implementationConfigurationName, dependency.implementationConfigurationName)
        project.extend(runtimeOnlyConfigurationName, dependency.runtimeOnlyConfigurationName)
        project.extend(compileOnlyConfigurationName, dependency.compileOnlyConfigurationName)

        project.tasks.named(compileJavaTaskName, JavaCompile::class.java) {
            addJavaCommonSources(dependency, it)
        }

        project.tasks.named(processResourcesTaskName, ProcessResources::class.java) {
            addCommonResources(dependency, it)
        }

        project.plugins.withId(KOTLIN_JVM) {
            val kotlin = project.extensions.getByType(KotlinJvmExtension::class.java)
            val kotlinCompilation = kotlin.target.compilations.getByName(name)

            kotlinCompilation.compileTaskProvider.configure {
                it as KotlinCompile

                it.multiPlatformEnabled.set(true)

                addKotlinCommonSources(kotlin, project.serviceOf(), dependency, info, it, project.tasks, project.layout.buildDirectory, project.serviceOf())
            }
        }
    }

    override fun apply(target: Project) {
        target.plugins.apply(JavaPlugin::class.java)

        target.extensions.getByType(SourceSetContainer::class.java).all { sourceSet ->
            val staticLinkInfo =
                sourceSet.extensions.create("staticLinkage", SourceSetStaticLinkageInfo::class.java, sourceSet, target.objects)

            staticLinkInfo.links.all { dependency ->
                sourceSet.addDependency(dependency, staticLinkInfo, target)
            }
        }

        modelBuilderRegistry.register(VirtualSourceSetModelBuilder())
    }
}

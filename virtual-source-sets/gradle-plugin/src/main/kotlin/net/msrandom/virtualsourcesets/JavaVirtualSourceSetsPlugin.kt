@file:OptIn(InternalKotlinGradlePluginApi::class)

package net.msrandom.virtualsourcesets

import net.msrandom.virtualsourcesets.model.VirtualSourceSetModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.extensions.core.serviceOf
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleJavaTargetExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import org.jetbrains.kotlin.gradle.tasks.K2MultiplatformStructure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject
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

fun <T : Any> KotlinCompile.addK2Argument(property: HasMultipleValues<T>, value: () -> T) {
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

    private fun SourceSet.addJavaCommonSources(task: JavaCompile) {
        task.source(java)

        extensions.getByType<SourceSetStaticLinkageInfo>().links.all {
            addJavaCommonSources(task)
        }
    }

    private fun SourceSet.addCommonResources(task: ProcessResources) {
        task.from(resources)

        extensions.getByType<SourceSetStaticLinkageInfo>().links.all {
            addCommonResources(task)
        }
    }

    private fun SourceSet.addKotlinCommonSources(
        kotlin: KotlinSourceSetContainer,
        providerFactory: ProviderFactory,
        dependency: SourceSet,
        info: SourceSetStaticLinkageInfo,
        compileTask: KotlinCompile,
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

        dependency.extensions.getByType<SourceSetStaticLinkageInfo>().links.all {
            dependency.addKotlinCommonSources(kotlin, providerFactory, this, info, compileTask)
        }
    }

    private fun SourceSet.addDependency(dependency: SourceSet, info: SourceSetStaticLinkageInfo, project: Project) {
        if (System.getProperty("idea.sync.active")?.toBoolean() == true) {
            // TODO Temporary until an intellij plugin is complete
            compileClasspath += dependency.output
        }

        project.tasks.named(compileJavaTaskName, JavaCompile::class.java) {
            dependency.addJavaCommonSources(this)
        }

        project.tasks.named(processResourcesTaskName, ProcessResources::class.java) {
            dependency.addCommonResources(this)
        }

        project.plugins.withId(KOTLIN_JVM) {
            val kotlin = project.extensions.getByType<KotlinSingleJavaTargetExtension>()
            val kotlinCompilation = kotlin.target.compilations.getByName(name)

            kotlinCompilation.compileTaskProvider.configure {
                this as KotlinCompile

                this.multiPlatformEnabled.set(true)

                addKotlinCommonSources(kotlin, project.serviceOf(), dependency, info, this)
            }
        }
    }

    override fun apply(target: Project) {
        target.apply<JavaPlugin>()

        target.extensions.getByType<SourceSetContainer>().all {
            val sourceSet = this

            val staticLinkInfo =
                sourceSet.extensions.create(
                    "staticLinkage",
                    SourceSetStaticLinkageInfo::class,
                    sourceSet,
                    target.objects
                )

            staticLinkInfo.links.all {
                sourceSet.addDependency(this, staticLinkInfo, target)
            }
        }

        modelBuilderRegistry.register(VirtualSourceSetModelBuilder())
    }
}

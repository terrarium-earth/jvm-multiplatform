package net.msrandom.classextensions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class ClassExtensionsPlugin : Plugin<Project>, KotlinCompilerPluginSupportPlugin {
    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.project.provider { emptyList() }

    override fun getCompilerPluginId() = "kotlin-class-extensions"

    override fun getPluginArtifact() = SubpluginArtifact("net.msrandom", "kotlin-class-extensions-plugin", "1.0.10")

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = kotlinCompilation.allKotlinSourceSets.any {
        it.dependsOn.isNotEmpty()
    }
}

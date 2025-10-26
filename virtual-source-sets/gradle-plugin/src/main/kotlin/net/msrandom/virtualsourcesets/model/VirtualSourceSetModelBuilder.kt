package net.msrandom.virtualsourcesets.model

import com.google.auto.service.AutoService
import net.msrandom.virtualsourcesets.SourceSetStaticLinkageInfo
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.getByType
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.Serializable

class VirtualSourceSet(val name: String, val dependencies: MutableList<VirtualSourceSet>)

sealed interface VirtualSourceSetsModel {
    val sourceSets: List<VirtualSourceSet>
}

class VirtualSourceSetsModelImpl(override val sourceSets: List<VirtualSourceSet>) : VirtualSourceSetsModel, Serializable

@AutoService(ToolingModelBuilder::class)
class VirtualSourceSetModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String) =
        modelName == VirtualSourceSetsModel::class.qualifiedName

    override fun buildAll(modelName: String, project: Project): VirtualSourceSetsModel {
        val sourceSetMap = hashMapOf<String, VirtualSourceSet>()

        fun computeSourceSet(name: String) = sourceSetMap.computeIfAbsent(name) {
            VirtualSourceSet(it, mutableListOf())
        }

        project.extensions
            .getByType<SourceSetContainer>()
            .all {
                val dependencies = computeSourceSet(name).dependencies

                extensions.getByType<SourceSetStaticLinkageInfo>().links.all {
                    dependencies.add(computeSourceSet(name))
                }
            }

        return VirtualSourceSetsModelImpl(sourceSetMap.values.toList())
    }
}

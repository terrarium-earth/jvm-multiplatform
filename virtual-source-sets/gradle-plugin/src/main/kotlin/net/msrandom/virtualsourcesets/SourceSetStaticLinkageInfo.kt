package net.msrandom.virtualsourcesets

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.SourceSet
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.listProperty

open class SourceSetStaticLinkageInfo(val sourceSet: SourceSet, private val objectFactory: ObjectFactory) {
    val links = objectFactory.domainObjectSet(SourceSet::class)

    private val weakTreeLinks = hashMapOf<SourceSet, ListProperty<SourceSet>>()

    /**
     * Means the compilation of [sourceSet] statically relies on the source of [dependency], and will be compiled alongside it
     * This is used for the IntelliJ model building as well
     */
    fun link(dependency: SourceSet) {
        links.add(dependency)
    }

    /**
     * Implies that [from] depends on [to] in the tree defined, this only affects the Kotlin Multiplatform K2 compiler's source dependency resolution.
     * Whether [from] and [to] are linked in any way is not handled by this method, as such they can be dynamically or statically linked as needed
     * This is not used for IntelliJ model building, [from] and [to] will only have the dependencies set using from.static.link(to) in the IntelliJ model
     */
    fun weakTreeLink(from: SourceSet, to: SourceSet) {
        weakTreeLinks(from).add(to)
    }

    fun weakTreeLinks(from: SourceSet) = weakTreeLinks.computeIfAbsent(from) {
        objectFactory.listProperty<SourceSet>()
    }
}

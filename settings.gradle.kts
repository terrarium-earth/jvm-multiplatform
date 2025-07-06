enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "jvm-multiplatform"

fun includeSubproject(name: String, path: String) {
    include(name)

    project(":$name").projectDir = file(path)
}

includeSubproject("jvm-virtual-source-sets-idea", "virtual-source-sets/idea-plugin")
includeSubproject("jvm-virtual-source-sets", "virtual-source-sets/gradle-plugin")

includeSubproject("java-expect-actual-idea", "java-expect-actual/idea-plugin")
includeSubproject("java-expect-actual-annotations", "java-expect-actual/annotations")
includeSubproject("java-expect-actual-processor", "java-expect-actual/processor")

includeSubproject("kmp-actual-stub-annotations", "kmp-actual-stubs/annotations")
includeSubproject("kmp-actual-stubs-processor", "kmp-actual-stubs/processor")

includeSubproject("class-extensions-idea", "class-extensions/idea-plugin")
includeSubproject("class-extensions-gradle-plugin", "class-extensions/gradle-plugin")
includeSubproject("class-extension-annotations", "class-extensions/annotations")
includeSubproject("java-class-extensions-processor", "class-extensions/java-processor")
includeSubproject("kotlin-class-extensions-plugin", "class-extensions/kotlin-plugin")

include("java-processor-util")
include("classpath-api-stubs")

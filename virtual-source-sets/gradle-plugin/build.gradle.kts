plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins.create("javaVirtualSourceSets") {
        id = "net.msrandom.virtual-source-sets"

        displayName = "Java Virtual Source Sets"

        description =
            "A plugin that allows including a source set for compilation with another, " +
            "rather than compiling separately and depending on each other"

        implementationClass = "net.msrandom.virtualsourcesets.JavaVirtualSourceSetsPlugin"
    }
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}

repositories {
    mavenCentral()
}

dependencies {
    ksp(group = "dev.zacsweers.autoservice", name = "auto-service-ksp", version = "1.2.0")
    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1")

    implementation(group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version = "2.1.0-1.0.29")

    compileOnly(kotlin("gradle-plugin"))
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins.create("classpathApiStubs") {
        id = "net.msrandom.classpath-api-stubs"

        displayName = "Classpath API Stubs"

        description = "A plugin that allows generating a JVM stub Jar based on the intersection of multiple classpaths"

        implementationClass = "net.msrandom.stubs.ClasspathApiStubsPlugin"
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
    implementation(group = "org.ow2.asm", name = "asm-tree", version = "9.7.1")
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.8.1")
    // implementation(group = "org.jetbrains.kotlin", name = "kotlin-metadata-jvm", version = "2.1.0")

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

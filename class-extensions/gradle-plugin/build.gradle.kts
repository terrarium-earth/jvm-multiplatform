plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins.create("jvmClassExtensions") {
        id = "net.msrandom.classextensions"

        displayName = "JVM Class Extensions"

        description = "All"

        implementationClass = "net.msrandom.classextensions.ClassExtensionsPlugin"
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
    compileOnly(kotlin("gradle-plugin"))
}

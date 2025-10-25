import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

// Setup Java 8 for main source set
java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}

// Setup Java 11 for Java 11+ support
val java11: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

@Suppress("UNCHECKED_CAST")
private fun <T> uncheckedNull() = null as T

dependencies {
    ksp(group = "dev.zacsweers.autoservice", name = "auto-service-ksp", version = "1.2.0")

    java11.compileOnlyConfigurationName(compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1"))

    val java8Launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    val jvm8 = java8Launcher.map {
        Jvm.discovered(
            it.metadata.installationPath.asFile,
            uncheckedNull(),
            it.metadata.languageVersion.asInt(),
        )
    }

    // Include tools Jar to allow using compiler APIs
    compileOnlyApi(files(jvm8.map(Jvm::getToolsJar)))
}

java {
    registerFeature(java11.name) {
        capability(group.toString(), name, version.toString())

        usingSourceSet(java11)

        withSourcesJar()
    }
}

configurations.named(java11.apiElementsConfigurationName) {
    attributes {
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
    }
}

configurations.named(java11.runtimeElementsConfigurationName) {
    attributes {
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
    }
}

configurations.named(java11.sourcesElementsConfigurationName) {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
    }
}

tasks.named<JavaCompile>(java11.compileJavaTaskName) {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
}

tasks.named<KotlinCompile>(java11.getCompileTaskName("kotlin")) {
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
}

artifacts {
    add(java11.apiElementsConfigurationName, tasks.jar)
    add(java11.runtimeElementsConfigurationName, tasks.jar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

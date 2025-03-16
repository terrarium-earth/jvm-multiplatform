import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
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

dependencies {
    implementation(projects.javaExpectActualAnnotations)

    val java8Launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    val jvm8 = java8Launcher.get().metadata.let {
        Jvm.discovered(
            it.installationPath.asFile,
            null,
            it.languageVersion.asInt()
        )
    }

    // Include tools Jar to allow using compiler APIs
    compileOnly(files(jvm8.toolsJar))
}

// Setup Java 11 for Java 11+ support
val java11: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

java {
    registerFeature(java11.name) {
        val capability = ProjectDerivedCapability(project)

        capability(capability.group, capability.name, capability.version)

        usingSourceSet(sourceSets[java11.name])

        withSourcesJar()
    }
}

dependencies {
    java11.implementationConfigurationName(projects.javaExpectActualAnnotations)

    ksp(group = "dev.zacsweers.autoservice", name = "auto-service-ksp", version = "1.2.0")

    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1")
    java11.compileOnlyConfigurationName(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1")
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

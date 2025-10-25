import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    groovy
    `maven-publish`
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

val gradleToolingExtension: SourceSet by sourceSets.creating

val gradleToolingExtensionJar = tasks.register(gradleToolingExtension.jarTaskName, Jar::class) {
    from(gradleToolingExtension.output)

    archiveClassifier.set(gradleToolingExtension.name)
}

tasks.named(gradleToolingExtension.getCompileTaskName("groovy"), GroovyCompile::class) {
    classpath += files(gradleToolingExtension.kotlin.destinationDirectory)
}

repositories {
    maven(url = "https://www.jetbrains.com/intellij-repository/releases/")
}

dependencies {
    gradleToolingExtension.implementationConfigurationName(kotlin("stdlib"))

    gradleToolingExtension.compileOnlyConfigurationName(group = "com.jetbrains.intellij.gradle", name = "gradle-tooling-extension", version = "latest.release") {
        exclude("org.jetbrains.intellij.deps", "gradle-api")
    }

    implementation(files(gradleToolingExtensionJar))

    intellijPlatform {
        intellijIdeaCommunity("2024.3.1")

        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.gradle")
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

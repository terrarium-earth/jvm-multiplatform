plugins {
    kotlin("jvm") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("org.jetbrains.intellij") version "1.17.4" apply false
}

subprojects {
    plugins.apply("maven-publish")

    configure<PublishingExtension> {
        repositories {
            maven("https://maven.msrandom.net/repository/root/") {
                credentials {
                    val mavenUsername: String? by project
                    val mavenPassword: String? by project

                    username = mavenUsername
                    password = mavenPassword
                }
            }
        }
    }
}

plugins {
    `embedded-kotlin` apply false
    id("com.google.devtools.ksp") version "$embeddedKotlinVersion-+" apply false
    id("org.jetbrains.intellij.platform") version "2.2.1" apply false
}

subprojects {
    plugins.apply("maven-publish")

    configure<PublishingExtension> {
        repositories {
            val isPluginClasspath = "annotation" !in project.name &&
                    "processor" !in project.name &&
                    project.path != rootProject.projects.kotlinClassExtensionsPlugin.path &&
                    project.path != rootProject.projects.kmpActualStubsCompilerPlugin.path

            val repository = if (isPluginClasspath) {
                "cloche"
            } else {
                "root"
            }

            maven("https://maven.msrandom.net/repository/$repository/") {
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

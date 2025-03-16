plugins {
  kotlin("jvm")
  id("org.jetbrains.intellij.platform")
}

repositories {
  mavenCentral()

  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  implementation(projects.javaExpectActualAnnotations)

  intellijPlatform {
    intellijIdeaCommunity("2024.3.1")

    bundledPlugin("com.intellij.java")
    bundledPlugin("com.intellij.gradle")
    localPlugin(projects.jvmVirtualSourceSetsIdea)
  }
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "11"
    targetCompatibility = "11"
  }

  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
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

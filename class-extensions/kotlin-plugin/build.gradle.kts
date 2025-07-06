plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://jitpack.io/")
}

dependencies {
    ksp(group = "dev.zacsweers.autoservice", name = "auto-service-ksp", version = "1.2.0")
    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1")
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-compiler-embeddable", version = "2.1.0")

    implementation(projects.classExtensionAnnotations)

    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

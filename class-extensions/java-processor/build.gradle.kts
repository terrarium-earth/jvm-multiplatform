plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    `maven-publish`
}

repositories {
    mavenCentral()
}

java {
    withSourcesJar()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation(projects.classExtensionAnnotations)
    implementation(projects.javaProcessorUtil)

    ksp(group = "dev.zacsweers.autoservice", name = "auto-service-ksp", version = "1.2.0")
    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

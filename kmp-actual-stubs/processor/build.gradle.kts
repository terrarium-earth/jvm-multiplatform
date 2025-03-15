plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    `maven-publish`
}

kotlin {
    jvmToolchain(8)
}

repositories {
    mavenCentral()
}

dependencies {
    ksp(group = "dev.zacsweers.autoservice", name = "auto-service-ksp", version = "1.2.0")
    compileOnly(group = "com.google.auto.service", name = "auto-service-annotations", version = "1.1.1")

    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.0-1.0.29")
    implementation("com.squareup:kotlinpoet:1.11.0")
    implementation("com.squareup:kotlinpoet-ksp:1.12.0")

    implementation(projects.kmpActualStubAnnotations)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

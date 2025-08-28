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

    implementation(kotlin("compiler-embeddable"))

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
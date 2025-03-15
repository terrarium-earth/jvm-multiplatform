plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin {
    jvmToolchain(8)
}

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

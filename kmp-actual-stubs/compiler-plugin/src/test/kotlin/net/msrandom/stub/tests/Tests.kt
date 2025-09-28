package net.msrandom.stub.tests

import kotlin.test.Test
import org.jetbrains.kotlin.cli.jvm.main as kotlinc

class Tests {
    @Test
    fun `Test Plugin`() {
        val classpath = System.getProperty("java.class.path")
        val annotations = "../annotations/build/libs/kmp-actual-stub-annotations-1.0.0.jar"

        val arguments = arrayOf(
            "src/test/resources/test.kt",

            "-language-version", "2.1",

            "-classpath",
            "$classpath:$annotations:../../kotlin/kotlin-stdlib.jar",

            "-Xcompiler-plugin=build/libs/kmp-actual-stubs-compiler-plugin.jar",

            "-Xmulti-platform",

            "-Xfragments=main",
            "-Xfragment-sources=main:src/test/resources/test.kt",
        )

        kotlinc(arguments)
    }
}
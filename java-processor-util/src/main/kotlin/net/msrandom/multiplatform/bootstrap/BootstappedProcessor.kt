package net.msrandom.multiplatform.bootstrap

import javax.annotation.processing.RoundEnvironment

// Loaded reflectively after com.sun.tools.javac packages are exported
interface BootstappedProcessor {
    fun process(roundEnvironment: RoundEnvironment)
}

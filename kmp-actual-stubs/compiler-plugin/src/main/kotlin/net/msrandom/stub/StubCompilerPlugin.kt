package net.msrandom.stub

import com.google.auto.service.AutoService
import net.msrandom.stub.fir.FirExpectEnumConstructorGenerator
import net.msrandom.stub.fir.FirExpectTransformer
import net.msrandom.stub.ir.IrStubBodyGenerator
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class StubCompilerPlugin : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration
    ) {
        FirExtensionRegistrarAdapter.registerExtension(StubFirExtensions)
        IrGenerationExtension.registerExtension(IrStubBodyGenerator)
    }
}

object StubFirExtensions : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::FirExpectTransformer
        +::FirExpectEnumConstructorGenerator
    }
}
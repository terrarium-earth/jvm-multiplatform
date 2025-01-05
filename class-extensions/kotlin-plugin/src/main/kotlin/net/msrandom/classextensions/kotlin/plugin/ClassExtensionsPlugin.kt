package net.msrandom.classextensions.kotlin.plugin

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension

class ClassExtensionsFirExtensionRegistrar(private val extensionFinder: LazyExtensionFinder) : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +FirDeclarationGenerationExtension.Factory {
            ClassExtensionFirDeclarations(it, extensionFinder)
        }

        +FirSupertypeGenerationExtension.Factory {
            ClassExtensionFirSupertypes(it, extensionFinder)
        }
    }
}

@OptIn(ExperimentalCompilerApi::class)
@AutoService(CompilerPluginRegistrar::class)
class ClassExtensionsPlugin : CompilerPluginRegistrar() {
    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val moduleStructure = configuration[CommonConfigurationKeys.HMPP_MODULE_STRUCTURE] ?: return
        val modules = configuration[JVMConfigurationKeys.MODULES] ?: return
        val moduleName = configuration.getNotNull(CommonConfigurationKeys.MODULE_NAME)

        val module = modules.first {
            it.getModuleName() == moduleName
        }

        FirExtensionRegistrarAdapter.registerExtension(ClassExtensionsFirExtensionRegistrar(LazyExtensionFinder(module, moduleStructure)))
        IrGenerationExtension.registerExtension(ExcludeClassExtensionsIrGenerationExtension())
    }
}

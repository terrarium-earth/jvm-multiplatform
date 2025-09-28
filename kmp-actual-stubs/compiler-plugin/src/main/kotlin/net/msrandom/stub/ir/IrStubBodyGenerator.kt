package net.msrandom.stub.ir

import net.msrandom.stub.STUB
import net.msrandom.stub.UNSUPPORTED_OPERATION_EXCEPTION
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.SYNTHETIC_OFFSET
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrVisitor

typealias IsExpectClassMember = Boolean

class IrStubBodyGenerator(val context: IrPluginContext) : IrVisitor<Unit, IsExpectClassMember>() {

    private val symbols: Symbols = Symbols()

    override fun visitElement(element: IrElement, data: IsExpectClassMember) {}

    override fun visitFile(declaration: IrFile, data: IsExpectClassMember) {
        declaration.acceptChildren(this, false)
    }

    override fun visitClass(declaration: IrClass, data: IsExpectClassMember) {
        if (data || declaration.hasAnnotation(STUB)) {
            if (declaration.kind != ClassKind.INTERFACE) {
                if (declaration.constructors.toList().isEmpty()) {
                    declaration.addConstructor().apply {
                        isPrimary = true
                        visibility = if (declaration.kind == ClassKind.OBJECT) {
                            DescriptorVisibilities.PRIVATE
                        } else {
                            DescriptorVisibilities.PUBLIC
                        }

                        body = createErrorBody(declaration)
                    }
                } else {
                    declaration.constructors.forEach {
                        it.body = createErrorBody(declaration)
                    }
                }
            }
            declaration.acceptChildren(this, true)
        }
    }

    override fun visitFunction(declaration: IrFunction, data: IsExpectClassMember) {
        if (data || declaration.hasAnnotation(STUB)) {
            declaration.body = createErrorBody(declaration)
        }
    }

    override fun visitProperty(declaration: IrProperty, data: IsExpectClassMember) {
        if (data || declaration.hasAnnotation(STUB)) {
            declaration.backingField?.initializer = createErrorBody(declaration)
            declaration.getter?.body = createErrorBody(declaration)
            if (declaration.isVar) declaration.setter?.body = createErrorBody(declaration)
        }
    }

    private fun createThrow(declaration: IrDeclaration): IrThrowImpl = IrSingleStatementBuilder(
        context,
        Scope(declaration.symbol),
        SYNTHETIC_OFFSET,
        SYNTHETIC_OFFSET
    ).build {
        irThrow(irCallConstructor(symbols.unsupportedOperationExceptionCtor, emptyList()).apply {
            putValueArgument(0, irString("Common modules are not runnable"))
        })
    }

    private fun createErrorBody(declaration: IrDeclaration): IrExpressionBody {
        return context.irFactory.createExpressionBody(createThrow(declaration))
    }

    // This is fine cs IrGenerationExtensions are only run after fir2ir is complete
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private inner class Symbols {
        val unsupportedOperationException = context.referenceClass(UNSUPPORTED_OPERATION_EXCEPTION)!!
        val unsupportedOperationExceptionCtor = unsupportedOperationException.constructors
            .first { it.owner.valueParameters.size == 1 }
    }

    companion object : IrGenerationExtension {
        override fun generate(
            moduleFragment: IrModuleFragment,
            pluginContext: IrPluginContext
        ) {
            moduleFragment.acceptChildren(IrStubBodyGenerator(pluginContext), false)
        }
    }
}
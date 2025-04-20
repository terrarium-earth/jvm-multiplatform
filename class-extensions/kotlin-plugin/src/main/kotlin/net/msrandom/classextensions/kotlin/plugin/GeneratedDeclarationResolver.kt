package net.msrandom.classextensions.kotlin.plugin

import net.msrandom.classextensions.ExtensionInject
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirFunctionTarget
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.FirAnnotationContainerBuilder
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.builder.*
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertyGetter
import org.jetbrains.kotlin.fir.declarations.impl.FirDefaultPropertySetter
import org.jetbrains.kotlin.fir.declarations.utils.*
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.expressions.builder.buildReturnExpression
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SessionHolderImpl
import org.jetbrains.kotlin.fir.resolve.calls.ImplicitDispatchReceiverValue
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.transformers.FirStatusResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.StatusComputationSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.visitors.FirDefaultTransformer
import org.jetbrains.kotlin.fir.visitors.transformSingle
import org.jetbrains.kotlin.util.PrivateForInline

object GeneratedDeclarationResolver {
    private fun replaceFunctionReturnTarget(original: FirFunction, copy: FirFunction) {
        copy.body?.transformStatements(object : FirDefaultTransformer<Nothing?>() {
            override fun <E : FirElement> transformElement(
                element: E,
                data: Nothing?
            ): E {
                return element
            }

            override fun transformReturnExpression(
                returnExpression: FirReturnExpression,
                data: Nothing?
            ): FirStatement {
                return buildReturnExpression {
                    source = returnExpression.source
                    annotations.addAll(returnExpression.annotations)

                    target = if (returnExpression.target.labeledElement === original) {
                        FirFunctionTarget(returnExpression.target.labelName, (returnExpression.target as? FirFunctionTarget)?.isLambda == true).apply {
                            bind(copy)
                        }
                    } else {
                        returnExpression.target
                    }

                    result = returnExpression.result
                }
            }
        }, null)
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(SymbolInternals::class, UnresolvedExpressionTypeAccess::class, PrivateForInline::class)
    internal fun <T : FirMemberDeclaration> resolve(owner: FirClassSymbol<*>, extension: ClassExtensionContext, element: T, session: FirSession): T {
        val scopeSession = ScopeSession()

        fun FirAnnotationContainerBuilder.removeExtensionInject() {
            annotations.removeIf { ExtensionInject::class.java.simpleName in it.annotationTypeRef.source?.lighterASTNode.toString() }
        }

        // This works because FirGetClassCall and FirTypeOperatorCall are badly typed;
        //  It contains a FirArgumentList that remains unresolved, since it is not a function call and can not have a proper mapping to a value parameter
        //  Thus we cheat by adding a mapping with an unsafe null value that will (hopefully) never be read
        fun <T> uncheckedNullCast() = null as T

        val transformed = element.transformSingle(
            object : FirDefaultTransformer<Nothing?>() {
                override fun <E : FirElement> transformElement(
                    element: E,
                    data: Nothing?
                ): E {
                    return element.transformChildren(this, data) as E
                }

                override fun transformGetClassCall(getClassCall: FirGetClassCall, data: Nothing?): FirStatement {
                    getClassCall.replaceArgumentList(buildResolvedArgumentList(getClassCall.argumentList, LinkedHashMap(mapOf(getClassCall.argument to uncheckedNullCast()))))

                    return super.transformGetClassCall(getClassCall, data)
                }

                override fun transformTypeOperatorCall(
                    typeOperatorCall: FirTypeOperatorCall,
                    data: Nothing?
                ): FirStatement {
                    typeOperatorCall.replaceArgumentList(buildResolvedArgumentList(typeOperatorCall.argumentList, LinkedHashMap(mapOf(typeOperatorCall.argument to uncheckedNullCast()))))

                    return super.transformTypeOperatorCall(typeOperatorCall, data)
                }

                override fun transformAnonymousFunction(
                    anonymousFunction: FirAnonymousFunction,
                    data: Nothing?
                ): FirAnonymousFunction {
                    val copy = buildAnonymousFunction {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirAnonymousFunctionSymbol()

                        source = anonymousFunction.source
                        resolvePhase = anonymousFunction.resolvePhase
                        annotations.addAll(anonymousFunction.annotations)
                        moduleData = anonymousFunction.moduleData
                        attributes = anonymousFunction.attributes
                        status = anonymousFunction.status
                        returnTypeRef = anonymousFunction.returnTypeRef
                        receiverParameter = anonymousFunction.receiverParameter
                        deprecationsProvider = anonymousFunction.deprecationsProvider
                        containerSource = anonymousFunction.containerSource
                        dispatchReceiverType = anonymousFunction.dispatchReceiverType
                        contextReceivers.addAll(anonymousFunction.contextReceivers)
                        controlFlowGraphReference = anonymousFunction.controlFlowGraphReference
                        valueParameters.addAll(anonymousFunction.valueParameters)
                        body = anonymousFunction.body
                        contractDescription = anonymousFunction.contractDescription
                        label = anonymousFunction.label
                        invocationKind = anonymousFunction.invocationKind
                        inlineStatus = anonymousFunction.inlineStatus
                        isLambda = anonymousFunction.isLambda
                        hasExplicitParameterList = anonymousFunction.hasExplicitParameterList
                        typeParameters.addAll(anonymousFunction.typeParameters)
                        typeRef = anonymousFunction.typeRef
                    }

                    replaceFunctionReturnTarget(anonymousFunction, copy)

                    return super.transformAnonymousFunction(copy, data) as FirAnonymousFunction
                }

                override fun transformAnonymousInitializer(
                    anonymousInitializer: FirAnonymousInitializer,
                    data: Nothing?
                ): FirAnonymousInitializer {
                    val copy = buildAnonymousInitializer {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirAnonymousInitializerSymbol()

                        source = anonymousInitializer.source
                        resolvePhase = anonymousInitializer.resolvePhase
                        annotations.addAll(anonymousInitializer.annotations)
                        moduleData = anonymousInitializer.moduleData
                        attributes = anonymousInitializer.attributes
                        body = anonymousInitializer.body
                        containingDeclarationSymbol = anonymousInitializer.containingDeclarationSymbol
                    }
                    return super.transformAnonymousInitializer(copy, data)
                }

                override fun transformAnonymousObject(
                    anonymousObject: FirAnonymousObject,
                    data: Nothing?
                ): FirAnonymousObject {
                    val copy = buildAnonymousObject {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirAnonymousObjectSymbol(anonymousObject.symbol.classId.packageFqName)

                        source = anonymousObject.source
                        resolvePhase = anonymousObject.resolvePhase
                        moduleData = anonymousObject.moduleData
                        attributes = anonymousObject.attributes
                        typeParameters.addAll(anonymousObject.typeParameters)
                        status = anonymousObject.status
                        deprecationsProvider = anonymousObject.deprecationsProvider
                        classKind = anonymousObject.classKind
                        superTypeRefs.addAll(anonymousObject.superTypeRefs)
                        declarations.addAll(anonymousObject.declarations)
                        annotations.addAll(anonymousObject.annotations)
                        scopeProvider = anonymousObject.scopeProvider
                    }
                    return super.transformAnonymousObject(copy, data) as FirAnonymousObject
                }

                override fun transformBackingField(backingField: FirBackingField, data: Nothing?): FirBackingField {
                    val copy = buildBackingField {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirBackingFieldSymbol(backingField.symbol.callableId)

                        source = backingField.source
                        resolvePhase = backingField.resolvePhase
                        moduleData = backingField.moduleData
                        attributes = backingField.attributes
                        returnTypeRef = backingField.returnTypeRef
                        receiverParameter = backingField.receiverParameter
                        deprecationsProvider = backingField.deprecationsProvider
                        containerSource = backingField.containerSource
                        dispatchReceiverType = backingField.dispatchReceiverType
                        contextReceivers.addAll(backingField.contextReceivers)
                        name = backingField.name
                        delegate = backingField.delegate
                        isVar = backingField.isVar
                        isVal = backingField.isVal
                        getter = backingField.getter
                        setter = backingField.setter
                        this.backingField = backingField.backingField
                        propertySymbol = backingField.propertySymbol
                        initializer = backingField.initializer
                        annotations.addAll(backingField.annotations)
                        typeParameters.addAll(backingField.typeParameters)
                        status = backingField.status
                    }
                    return super.transformBackingField(copy, data) as FirBackingField
                }

                override fun transformCodeFragment(codeFragment: FirCodeFragment, data: Nothing?): FirCodeFragment {
                    val copy = buildCodeFragmentCopy(codeFragment) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirCodeFragmentSymbol()
                    }
                    return super.transformCodeFragment(copy, data)
                }

                override fun transformConstructor(constructor: FirConstructor, data: Nothing?): FirConstructor {
                    val copy = buildConstructorCopy(constructor) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirConstructorSymbol(constructor.symbol.callableId)

                        removeExtensionInject()
                    }

                    replaceFunctionReturnTarget(constructor, copy)

                    return super.transformConstructor(copy, data) as FirConstructor
                }

                override fun transformEnumEntry(enumEntry: FirEnumEntry, data: Nothing?): FirEnumEntry {
                    val copy = buildEnumEntryCopy(enumEntry) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirEnumEntrySymbol(enumEntry.symbol.callableId)
                    }
                    return super.transformEnumEntry(copy, data) as FirEnumEntry
                }

                override fun transformField(field: FirField, data: Nothing?): FirField {
                    val copy = buildFieldCopy(field) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirFieldSymbol(field.symbol.callableId)
                    }
                    return super.transformField(copy, data) as FirField
                }

                override fun transformProperty(property: FirProperty, data: Nothing?): FirProperty {
                    val copy = buildPropertyCopy(property) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirPropertySymbol(property.symbol.callableId)

                        removeExtensionInject()
                    }

                    return super.transformProperty(copy, data) as FirProperty
                }

                override fun transformPropertyAccessor(
                    propertyAccessor: FirPropertyAccessor,
                    data: Nothing?
                ): FirPropertyAccessor {
                    val copy = if (propertyAccessor is FirDefaultPropertyAccessor) {
                        if (propertyAccessor.isGetter) {
                            FirDefaultPropertyGetter(
                                propertyAccessor.source,
                                propertyAccessor.moduleData,
                                ClassExtensionFirDeclarations.Key.origin,
                                propertyAccessor.returnTypeRef,
                                propertyAccessor.visibility,
                                propertyAccessor.propertySymbol,
                                propertyAccessor.modality ?: Modality.FINAL,
                                propertyAccessor.effectiveVisibility,
                                propertyAccessor.isInline,
                                propertyAccessor.isOverride,
                                FirPropertyAccessorSymbol(),
                                propertyAccessor.resolvePhase,
                                propertyAccessor.attributes,
                            )
                        } else {
                            FirDefaultPropertySetter(
                                propertyAccessor.source,
                                propertyAccessor.moduleData,
                                ClassExtensionFirDeclarations.Key.origin,
                                propertyAccessor.returnTypeRef,
                                propertyAccessor.visibility,
                                propertyAccessor.propertySymbol,
                                propertyAccessor.modality ?: Modality.FINAL,
                                propertyAccessor.effectiveVisibility,
                                propertyAccessor.isInline,
                                propertyAccessor.isOverride,
                                FirPropertyAccessorSymbol(),
                                propertyAccessor.valueParameters[0].source,
                                propertyAccessor.valueParameters[0].annotations,
                                propertyAccessor.resolvePhase,
                                propertyAccessor.attributes,
                            )
                        }
                    } else {
                        buildPropertyAccessorCopy(propertyAccessor) {
                            origin = ClassExtensionFirDeclarations.Key.origin
                            symbol = FirPropertyAccessorSymbol()
                        }
                    }

                    replaceFunctionReturnTarget(propertyAccessor, copy)

                    return super.transformPropertyAccessor(copy, data) as FirPropertyAccessor
                }

                override fun transformRegularClass(regularClass: FirRegularClass, data: Nothing?): FirRegularClass {
                    val copy = buildRegularClassCopy(regularClass) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirRegularClassSymbol(regularClass.symbol.classId)

                        removeExtensionInject()
                    }

                    return super.transformRegularClass(copy, data) as FirRegularClass
                }

                override fun transformSimpleFunction(simpleFunction: FirSimpleFunction, data: Nothing?): FirSimpleFunction {
                    val copy = buildSimpleFunctionCopy(simpleFunction) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirNamedFunctionSymbol(simpleFunction.symbol.callableId)

                        removeExtensionInject()
                    }

                    replaceFunctionReturnTarget(simpleFunction, copy)

                    return super.transformSimpleFunction(copy, data) as FirSimpleFunction
                }

                override fun transformTypeAlias(typeAlias: FirTypeAlias, data: Nothing?): FirTypeAlias {
                    val copy = buildTypeAliasCopy(typeAlias) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirTypeAliasSymbol(typeAlias.symbol.classId)
                    }
                    return super.transformTypeAlias(copy, data) as FirTypeAlias
                }

                override fun transformTypeParameter(typeParameter: FirTypeParameter, data: Nothing?): FirTypeParameter {
                    val copy = buildTypeParameterCopy(typeParameter) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirTypeParameterSymbol()
                    }
                    return super.transformTypeParameter(copy, data) as FirTypeParameter
                }

                override fun transformValueParameter(valueParameter: FirValueParameter, data: Nothing?): FirValueParameter {
                    val copy = buildValueParameterCopy(valueParameter) {
                        origin = ClassExtensionFirDeclarations.Key.origin
                        symbol = FirValueParameterSymbol(valueParameter.symbol.name)
                    }
                    return super.transformValueParameter(copy, data) as FirValueParameter
                }
            },
            null
        )

        transformed.transformSingle(
            FirStatusResolveTransformer(session, scopeSession, StatusComputationSession()),
            null
        )

        val transformer = FirBodyResolveTransformer(session, FirResolvePhase.BODY_RESOLVE, false, scopeSession)

        transformer.context.withFile(
            session.firProvider.getFirClassifierContainerFile(owner),
            SessionHolderImpl(session, scopeSession)
        ) {
            transformer.context.fileImportsScope += extension.scope.scopes
            transformer.context.addNonLocalTowerDataElements(extension.scope.scopes.map { it.asTowerDataElement(isLocal = false) })
            transformer.context.addReceiver(null, ImplicitDispatchReceiverValue(owner, session, scopeSession))

            transformer.context.withContainer(owner.fir) {
                transformer.context.withContainingClass(owner.fir) {
                    transformed.transformSingle(transformer, ResolutionMode.ContextIndependent)
                }
            }
        }

        return transformed
    }
}

package net.msrandom.stub.fir

import net.msrandom.stub.STUB
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.jetbrains.kotlin.fakeElement
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.getModifierList
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.expressions.builder.buildVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.extensions.transform
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.createArrayType
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.types.ConstantValueKind

class FirExpectTransformer(session: FirSession) : FirStatusTransformerExtension(session) {

    override fun needTransformStatus(declaration: FirDeclaration): Boolean {
        return declaration.source.getModifierList()?.let { KtTokens.EXPECT_KEYWORD in it } ?: false
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        function: FirSimpleFunction,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean
    ): FirDeclarationStatus {
        addStubAnnotation(function)
        addSuppressions(function,
            FirErrors.NON_MEMBER_FUNCTION_NO_BODY,
            FirErrors.NON_ABSTRACT_FUNCTION_WITH_NO_BODY
        )
        return status.transform { isExpect = false }
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        property: FirProperty,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean
    ): FirDeclarationStatus {
        addStubAnnotation(property)
        addSuppressions(property,
            FirErrors.MUST_BE_INITIALIZED,
            FirErrors.EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT,
        )
        return status.transform { isExpect = false }
    }

    override fun transformStatus(
        status: FirDeclarationStatus,
        regularClass: FirRegularClass,
        containingClass: FirClassLikeSymbol<*>?,
        isLocal: Boolean
    ): FirDeclarationStatus {
        addStubAnnotation(regularClass)

        regularClass.transformDeclarations(object : FirTransformer<Nothing?>() {
            override fun <E : FirElement> transformElement(
                element: E,
                data: Nothing?
            ): E = element

            override fun transformDeclaration(declaration: FirDeclaration, data: Nothing?): FirDeclaration {
                addSuppressions(declaration,
                    FirErrors.NON_ABSTRACT_FUNCTION_WITH_NO_BODY,
                    FirErrors.MUST_BE_INITIALIZED,
                    FirErrors.EXTENSION_PROPERTY_MUST_HAVE_ACCESSORS_OR_BE_ABSTRACT,
                )

                return super.transformDeclaration(declaration, data)
            }
        }, null)

        return status.transform { isExpect = false }
    }

    private fun addStubAnnotation(declaration: FirDeclaration) {
        if (declaration.getAnnotationByClassId(STUB, session) == null) {
            declaration.replaceAnnotations(declaration.annotations + buildAnnotation {
                annotationTypeRef = STUB.createConeType(session).toFirResolvedTypeRef()
                argumentMapping = FirEmptyAnnotationArgumentMapping
                source = declaration.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
            })
        }
    }

    private fun addSuppressions(declaration: FirDeclaration, vararg diagnostics: AbstractKtDiagnosticFactory) {
        val existingSuppress = declaration.getAnnotationByClassId(StandardClassIds.Annotations.Suppress, session)
        val existingSuppressions = existingSuppress?.getStringArrayArgument(StandardClassIds.Annotations.ParameterNames.suppressNames, session)
        val newSuppress = buildAnnotation {
            annotationTypeRef = StandardClassIds.Annotations.Suppress.createConeType(session).toFirResolvedTypeRef()
            argumentMapping = buildAnnotationArgumentMapping {
                source = declaration.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
                mapping[StandardClassIds.Annotations.ParameterNames.suppressNames] = buildVarargArgumentsExpression {
                    coneTypeOrNull = session.builtinTypes.stringType.coneType.createArrayType()
                    coneElementTypeOrNull = session.builtinTypes.stringType.coneType
                    source = declaration.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)

                    for (factory in diagnostics) {
                        arguments += buildLiteralExpression(
                            declaration.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated),
                            ConstantValueKind.String,
                            factory.name,
                            setType = true
                        )
                    }

                    existingSuppressions?.forEach {
                        arguments += buildLiteralExpression(
                            declaration.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated),
                            ConstantValueKind.String,
                            it, setType = true
                        )
                    }
                }
            }
            source = declaration.source?.fakeElement(KtFakeSourceElementKind.PluginGenerated)
        }

        val mutable = declaration.annotations.toMutableList()
        existingSuppress?.let(mutable::remove)
        mutable.add(newSuppress)

        declaration.replaceAnnotations(mutable)
    }

}
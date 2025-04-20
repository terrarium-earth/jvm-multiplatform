package net.msrandom.classextensions.kotlin.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.lightTree.converter.LightTreeRawFirDeclarationBuilder
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef

class ClassExtensionFirSupertypes(session: FirSession, extensionFinder: LazyExtensionFinder) :
    FirSupertypeGenerationExtension(session) {
    private val resolver = FirClassExtensionResolver(session, extensionFinder)

    override fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService,
    ): List<ConeKotlinType> {
        val symbol = classLikeDeclaration.symbol

        if (symbol !is FirClassSymbol<*>) {
            return emptyList()
        }

        val newSuperTypes = resolver.getExtensions(symbol).flatMap { extension ->
            extension.info.superList.map {
                extension to it
            }
        }.toList()

        return newSuperTypes.mapNotNull { (extension, node) ->
            val builder = LightTreeRawFirDeclarationBuilder(session, session.kotlinScopeProvider, extension.info.treeStructure)

            resolver.resolveUserType(extension.scope, builder.convertType(node) as FirUserTypeRef)
                .takeUnless { it.coneType in resolvedSupertypes.map { it.coneType } }?.coneType
        }
    }

    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
        return declaration.symbol is FirClassSymbol<*> && resolver.getExtensions(declaration.symbol as FirClassSymbol<*>)
            .any { extension -> extension.info.superList.isNotEmpty() }
    }
}

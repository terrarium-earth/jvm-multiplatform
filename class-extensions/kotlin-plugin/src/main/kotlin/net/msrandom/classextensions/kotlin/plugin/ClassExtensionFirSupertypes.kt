package net.msrandom.classextensions.kotlin.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.name.Name

class ClassExtensionFirSupertypes(session: FirSession, extensionFinder: LazyExtensionFinder) :
    FirSupertypeGenerationExtension(session) {
    private val resolver = FirClassExtensionResolver(session, extensionFinder)

    override fun computeAdditionalSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>,
        typeResolver: TypeResolveService
    ): List<ConeKotlinType> {
        val symbol = classLikeDeclaration.symbol

        if (symbol !is FirClassSymbol<*>) {
            return emptyList()
        }

        val newSuperTypes = resolver.getExtensions(symbol).flatMap { it.superList }.toList()

        return newSuperTypes.mapNotNull {
            val type = typeResolver.resolveUserType(buildUserTypeFromQualifierParts(false) {
                part(Name.identifier(it))
            })

            type.takeUnless { it.coneType in resolvedSupertypes.map { it.coneType } }?.coneType
        }
    }

    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration): Boolean {
        return declaration.symbol is FirClassSymbol<*> && resolver.getExtensions(declaration.symbol as FirClassSymbol<*>)
            .any { it.superList.isNotEmpty() }
    }
}

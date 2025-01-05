package net.msrandom.classextensions.kotlin.plugin

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.buildUserTypeFromQualifierParts
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.transformers.FirSpecificTypeResolverTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.ScopeClassDeclaration
import org.jetbrains.kotlin.fir.scopes.createImportingScopes
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class FirClassExtensionResolver(
    private val session: FirSession,
    private val extensionFinder: LazyExtensionFinder,
) {
    private val classExtensions by lazy {
        extensionFinder.getAllExtensionClasses()
    }

    fun getExtensions(expected: FirClassSymbol<*>) = classExtensions.asSequence().mapNotNull {
        val expectedType = resolveType(expected, buildUserTypeFromQualifierParts(false) {
            part(Name.identifier(it.targetTypeName))
        })

        if (expectedType != null && expectedType.coneType.classId == expected.classId) {
            it
        } else {
            null
        }
    }

    @OptIn(SymbolInternals::class)
    internal fun resolveType(currentClass: FirClassLikeSymbol<*>, type: FirUserTypeRef): FirResolvedTypeRef? {
        val file = session.firProvider.getFirClassifierContainerFileIfAny(currentClass) ?: return null
        val scopes = createImportingScopes(file, session, ScopeSession())
        val firSpecificTypeResolverTransformer = FirSpecificTypeResolverTransformer(session, expandTypeAliases = true)

        return firSpecificTypeResolverTransformer.transformTypeRef(
            type,
            ScopeClassDeclaration(scopes, listOf(currentClass.fir)),
        )
    }

    companion object {
        internal fun classId(cls: Class<*>) = FqName(cls.name).let {
            ClassId(it.parent(), it.shortName())
        }
    }
}

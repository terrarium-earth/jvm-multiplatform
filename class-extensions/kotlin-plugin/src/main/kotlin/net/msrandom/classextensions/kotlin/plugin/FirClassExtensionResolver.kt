package net.msrandom.classextensions.kotlin.plugin

import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtNodeTypes.*
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirPackageDirective
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirImport
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.asTowerDataElement
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.lightTree.converter.LightTreeRawFirDeclarationBuilder
import org.jetbrains.kotlin.fir.lightTree.converter.LightTreeRawFirExpressionBuilder
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SessionHolderImpl
import org.jetbrains.kotlin.fir.resolve.SupertypeSupplier
import org.jetbrains.kotlin.fir.resolve.calls.FirCallResolver
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.transformers.FirImportResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.FirSpecificTypeResolverTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.ScopeClassDeclaration
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformer.BodyResolveTransformerComponents
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolve.typeResolver
import org.jetbrains.kotlin.fir.scopes.*
import org.jetbrains.kotlin.fir.scopes.impl.*
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.impl.FirQualifierPartImpl
import org.jetbrains.kotlin.fir.types.impl.FirTypeArgumentListImpl
import org.jetbrains.kotlin.fir.visitors.transformSingle
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.util.PrivateForInline
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.appendLines
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.io.path.notExists

internal fun computeImportingScopes(
    session: FirSession,
    packageDirective: FirPackageDirective,
    imports: List<FirImport>
): List<FirScope> {
    val scopeSession = ScopeSession()

    val excludedImportNames =
        imports.filter { it.aliasName != null }.mapNotNullTo(hashSetOf()) { it.importedFqName }
            .ifEmpty { emptySet() }

    val excludedNamesInPackage =
        excludedImportNames.mapNotNullTo(mutableSetOf()) {
            if (it.parent() == packageDirective.packageFqName) it.shortName() else null
        }

    return buildList {
        this += FirDefaultStarImportingScope(
            FirSingleLevelDefaultStarImportingScope(
                session,
                scopeSession,
                DefaultImportPriority.HIGH,
                excludedImportNames
            ),

            FirSingleLevelDefaultStarImportingScope(
                session,
                scopeSession,
                DefaultImportPriority.LOW,
                excludedImportNames
            ),
        )

        this += FirExplicitStarImportingScope(imports, session, scopeSession, excludedImportNames)

        this += FirDefaultSimpleImportingScope(
            session,
            scopeSession,
            priority = DefaultImportPriority.LOW,
            excludedImportNames
        )

        this += FirDefaultSimpleImportingScope(
            session,
            scopeSession,
            priority = DefaultImportPriority.HIGH,
            excludedImportNames
        )

        this += when {
            excludedNamesInPackage.isEmpty() ->
                // Supposed to be the most common branch, so we cache it
                scopeSession.getOrBuild(packageDirective.packageFqName to session, PACKAGE_MEMBER) {
                    FirPackageMemberScope(packageDirective.packageFqName, session, excludedNames = emptySet())
                }

            else ->
                FirPackageMemberScope(packageDirective.packageFqName, session, excludedNames = excludedNamesInPackage)
        }

        // TODO: explicit simple importing scope should have highest priority (higher than inner scopes added in process)
        this += FirExplicitSimpleImportingScope(imports, session, scopeSession)
    }
}

internal fun <T : FirElement> convertAst(
    node: LighterASTNode,
    builder: LightTreeRawFirDeclarationBuilder,
    convertor: String
): T {
    @Suppress("UNCHECKED_CAST")
    val method = LightTreeRawFirDeclarationBuilder::class.java.declaredMethods.first { it.name == convertor }

    method.isAccessible = true

    @Suppress("UNCHECKED_CAST")
    return method.invoke(builder, node) as T
}

internal class ClassExtensionContext(val info: ClassExtensionInfo, val scope: ScopeClassDeclaration)

internal class FirClassExtensionResolver(
    private val session: FirSession,
    private val extensionFinder: LazyExtensionFinder,
) {
    private val classExtensions by lazy {
        extensionFinder.getAllExtensionClasses()
    }

    @OptIn(SymbolInternals::class, PrivateForInline::class)
    fun getExtensions(expected: FirClassSymbol<*>) = classExtensions.asSequence().mapNotNull { extension ->
        val builder =
            LightTreeRawFirDeclarationBuilder(session, session.kotlinScopeProvider, extension.treeStructure)

        val getClassCall = LightTreeRawFirExpressionBuilder(
            session,
            extension.treeStructure,
            builder
        ).convertExpression(extension.targetTypeName, "") as FirGetClassCall

        val qualifiers = buildList {
            var propertyAccess: FirPropertyAccessExpression? = getClassCall.argument as FirPropertyAccessExpression

            while (propertyAccess != null) {
                add(propertyAccess.calleeReference as FirSimpleNamedReference)

                propertyAccess = propertyAccess.explicitReceiver as FirPropertyAccessExpression?
            }
        }.reversed()

        val typeRef = buildUserTypeRef {
            source = getClassCall.argument.source

            isMarkedNullable = false

            qualifier.addAll(qualifiers.map {
                FirQualifierPartImpl(it.source, it.name, FirTypeArgumentListImpl(null))
            })
        }

        val importResolveTransformer = FirImportResolveTransformer(session)

        val firPackage = extension.packageDirective?.let { convertAst(it, builder, "convertPackageDirective") }
            ?: buildPackageDirective {
                packageFqName = FqName.ROOT
            }

        val imports = extension.imports.map {
            convertAst<FirImport>(it, builder, "convertImportDirective").transformSingle(importResolveTransformer, null)
        }

        val scope = ScopeClassDeclaration(computeImportingScopes(session, firPackage, imports), emptyList())

        val typeResolution = session.typeResolver.resolveType(
            typeRef,
            scope,
            true,
            false,
            true,
            null,
            SupertypeSupplier.Default,
        )

        if (typeResolution.type.classId == expected.classId) {
            ClassExtensionContext(extension, scope)
        } else {
            null
        }
    }

    internal fun resolveUserType(scope: ScopeClassDeclaration, userType: FirUserTypeRef): FirResolvedTypeRef {
        val firSpecificTypeResolverTransformer = FirSpecificTypeResolverTransformer(session, expandTypeAliases = true)

        return userType.transform(firSpecificTypeResolverTransformer, scope)
    }
}

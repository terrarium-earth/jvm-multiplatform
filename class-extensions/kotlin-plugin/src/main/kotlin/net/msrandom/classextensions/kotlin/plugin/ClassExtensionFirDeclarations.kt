package net.msrandom.classextensions.kotlin.plugin

import net.msrandom.classextensions.ExtensionInject
import net.msrandom.classextensions.kotlin.plugin.FirClassExtensionResolver.Companion.classId
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.Context
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.lightTree.converter.LightTreeRawFirDeclarationBuilder
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.SessionHolderImpl
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.transformers.FirStatusResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.FirTypeResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.StatusComputationSession
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.visitors.transformSingle
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

class ClassExtensionFirDeclarations(session: FirSession, extensionFinder: LazyExtensionFinder) :
    FirDeclarationGenerationExtension(session) {
    private val resolver = FirClassExtensionResolver(session, extensionFinder)
    private val compiledExtensions = hashMapOf<ClassId, CompiledExtensionInfo>()

/*    @OptIn(AdapterForResolveProcessor::class)
    private fun resolveCallableInfo(element: FirCallableDeclaration) {
        element.transformSingle(statusResolveTransformer, null)

        // FirBodyResolveTransformer ->
        // FirDeclarationsResolveTransformer ->
        // FirAbstractBodyResolveTransformerDispatcher ->
        // FirExpressionsResolveTransformer ->
        // FirCallCompleter ->
        // FirCallCompletionResultsWriterTransformer
        element.transformChildren(object : FirTransformer<Unit>() {
            override fun <E : FirElement> transformElement(element: E, data: Unit): E {
                return element.transformChildren(this, data) as E
            }

            override fun transformFunctionCall(functionCall: FirFunctionCall, data: Unit): FirStatement {
                return bodyResolveTransformer.transformFunctionCall(functionCall, ResolutionMode.ContextIndependent)
            }

            *//*            override fun transformArgumentList(argumentList: FirArgumentList, data: Unit): FirArgumentList {
                            return bodyResolveTransformer.transformArgumentList(argumentList, ResolutionMode.ContextIndependent)
                        }*//*

            override fun transformNamedReference(namedReference: FirNamedReference, data: Unit): FirReference {
                return bodyResolveTransformer.transformNamedReference(namedReference, ResolutionMode.ContextIndependent)
            }
        }, Unit)
    }*/

    @OptIn(SymbolInternals::class)
    private fun compiledExtensions(owner: FirClassSymbol<*>) = compiledExtensions.computeIfAbsent(owner.classId) {
        val extensions = resolver.getExtensions(owner)

        val firBuilderContext = Context<LighterASTNode>().apply {
            packageFqName = owner.classId.packageFqName
            className = owner.classId.asSingleFqName()
        }

        val functions = mutableListOf<FirSimpleFunction>()
        val properties = mutableListOf<FirProperty>()
        val constructors = mutableListOf<FirConstructor>()
        val nestedClassifiers = mutableListOf<FirRegularClass>()

        for (info in extensions) {
            val builder = LightTreeRawFirDeclarationBuilder(
                session,
                session.kotlinScopeProvider,
                info.treeStructure,
                firBuilderContext
            )

            for (function in info.functions) {
                val firFunction = builder.convertFunctionDeclaration(function) as FirSimpleFunction

                functions.add(firFunction)
            }

            for (property in info.properties) {
                val firProperty = builder.convertPropertyDeclaration(property) as FirProperty

                properties.add(firProperty)
            }
        }

        CompiledExtensionInfo(
            functions,
            properties,
            constructors,
            nestedClassifiers,
        )
    }

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: NestedClassGenerationContext
    ): FirClassLikeSymbol<*>? {
        return super.generateNestedClassLikeDeclaration(owner, name, context)
    }

    @OptIn(SymbolInternals::class)
    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirNamedFunctionSymbol> {
        val compiled = compiledExtensions(context!!.owner)

        val scopeSession = ScopeSession()

        return compiled.functions.mapNotNull {
            if (it.name == callableId.callableName) {
                it.transformSingle(FirStatusResolveTransformer(session, scopeSession, StatusComputationSession()), null)
                val transformer = FirBodyResolveTransformer(session, FirResolvePhase.BODY_RESOLVE, false, scopeSession)

                transformer.context.withFile(session.firProvider.getFirClassifierContainerFile(context.owner), SessionHolderImpl(session, scopeSession)) {
                    transformer.context.withContainingClass(context.owner.fir) {
                        it.transformSingle(transformer, ResolutionMode.ContextDependent)
                    }
                }

                it.symbol
            } else {
                null
            }
        }
    }

    @OptIn(SymbolInternals::class)
    override fun generateProperties(
        callableId: CallableId,
        context: MemberGenerationContext?,
    ): List<FirPropertySymbol> {
        val compiled = compiledExtensions(context!!.owner)

        val scopeSession = ScopeSession()

        return compiled.properties.mapNotNull {
            if (it.name == callableId.callableName) {
                it.transformSingle(FirStatusResolveTransformer(session, scopeSession, StatusComputationSession()), null)
                val transformer = FirBodyResolveTransformer(session, FirResolvePhase.BODY_RESOLVE, false, scopeSession)

                transformer.context.withFile(session.firProvider.getFirClassifierContainerFile(context.owner), SessionHolderImpl(session, scopeSession)) {
                    transformer.context.withContainingClass(context.owner.fir) {
                        it.transformSingle(transformer, ResolutionMode.ContextDependent)
                    }
                }

                it.symbol
            } else {
                null
            }
        }
    }

    @OptIn(SymbolInternals::class)
    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> {
        return getCallables(context.owner)
            .filterIsInstance<FirConstructorSymbol>()
            .map {
                SymbolCopyProvider.copyIfNeeded(it, context.owner)
            }
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        val compiledExtensions = compiledExtensions(context.owner)

        val names =
            compiledExtensions.functions.map { it.name } + compiledExtensions.properties.map { it.name } + listOfNotNull(
                SpecialNames.INIT.takeIf { compiledExtensions.constructors.isNotEmpty() })

        return names.toSet()
    }

    override fun getNestedClassifiersNames(
        classSymbol: FirClassSymbol<*>,
        context: NestedClassGenerationContext
    ): Set<Name> {
        return super.getNestedClassifiersNames(classSymbol, context)
    }

    private fun isInject(extension: FirClassSymbol<*>, member: FirCallableSymbol<*>?) = member
        ?.annotations
        ?.any { annotation ->
            val type = annotation.annotationTypeRef

            val resolvedType = if (type is FirUserTypeRef) {
                resolver.resolveType(extension, type)
            } else {
                type
            }

            return resolvedType?.coneType?.classId == extensionInject
        } == true

    private fun getCallables(classSymbol: FirClassSymbol<*>) = emptyList<FirCallableSymbol<*>>()/*resolver.getExtensions(classSymbol)
        .flatMap { extension ->
            extension.declarationSymbols.mapNotNull {
                if (it !is FirCallableSymbol) {
                    return@mapNotNull null
                }

                if (isInject(extension, it)) {
                    return@mapNotNull it
                }

                if (it !is FirPropertySymbol) {
                    return@mapNotNull null
                }

                if (isInject(extension, it.getterSymbol) || isInject(extension, it.setterSymbol) || isInject(extension, it.backingFieldSymbol) || isInject(extension, it.delegateFieldSymbol)) {
                    return@mapNotNull it
                }

                return@mapNotNull null
            }
        }
        .toList()*/

    private companion object {
        private val extensionInject = classId(ExtensionInject::class.java)
    }

    internal object Key : GeneratedDeclarationKey()
}

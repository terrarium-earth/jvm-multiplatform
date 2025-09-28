package net.msrandom.stub.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.getModifierList
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createConstructor
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name

class FirExpectEnumConstructorGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        if ((classSymbol.classKind == ClassKind.ENUM_CLASS || classSymbol.classKind == ClassKind.ENUM_ENTRY)
            && classSymbol.source.getModifierList()?.contains(KtTokens.EXPECT_KEYWORD) == true) {
            return setOf(Name.special("<init>"))
        }

        return emptySet()
    }

    override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> =
        listOf(createConstructor(context.owner, Key, true).symbol)

    private object Key : GeneratedDeclarationKey()
}
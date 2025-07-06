package net.msrandom.stubs.signature

import org.junit.Test
import kotlin.test.assertEquals

class ParsingTest {
    @Test
    fun `Test Signature Parsing`() {
        fun t(name: String): TypeSignature = TypeSignature.Reference.TypeVariable(name)

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "java/lang/Class",
                    listOf(
                        TypeArgument.Bounded(
                            t("T"),
                            TypeArgument.Bounded.Variance.Invariant,
                        ),
                    )
                ),
            ),
            parseTypeSignature("Ljava/lang/Class<TT;>;"),
        )

        val genericProperty = TypeArgument.Bounded(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "net/minecraft/world/level/block/state/properties/Property",
                    listOf(TypeArgument.Unbounded)
                ),
            ),
            TypeArgument.Bounded.Variance.Invariant
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "java/util/function/Function",
                    listOf(
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment(
                                    "java/util/Map\$Entry",
                                    listOf(
                                        genericProperty,
                                        TypeArgument.Bounded(
                                            TypeSignature.Reference.Class(
                                                ClassNameSegment(
                                                    "java/lang/Comparable",
                                                    listOf(TypeArgument.Unbounded)
                                                ),
                                            ),
                                            TypeArgument.Bounded.Variance.Invariant
                                        ),
                                    )
                                ),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        ),
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment("java/lang/String", emptyList()),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        )
                    )
                ),
            ),
            parseTypeSignature(
                "Ljava/util/function/Function<Ljava/util/Map\$Entry<Lnet/minecraft/world/level/block/state/properties/Property<*>;Ljava/lang/Comparable<*>;>;Ljava/lang/String;>;"
            )
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/google/common/collect/ImmutableMap",
                    listOf(
                        genericProperty,
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment(
                                    "java/lang/Comparable",
                                    listOf(TypeArgument.Unbounded)
                                ),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        )
                    )
                ),
            ),
            parseTypeSignature(
                "Lcom/google/common/collect/ImmutableMap<Lnet/minecraft/world/level/block/state/properties/Property<*>;Ljava/lang/Comparable<*>;>;"
            )
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/google/common/collect/Table",
                    listOf(
                        genericProperty,
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment(
                                    "java/lang/Comparable",
                                    listOf(TypeArgument.Unbounded)
                                ),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        ),
                        TypeArgument.Bounded(t("S"), TypeArgument.Bounded.Variance.Invariant)
                    )
                ),
            ),
            parseTypeSignature(
                "Lcom/google/common/collect/Table<Lnet/minecraft/world/level/block/state/properties/Property<*>;Ljava/lang/Comparable<*>;TS;>;"
            )
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/google/common/collect/ImmutableSortedMap",
                    listOf(
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(ClassNameSegment("java/lang/String", emptyList()), emptyList()),
                            TypeArgument.Bounded.Variance.Invariant
                        ),
                        genericProperty,
                    )
                ),
            ),
            parseTypeSignature(
                "Lcom/google/common/collect/ImmutableSortedMap<Ljava/lang/String;Lnet/minecraft/world/level/block/state/properties/Property<*>;>;"
            )
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/google/common/collect/ImmutableList",
                    listOf(TypeArgument.Bounded(t("S"), TypeArgument.Bounded.Variance.Invariant))
                ),
            ),
            parseTypeSignature("Lcom/google/common/collect/ImmutableList<TS;>;")
        )

        assertEquals(
            t("O"),
            parseTypeSignature("TO;")
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/google/common/collect/ImmutableSortedMap",
                    listOf(
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(ClassNameSegment("java/lang/String", emptyList()), emptyList()),
                            TypeArgument.Bounded.Variance.Invariant
                        ),
                        genericProperty
                    )
                ),
            ),
            parseTypeSignature(
                "Lcom/google/common/collect/ImmutableSortedMap<Ljava/lang/String;Lnet/minecraft/world/level/block/state/properties/Property<*>;>;"
            )
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/google/common/collect/ImmutableList",
                    listOf(TypeArgument.Bounded(t("S"), TypeArgument.Bounded.Variance.Invariant))
                ),
            ),
            parseTypeSignature("Lcom/google/common/collect/ImmutableList<TS;>;")
        )

        assertEquals(
            TypeSignature.Reference.Array(
                TypeSignature.Reference.Array(
                    TypeSignature.Reference.Array(
                        TypeSignature.Reference.Class(
                            ClassNameSegment(
                                "java/util/function/Predicate",
                                listOf(TypeArgument.Bounded(TypeSignature.Reference.Class(ClassNameSegment("net/minecraft/world/level/block/state/pattern/BlockInWorld", emptyList()), emptyList()), TypeArgument.Bounded.Variance.Invariant))
                            ),
                        )
                    )
                )
            ),
            parseTypeSignature(
                "[[[Ljava/util/function/Predicate<Lnet/minecraft/world/level/block/state/pattern/BlockInWorld;>;"
            )
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "java/util/List",
                    listOf(
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment(
                                    "net/minecraft/world/level/block/entity/BeehiveBlockEntity\$BeeData",
                                ),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        )
                    )
                ),
            ),
            parseTypeSignature("Ljava/util/List<Lnet/minecraft/world/level/block/entity/BeehiveBlockEntity\$BeeData;>;")
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "java/util/Set",
                    listOf(
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment("net/minecraft/world/level/block/Block", emptyList()),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        )
                    )
                ),
            ),
            parseTypeSignature("Ljava/util/Set<Lnet/minecraft/world/level/block/Block;>;")
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "com/mojang/datafixers/types/Type",
                    listOf(TypeArgument.Unbounded)
                ),
            ),
            parseTypeSignature("Lcom/mojang/datafixers/types/Type<*>;")
        )

        assertEquals(
            TypeSignature.Reference.Class(
                ClassNameSegment(
                    "java/util/List",
                    listOf(
                        TypeArgument.Bounded(
                            TypeSignature.Reference.Class(
                                ClassNameSegment(
                                    "com/mojang/datafixers/util/Pair",
                                    listOf(
                                        TypeArgument.Bounded(
                                            TypeSignature.Reference.Class(
                                                ClassNameSegment(
                                                    "net/minecraft/world/level/block/entity/BannerPattern",
                                                ),
                                            ),
                                            TypeArgument.Bounded.Variance.Invariant
                                        ),
                                        TypeArgument.Bounded(
                                            TypeSignature.Reference.Class(
                                                ClassNameSegment("net/minecraft/world/item/DyeColor", emptyList()),
                                            ),
                                            TypeArgument.Bounded.Variance.Invariant
                                        )
                                    )
                                ),
                            ),
                            TypeArgument.Bounded.Variance.Invariant
                        )
                    )
                ),
            ),

            parseTypeSignature(
                "Ljava/util/List<Lcom/mojang/datafixers/util/Pair<Lnet/minecraft/world/level/block/entity/BannerPattern;Lnet/minecraft/world/item/DyeColor;>;>;"
            )
        )
    }
}

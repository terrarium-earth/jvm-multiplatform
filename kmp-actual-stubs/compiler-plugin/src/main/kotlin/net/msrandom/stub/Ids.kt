package net.msrandom.stub

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

val STUB: ClassId = ClassId.topLevel(FqName("net.msrandom.stub.Stub"))

val UNSUPPORTED_OPERATION_EXCEPTION = ClassId.topLevel(FqName("java.lang.UnsupportedOperationException"))

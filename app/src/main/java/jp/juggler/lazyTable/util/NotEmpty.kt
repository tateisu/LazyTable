package jp.juggler.lazyTable.util

// ====================================================
// takeIf{...} で頻出するパターンのショートハンド

fun <T : CharSequence> T?.notEmpty() = if (this == null || isEmpty()) null else this
fun <T : CharSequence> T?.notBlank() = if (this == null || isBlank()) null else this

fun Int?.notZero() = if (this == null || this == 0) null else this
fun Long?.notZero() = if (this == null || this == 0L) null else this
fun Short?.notZero() = if (this == null || this == 0.toShort()) null else this
fun Byte?.notZero() = if (this == null || this == 0.toByte()) null else this

fun Char?.notNul() = if (this == null || this == '\u0000') null else this

fun Collection<*>?.notEmpty() = if (this == null || isEmpty()) null else this
fun Array<*>?.notEmpty() = if (this == null || isEmpty()) null else this
fun Map<*, *>?.notEmpty() = if (this == null || isEmpty()) null else this

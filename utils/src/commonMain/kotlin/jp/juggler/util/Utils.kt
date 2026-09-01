package jp.juggler.util

import kotlin.time.Clock
import kotlin.time.Instant

fun <T : CharSequence> T?.notEmpty() = if (isNullOrEmpty()) null else this
fun <T : CharSequence> T?.notBlank() = if (isNullOrBlank()) null else this
fun String.truthy() = !falsy()
fun String.falsy(): Boolean = when (lowercase()) {
    "", "0", "no", "false", "off" -> true
    else -> false
}

fun getMachineTime(): Instant = Clock.System.now()

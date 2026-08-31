package jp.juggler.util

fun <T : CharSequence> T?.notEmpty() = if (isNullOrEmpty()) null else this
fun <T : CharSequence> T?.notBlank() = if (isNullOrBlank()) null else this
fun String.truthy() = !falsy()
fun String.falsy(): Boolean = when (lowercase()) {
    "", "0", "no", "false", "off" -> true
    else -> false
}

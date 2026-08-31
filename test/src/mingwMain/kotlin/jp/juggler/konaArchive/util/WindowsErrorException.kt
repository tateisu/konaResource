@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaArchive.util

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.windows.FORMAT_MESSAGE_FROM_SYSTEM
import platform.windows.FORMAT_MESSAGE_IGNORE_INSERTS
import platform.windows.FormatMessageA
import platform.windows.GetLastError

class WindowsErrorException(
    message: String? = null,
    @Suppress("unused")
    val errNum: Int = getLastError(),
) : RuntimeException(
    when {
        message == null -> strError(errNum)
        else -> "$message ${strError(errNum)}"
    },
) {
    constructor(errNum: Int) : this(
        message = null,
        errNum = errNum,
    )
}

fun getLastError(): Int = GetLastError().toInt()

@OptIn(ExperimentalForeignApi::class)
fun strError(errNum: Int = getLastError()): String = memScoped {
    val buffer = allocArray<ByteVar>(256)
    val written = FormatMessageA(
        (FORMAT_MESSAGE_FROM_SYSTEM or FORMAT_MESSAGE_IGNORE_INSERTS).toUInt(),
        null,
        errNum.toUInt(),
        0u,
        buffer,
        256u,
        null,
    )
    if (written != 0u) buffer.toKString().trim() else "error=$errNum"
}

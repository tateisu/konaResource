package jp.juggler.konaArchive.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.errno
import platform.posix.strerror

internal class ErrnoException(
    message: String? = null,
    @Suppress("unused")
    val errNum: Int = errno,
) : RuntimeException(
    when {
        message == null -> strError(errNum)
        else -> "$message ${strError(errNum)}"
    },
) {
    constructor(errNum: Int = errno) : this(
        message = null,
        errNum = errNum,
    )
}

@OptIn(ExperimentalForeignApi::class)
internal fun strError(errNum: Int = errno): String =
    strerror(errNum)?.toKString() ?: "errno=$errNum"

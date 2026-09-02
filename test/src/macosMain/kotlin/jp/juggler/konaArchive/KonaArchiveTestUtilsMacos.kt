package jp.juggler.konaArchive

import platform.posix.mkdir

internal actual fun mkdirWithUserRwxPermission(path: String): Int = mkdir(path, 448U.toUShort())

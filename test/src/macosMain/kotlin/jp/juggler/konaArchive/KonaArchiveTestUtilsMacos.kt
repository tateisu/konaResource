package jp.juggler.konaArchive

import platform.posix.EEXIST
import platform.posix.errno
import platform.posix.mkdir

internal actual fun mkdirWithUserRwxPermission(path: String) {
    if (mkdir(path, 448U.toUShort()) != 0 && errno != EEXIST) {
        error("Unable to create directory: $path (errno=$errno)")
    }
}

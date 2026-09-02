package jp.juggler.konaArchive

import platform.windows.CreateDirectoryA
import platform.windows.GetLastError

private const val ERROR_ALREADY_EXISTS = 183u

internal actual fun mkdirWithUserRwxPermission(path: String) {
    if (CreateDirectoryA(path, null) == 0) {
        val errorCode = GetLastError()
        if (errorCode != ERROR_ALREADY_EXISTS) {
            error("Unable to create directory: $path (error=$errorCode)")
        }
    }
}

package jp.juggler.konaArchive

internal actual fun mkdirWithUserRwxPermission(path: String) {
    val directory = java.io.File(path)
    if (!directory.mkdir() && !directory.isDirectory) {
        throw java.io.IOException("Unable to create directory: $path")
    }
}

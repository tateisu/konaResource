package jp.juggler.konaArchive

/** Creates the directory, treating an existing directory as success. Throws on other failures. */
internal expect fun mkdirWithUserRwxPermission(path: String)

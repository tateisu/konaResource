package jp.juggler.konaResource.benchmark

import jp.juggler.konaArchive.util.KonaBlake3n256Jni
import jp.juggler.konaArchive.util.KonaDigest

internal actual fun defaultBlake3(): KonaDigest = KonaBlake3n256Jni()

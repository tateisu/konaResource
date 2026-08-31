package jp.juggler.konaArchive.util

actual val defaultKonaBlake3n256: KonaDigest = KonaBlake3n256Jni()
actual val defaultKonaSha256: KonaDigest = KonaSha256Jvm()
actual val defaultLz4Codec: Lz4Codec = Lz4CodecJvm

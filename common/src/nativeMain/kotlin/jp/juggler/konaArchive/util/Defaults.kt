package jp.juggler.konaArchive.util

actual val defaultKonaBlake3n256: KonaDigest = KonaBlake3n256Native()
actual val defaultKonaSha256: KonaDigest = KonaSha256Intrinsics()
actual val defaultLz4Codec: Lz4Codec = Lz4CodecNative

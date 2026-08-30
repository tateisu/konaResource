package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jp.juggler.konaArchive.util.EmbedRandomAccess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import okio.Buffer

@OptIn(ExperimentalForeignApi::class)
@Suppress("MagicNumber")
class EmbedRandomAccessTest : FreeSpec(
    {
        "readsPinnedMemoryAndSupportsEmptyRanges" {
            val source = byteArrayOf(0x04, 0x03, 0x02, 0x01, 0x7f)
            val pinned = source.pin()
            try {
                val address = pinned.addressOf(0).rawValue.toLong()
                val access = EmbedRandomAccess(address until address + source.size)

                access.size shouldBe source.size.toLong()
                access.pos shouldBe 0L
                access.readInt32("test") shouldBe 0x01020304
                access.pos shouldBe 4L
                access.readByte() shouldBe 0x7f
                access.readByte().shouldBeNull()
                access.pos shouldBe source.size.toLong()

                access.seek(0)
                val destination = ByteArray(source.size + 2)
                access.readByteArray(destination, 1, source.size + 1) shouldBe source.size
                destination.copyOfRange(1, source.size + 1).toList() shouldBe source.toList()

                access.seek(0)
                val buffer = Buffer()
                access.readBuffer(buffer, source.size.toLong()) shouldBe source.size.toLong()
                buffer.readByteArray().toList() shouldBe source.toList()

                access.seek(access.size)
                access.pos shouldBe access.size
                access.readByteArray(ByteArray(1)) shouldBe 0

                val empty = access.subRange(2, 2)
                empty.size shouldBe 0L
                empty.pos shouldBe 0L
                empty.readByteArray(ByteArray(1)) shouldBe 0
                empty.readByte().shouldBeNull()
            } finally {
                pinned.unpin()
            }
        }
    },
)

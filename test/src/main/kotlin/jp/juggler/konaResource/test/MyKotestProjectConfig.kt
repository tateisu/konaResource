package jp.juggler.konaResource.test

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.config.LogLevel
import io.kotest.core.descriptors.Descriptor
import io.kotest.core.extensions.Extension
import io.kotest.engine.extensions.filter.DescriptorFilter
import io.kotest.engine.extensions.filter.DescriptorFilterResult

/**
 * --testName でテストを絞り込む config。
 * DescriptorFilter を拡張として渡す。testName はパスの前方一致(プレフィックス)で、
 * - `testName.startsWith(path)`: 対象の祖先コンテナ/対象自身を有効化(子の発見に必要)
 * - `path.startsWith(testName)`: testName 配下のテストを有効化
 * のどちらかを満たすディスクリプタだけを実行する。
 */
internal class MyKotestProjectConfig(
    private val testName: String?,
) : AbstractProjectConfig() {
    override val logLevel = LogLevel.Off

    override val extensions: List<Extension> = buildList {
        if (testName != null) {
            add(
                object : DescriptorFilter {
                    override fun filter(descriptor: Descriptor): DescriptorFilterResult {
                        val path = descriptor.testParts().joinToString("/").trim()
                        return when {
                            // testName がこのパスの先にある(対象の祖先コンテナや対象自身) → 有効化して子を発見させる
                            // このパスが testName を先頭に含む(testName 配下のテスト) → 有効化
                            testName.startsWith(path) || path.startsWith(testName) ->
                                DescriptorFilterResult.Include

                            else -> DescriptorFilterResult.Exclude("testName not match.")
                        }
                    }
                },
            )
        }
    }
}

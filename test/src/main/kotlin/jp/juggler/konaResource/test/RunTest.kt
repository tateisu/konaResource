package jp.juggler.konaResource.test

import io.kotest.common.KotestInternal
import io.kotest.core.spec.SpecRef
import io.kotest.engine.TestEngineLauncher
import jp.juggler.util.EmptyCoroutineScope
import jp.juggler.util.notBlank
import kotlinx.coroutines.runBlocking

/**
 * kotest のテストを実行する。
 * テストクラスの一覧は KSP 生成の TestClassList を返すターゲット別の actual(testSpecList)から得る。
 *
 * - `--testClass X`: クラス名が X で終わるものだけを実行する(後方一致)。
 * - `--testName Y`: パスが Y を前方一致(プレフィックス)で含むテストだけを実行する。
 */
@OptIn(KotestInternal::class)
fun runTest(cliOptions: CliOptions) {
    val testOptions = cliOptions.test

    var specs = allTestSpecs().map { it to (it.kclass.qualifiedName ?: "(anonymous)") }

    // testClass はクラスfqcnの後方一致で絞り込む
    testOptions.testClass.notBlank()?.let { filterClass ->
        specs = specs.filter { it.second.endsWith(filterClass) }
    }

    when {
        testOptions.list -> {
            for ((_, className) in specs.sortedBy { it.second }) {
                println(className)
                // テスト名の列挙は諦めた。kotestでは動的すぎて色々難しい
            }
        }

        specs.isEmpty() -> {
            when (val filterClass = testOptions.testClass.notBlank()) {
                null -> error("no test classes.")
                else -> error("no test classes that match to [$filterClass]")
            }
        }

        else -> {
            runBlocking {
                val listener = MyTestEngineListener(EmptyCoroutineScope)
                try {
                    // 出力形式はカスタム TestEngineListener(TestReporter)で自由に変更できる。
                    TestEngineLauncher()
                        .withSpecRefs(specs.map { it.first })
                        .withProjectConfig(MyKotestProjectConfig(testOptions.testName.notBlank()))
                        .withListener(listener)
                        .execute()
                } finally {
                    listener.close()
                }
            }
        }
    }
}

/**
 * このターゲットで実行するテストクラスの一覧。
 * テストクラスはターゲットごとに異なる(jvm/native/posix/mingw)ため、leaf ソースセットの actual が
 * KSP 生成の TestClassList を返す。
 */
internal expect fun allTestSpecs(): List<SpecRef>

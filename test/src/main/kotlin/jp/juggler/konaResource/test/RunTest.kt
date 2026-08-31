@file:Suppress("MatchingDeclarationName")

package jp.juggler.konaResource.test

import jp.juggler.util.buildCommandSpec

class TestOptions {
    var testClass = ""
    var testName = ""
}

val specTest = buildCommandSpec(
    "run the test.",
    creator = { TestOptions() },
) {
    arg(
        desc = "test class",
        name = "testClass",
        required = false,
        multiple = false,
    ) { testClass = it }
    arg(
        desc = "test name",
        name = "testName",
        required = false,
        multiple = false,
    ) { testName = it }
}

/**
 * kotest の TestEngineLauncher でテストを実行する。
 * 実際の実行はターゲット別の actual が行い、テストクラスの一覧は KSP 生成の TestClassList から得る。
 */
internal expect fun runTest(cliOptions: CliOptions)

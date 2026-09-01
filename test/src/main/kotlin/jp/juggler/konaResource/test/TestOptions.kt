package jp.juggler.konaResource.test

import jp.juggler.util.buildCommandSpec

class TestOptions {
    var list = false
    var testClass = ""
    var testName = ""
}

val specTest = buildCommandSpec(
    "run the test.",
    creator = { TestOptions() },
) {
    arg(
        desc = "クラス名が指定文字列で終わるものをテストする",
        name = "testClass",
        required = false,
        multiple = false,
    ) { testClass = it }

    arg(
        desc = "テスト名が指定文字列で始まるものとその親をテストする",
        name = "testName",
        required = false,
        multiple = false,
    ) { testName = it }

    flagOption(
        desc = "テストクラスの列挙",
        fullName = "list",
        shortName = 'l',
    ) { list = it }
}

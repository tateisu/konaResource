package jp.juggler.konaResource.test

import jp.juggler.util.ArgParserConfig
import jp.juggler.util.ArgParserResult

class CliOptions(
    val result: ArgParserResult,
    val top: TopOptions,
    val test: TestOptions,
)

fun cliParserConfig(args: Array<out String>) = ArgParserConfig(
    program = "konaCommonTest",
    args = args,
    topSpec = specTop,
    subcommandByArg = true,
    subcommands = mapOf(
        "test" to specTest,
    ),
)

inline fun <reified T> ArgParserResult.castOrCreate(creator: () -> T) =
    (subcommand as? T) ?: creator()

fun ArgParserResult.toCliOptions() = CliOptions(
    result = this,
    top = top as TopOptions,
    test = castOrCreate { TestOptions() },
)

package jp.juggler.konaResource.test

import jp.juggler.util.LogTag
import jp.juggler.util.parse

private val log = LogTag("Main")

fun main(args: Array<out String>) {
    val result = cliParserConfig(args).parse()
    fun usage(error: String? = null) = println(result.formatUsage(error))
    val cliOptions = result.toCliOptions()
    LogTag.level = cliOptions.top.verbose + 1
    val error = result.error
    try {
        when {
            error != null -> throw error
            result.help -> usage()

            else -> when (val name = result.subcommandName) {
                null -> usage("subcommand not specified.")
                "test" -> runTest(cliOptions)
                else -> usage("unknown subcommand name. [$name]")
            }
        }
    } catch (ex: Throwable) {
        log.e(ex)
    }
}

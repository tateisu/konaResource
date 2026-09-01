package jp.juggler.konaArchive.cli

import jp.juggler.konaArchive.konaArchiveExtract
import jp.juggler.konaArchive.konaArchiveList
import jp.juggler.konaArchive.konaArchivePack
import jp.juggler.util.ArgParserConfig
import jp.juggler.util.ArgParserResult
import jp.juggler.util.LogTag
import jp.juggler.util.buildCommandSpec
import jp.juggler.util.parse
import java.nio.file.Path
import kotlin.system.exitProcess

private val log = LogTag("Main")
private const val PROGRAM_NAME = "konaArchive"
private const val SUBCOMMAND_PACK = "pack"
private const val SUBCOMMAND_LIST = "list"
private const val SUBCOMMAND_EXTRACT = "extract"

// -----------------------------------------------
private class TopOptions(
    var verbose: Int = 0,
)

private val specTop = buildCommandSpec(
    desc = "konaArchive maintenance tool",
    creator = { TopOptions() },
) {
    incrementalOption(
        "冗長出力",
        fullName = "verbose",
        shortName = 'v',
    ) { verbose = it ?: (1 + verbose) }
}
// -----------------------------------------------

private class PackOptions(
    var archive: String = "",
    var inputDirectory: String = "",
    var previous: String? = null,
)

private val specPack = buildCommandSpec(
    desc = "Create archive",
    creator = { PackOptions() },
) {
    arg(
        desc = "Output archive path",
        name = "archiveFile",
        required = true,
    ) { archive = it }
    arg(
        desc = "Input directory path",
        name = "inDir",
        required = true,
    ) { inputDirectory = it }

    stringOption(
        desc = "Previous archive path used to incremental update",
        fullName = "previous",
        shortName = 'p',
        valueName = "filePath",
    ) { previous = it }
}

private fun pack(options: CliOptions) {
    with(options.pack) {
        konaArchivePack(
            archivePath = Path.of(archive),
            inputDirectory = Path.of(inputDirectory),
            previousArchivePath = previous?.let { Path.of(it) },
        )
    }
}

// -----------------------------------------------

private class ListOptions(
    var archive: String = "",
)

private val specList = buildCommandSpec(
    desc = "List archive entries",
    creator = { ListOptions() },
) {
    arg(
        desc = "Archive file path",
        name = "archivePath",
        required = true,
    ) { archive = it }
}

private fun list(options: CliOptions) {
    with(options.list) {
        konaArchiveList(
            archiveFile = Path.of(archive).toFile(),
        )
    }
}

// -----------------------------------------------

private class ExtractOptions(
    var archive: String = "",
    var outputDirectory: String = "",
)

private val specExtract = buildCommandSpec(
    desc = "Extract archive entries",
    creator = { ExtractOptions() },
) {
    arg(
        desc = "Archive file path",
        name = "archive",
        required = true,
    ) { archive = it }

    arg(
        desc = "Output directory path",
        name = "outAir",
        required = true,
    ) { outputDirectory = it }
}

private fun extract(options: CliOptions) {
    with(options.extract) {
        konaArchiveExtract(
            archivePath = Path.of(archive),
            outputDirectory = Path.of(outputDirectory),
        )
    }
}

// -----------------------------------------------

private class CliOptions(
    @Suppress("unused")
    val result: ArgParserResult,
    val top: TopOptions,
    val pack: PackOptions,
    val list: ListOptions,
    val extract: ExtractOptions,
)

private fun cliOptionsConfig(args: Array<out String>) = ArgParserConfig(
    program = PROGRAM_NAME,
    args = args,
    topSpec = specTop,
    subcommandByArg = true,
    subcommands = mapOf(
        SUBCOMMAND_PACK to specPack,
        SUBCOMMAND_LIST to specList,
        SUBCOMMAND_EXTRACT to specExtract,
    ),
)

private inline fun <reified T> ArgParserResult.castOrCreate(creator: () -> T): T =
    (subcommand as? T) ?: creator()

private fun ArgParserResult.toCliOptions() = CliOptions(
    result = this,
    top = top as TopOptions,
    pack = castOrCreate { PackOptions() },
    list = castOrCreate { ListOptions() },
    extract = castOrCreate { ExtractOptions() },
)

fun main(args: Array<String>) {
    val result = cliOptionsConfig(args).parse()
    val cliOptions = result.toCliOptions()
    LogTag.level = cliOptions.top.verbose + 1
    val error = result.error
    fun usage(error: String? = null) = println(result.formatUsage(error))
    when {
        error != null -> {
            log.e(error)
            exitProcess(1)
        }

        result.help -> {
            usage()
            exitProcess(1)
        }

        else -> when (result.subcommandName) {
            null -> {
                usage("subcommand not specified.")
                exitProcess(1)
            }

            SUBCOMMAND_PACK -> pack(cliOptions)
            SUBCOMMAND_LIST -> list(cliOptions)
            SUBCOMMAND_EXTRACT -> extract(cliOptions)
            else -> {
                usage("unknown subcommand [${result.subcommandName}]")
                exitProcess(1)
            }
        }
    }
}

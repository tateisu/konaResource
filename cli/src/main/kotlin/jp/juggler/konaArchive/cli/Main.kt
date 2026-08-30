@file:OptIn(kotlinx.cli.ExperimentalCli::class)

package jp.juggler.konaArchive.cli

import jp.juggler.konaArchive.konaArchiveExtract
import jp.juggler.konaArchive.konaArchiveList
import jp.juggler.konaArchive.konaArchivePack
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.Subcommand
import java.nio.file.Path

private const val PROGRAM_NAME = "konaArchive"

private class PackCommand : Subcommand(
    name = "pack",
    actionDescription = "Create archive",
) {
    private val archive by argument(
        type = ArgType.String,
        description = "Output archive path",
    )
    private val inputDirectory by argument(
        type = ArgType.String,
        description = "Input directory path",
    )
    private val previous by option(
        type = ArgType.String,
        shortName = "p",
        description = "Previous archive path",
    )

    override fun execute() {
        konaArchivePack(
            archivePath = Path.of(archive),
            inputDirectory = Path.of(inputDirectory),
            previousArchivePath = previous?.let { Path.of(it) },
        )
    }
}

private class ListCommand : Subcommand(
    name = "list",
    actionDescription = "List archive entries",
) {
    private val archive by argument(
        type = ArgType.String,
        description = "Archive path",
    )

    override fun execute() {
        konaArchiveList(
            archiveFile = Path.of(archive).toFile(),
        )
    }
}

private class ExtractCommand : Subcommand(
    name = "extract",
    actionDescription = "Extract archive entries",
) {
    private val archive by argument(
        type = ArgType.String,
        description = "Archive path",
    )
    private val outputDirectory by argument(
        type = ArgType.String,
        description = "Output directory path",
    )

    override fun execute() {
        konaArchiveExtract(
            archivePath = Path.of(archive),
            outputDirectory = Path.of(outputDirectory),
        )
    }
}

fun main(args: Array<String>) {
    val parser = ArgParser(PROGRAM_NAME)
    parser.subcommands(
        PackCommand(),
        ListCommand(),
        ExtractCommand(),
    )
    parser.parse(args)
}

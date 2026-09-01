package jp.juggler.konaResource.test

import jp.juggler.util.buildCommandSpec

class TopOptions {
    var verbose = 0
}

val specTop = buildCommandSpec(
    "test module",
    creator = { TopOptions() },
) {
    incrementalOption(
        desc = "verbose output. to increase level, use -v=<level> or multi time set like as -vvv",
        fullName = "verbose",
        shortName = 'v',
        valueName = "level",
        setter = { verbose = it ?: (1 + verbose) },
    )
}

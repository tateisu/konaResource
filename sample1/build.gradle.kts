plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // =======================================
    // --- konaResource plugin ---
    id("jp.juggler.konaResource") version "0.1.5"
    // =======================================
}

// =======================================
// --- konaResource{} block ---
konaResource {
    // --- add pairs of resouce symbol name and resource input directory
    modules.add("res" to "src/res")
    modules.add("res2" to "src/res2")

//    // LZ4 compression parameters. All parameters have default values and are optional.
//    // LZ4F compression level. 0 is the default fast compression, positive values use LZ4HC, and negative values use fast acceleration.
//    lz4CompressionLevel = 0
//    lz4BlockSizeID = 1MB
//    lz4BlockMode = "LZ4F_blockLinked"
//    lz4ContentSizeFlag = true
//    lz4ContentChecksumFlag	= true
//    lz4blockChecksumFlag = true
//    lz4AutoFlush = false
//    lz4FavorDecSpeed = true
}
// =======================================

kotlin {
    linuxX64 {
        binaries {
            executable {
                entryPoint = "jp.juggler.konaResource.sample.main"
            }
        }
    }
    sourceSets {
        linuxX64Main.dependencies {
            // =======================================
            // add konaResource:common library
            // =======================================
            implementation("jp.juggler.konaResource:common:0.1.5")
        }
    }
}

val hostRunTask = when {
    System.getProperty("os.name").lowercase().contains("linux") &&
        System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64", "x64") ->
        "runDebugExecutableLinuxX64"

    else -> throw GradleException(
        "sample1:runDebug supports Linux x64 hosts only",
    )
}

tasks.register("runDebug") {
    group = "run"
    description = "Runs sample1 for the host architecture."
    dependsOn(hostRunTask)
}

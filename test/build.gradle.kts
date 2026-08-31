import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
}

group = "jp.juggler.konaResource"
version = rootProject.version

// -Pmacos=true を指定したときのみ macos ターゲットを含める。
// macOS ターゲットのJNIビルドは macOS ホストでしか処理できないため。
val enableMacos: Boolean = (findProperty("macos") as? String)?.toBoolean() == true

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    linuxX64()
    linuxArm64()
    if (enableMacos) {
        macosArm64()
    }
    mingwX64()

    targets.withType<KotlinNativeTarget>().configureEach {
        // posixMain は default hierarchy にないため、POSIX ターゲット(linux/apple)の main に srcDir で追加する。
        // mingwMain は default hierarchy が自動生成するため(src/mingwMain/kotlin が既定ディレクトリ)、追加不要。
        if (name != "mingwX64") {
            compilations.getByName("main").defaultSourceSet.kotlin.srcDir("src/posixMain/kotlin")
        }
        // runTest の actual。ターゲット別の KSP 生成 TestClassList を参照するため leaf ソースセットに追加する。
        compilations.getByName("main").defaultSourceSet.kotlin.srcDir("src/cliRun/kotlin")

        // CLI のネイティブ実行可能バイナリ。ネイティブターゲットは blake3Jni(JNI) に依存しない。
        binaries.executable {
            entryPoint = "jp.juggler.konaResource.test.main"
            baseName = "konaCommonTest"
        }
    }

    sourceSets {
        // 共有テストクラス・フィクスチャ・CLI は src/main に置く。
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":common"))
                implementation(libs.okio)
                implementation(libs.kotlinxDatetime)
                implementation(libs.kotestFrameworkEngine)
                implementation(libs.kotestAssertions)
            }
        }
        jvmMain {
            kotlin.srcDir("src/cliRun/kotlin")
            dependencies {
                implementation(libs.kotestFrameworkEngine)
                implementation(libs.kotestRunner)
                implementation(libs.kotestAssertions)
            }
        }
        nativeMain.dependencies {
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestAssertions)
        }
    }
}

// KSP: テストクラス(Spec サブクラス)を列挙するプロセッサ。
// ターゲットごとの main コンパイルに TestClassList を生成する。
dependencies {
    add("kspJvm", project(":test-ksp"))
    add("kspLinuxX64", project(":test-ksp"))
    add("kspLinuxArm64", project(":test-ksp"))
    add("kspMingwX64", project(":test-ksp"))
    if (enableMacos) {
        add("kspMacosArm64", project(":test-ksp"))
    }
}

// FatJar(CLI)。blake3Jni の DLL を全プラットフォーム分同梱する。
val fatJar = tasks.register<Jar>("konaCommonTestFatJar") {
    dependsOn("jvmMainClasses")
    dependsOn(":blake3Jni:buildBlake3JniLinuxX64")
    dependsOn(":blake3Jni:buildBlake3JniLinuxArm64")
    dependsOn(":blake3Jni:buildBlake3JniWindowsX64")
    dependsOn(":blake3Jni:buildBlake3JniWindowsArm64")
    if (enableMacos) {
        dependsOn(":blake3Jni:buildBlake3JniMacosUniversal2")
    }
    archiveFileName.set("konaCommonTest.jar")
    manifest {
        attributes["Main-Class"] = "jp.juggler.konaResource.test.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(tasks.named("jvmMainClasses"))

    // 依存ライブラリ(common-jvm, okio, kotlinx-cli, stdlib)を展開して含める。
    from(configurations.named("jvmRuntimeClasspath").map { classpath ->
        classpath.files.map { dep ->
            if (dep.isDirectory) dep else zipTree(dep)
        }
    })

    // blake3Jni の DLL をプラットフォーム別パスで同梱する。
    val blake3Jni = rootProject.project(":blake3Jni")
    from(blake3Jni.file("build/native/linuxX64/libblake3_jni.so")) {
        into("jp/juggler/konaArchive/native/linux-x86_64")
    }
    from(blake3Jni.file("build/native/linuxArm64/libblake3_jni.so")) {
        into("jp/juggler/konaArchive/native/linux-aarch64")
    }
    from(blake3Jni.file("build/native/windowsX64/blake3_jni.dll")) {
        into("jp/juggler/konaArchive/native/windows-x86_64")
    }
    from(blake3Jni.file("build/native/windowsArm64/blake3_jni.dll")) {
        into("jp/juggler/konaArchive/native/windows-aarch64")
    }
    if (enableMacos) {
        from(blake3Jni.file("build/native/macosUniversal2/libblake3_jni.dylib")) {
            into("jp/juggler/konaArchive/native/macos-universal")
        }
    }
}

// ネイティブ実行可能バイナリのリンクタスクと、deploy 時の表示名
data class CliNativeBinary(
    val displayName: String,
    val linkTaskName: String,
)

val cliNativeBinaries = buildList {
    add(CliNativeBinary("linuxX64", "linkReleaseExecutableLinuxX64"))
    add(CliNativeBinary("linuxArm64", "linkReleaseExecutableLinuxArm64"))
    add(CliNativeBinary("mingwX64", "linkReleaseExecutableMingwX64"))
    if (enableMacos) {
        add(CliNativeBinary("macosArm64", "linkReleaseExecutableMacosArm64"))
    }
}

// A(ネイティブバイナリ)と B(FatJar)をルートプロジェクトへコピーする。
val deploy = tasks.register("deploy") {
    group = "build"
    description = "Copies the CLI native binaries and FatJar to the root project"
    dependsOn(fatJar)
    cliNativeBinaries.forEach { dependsOn(it.linkTaskName) }

    doLast {
        copy {
            from(fatJar.flatMap { it.archiveFile })
            into(rootProject.layout.projectDirectory)
            rename { "konaCommonTest.jar" }
        }
        cliNativeBinaries.forEach { binary ->
            val linkTask = tasks.named<KotlinNativeLink>(binary.linkTaskName).get()
            val output = linkTask.outputFile.get()
            val binaryFile: File = when (output) {
                is File -> output
                else -> (output as org.gradle.api.file.RegularFile).asFile
            }
            val extension = binaryFile.extension.takeIf { it.isNotEmpty() && it != "kexe" }
                ?.let { ".$it" } ?: ""
            copy {
                from(binaryFile)
                into(rootProject.layout.projectDirectory)
                rename { "konaCommonTest-${binary.displayName}$extension" }
            }
        }
    }
}

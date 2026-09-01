import jp.juggler.konaResource.buildlogic.DeployBinarySpec
import jp.juggler.konaResource.buildlogic.DeployKonaCommonTestTask
import jp.juggler.konaResource.buildlogic.isCompilerAvailable
import jp.juggler.konaResource.buildlogic.macosEnabled
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    id("jp.juggler.konaResource.buildlogic")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
}

group = "jp.juggler.konaResource"
version = rootProject.version

// -Pmacos=true/false の上書きを考慮した macOS ビルドの有効/無効 (build-logic のユーティリティ)。
val enableMacos: Boolean = macosEnabled()

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
        val sourceSetKotlin = compilations.getByName("main").defaultSourceSet.kotlin
        // posixMain は default hierarchy にないため、POSIX ターゲット(linux/apple)の main に srcDir で追加する。
        // mingwMain は default hierarchy が自動生成するため(src/mingwMain/kotlin が既定ディレクトリ)、追加不要。
        if (name != "mingwX64") sourceSetKotlin.srcDir("src/posixMain/kotlin")

        // testSpecList の actual。KSP 生成の TestClassList はターゲット別のため、leaf ソースセットに追加する。
        // (nativeMain のような共有ソースセットでは metadata コンパイルに TestClassList が無く参照できない)
        sourceSetKotlin.srcDir("src/testSpecs/kotlin")

        // CLI のネイティブ実行可能バイナリ。ネイティブターゲットは blake3Jni(JNI) に依存しない。
        binaries.executable {
            entryPoint = "jp.juggler.konaResource.test.main"
            baseName = "konaCommonTest"
        }
    }

    sourceSets {
        // 共有テストクラス・フィクスチャ・CLI は src/main に置く
        commonMain {
            kotlin.srcDir("src/main/kotlin")
            dependencies {
                implementation(project(":utils"))
                implementation(project(":common"))
                implementation(libs.kotestAssertions)
                implementation(libs.kotestFrameworkEngine)
                implementation(libs.kotlinxAtomicfu)
                implementation(libs.kotlinxDatetime)
                implementation(libs.okio)
            }
        }
        jvmMain {
            kotlin.srcDir("src/testSpecs/kotlin")
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

// blake3Jni の DLL 情報。buildTask で生成し、resourceDir に同梱する。
data class JniDll(
    val compiler: String,
    val buildTask: String,
    val sourcePath: String,
    val resourceDir: String,
    val required: Boolean = false,
    val availability: () -> Boolean = { isCompilerAvailable(compiler) },
    val skipReason: String = "cross compiler '$compiler' not found",
)

// FatJar(CLI)。利用可能なクロスコンパイラが存在する blake3Jni の DLL のみを同梱する。
// ビルドできないターゲットは自動的にスキップし、警告を出す
// (linux-x86_64 の .so は common-jvm の jar が同梱するため必須ではない)。
val fatJar = tasks.register<Jar>("konaCommonTestFatJar") {
    description = "テストモジュールのJVM Fat Jarを作成します"
    dependsOn("jvmMainClasses")
    val blake3Jni = rootProject.project(":blake3Jni")
    val jniDlls = listOf(
        JniDll(
            compiler = "cc",
            buildTask = ":blake3Jni:buildBlake3JniLinuxX64",
            sourcePath = "build/native/linuxX64/libblake3_jni.so",
            resourceDir = "linux-x86_64",
            required = true,
        ),
        JniDll(
            compiler = "aarch64-linux-gnu-gcc",
            buildTask = ":blake3Jni:buildBlake3JniLinuxArm64",
            sourcePath = "build/native/linuxArm64/libblake3_jni.so",
            resourceDir = "linux-aarch64",
        ),
        JniDll(
            compiler = "x86_64-w64-mingw32-gcc",
            buildTask = ":blake3Jni:buildBlake3JniWindowsX64",
            sourcePath = "build/native/windowsX64/blake3_jni.dll",
            resourceDir = "windows-x86_64",
        ),
        JniDll(
            compiler = "aarch64-w64-mingw32-gcc",
            buildTask = ":blake3Jni:buildBlake3JniWindowsArm64",
            sourcePath = "build/native/windowsArm64/blake3_jni.dll",
            resourceDir = "windows-aarch64",
        ),
        JniDll(
            compiler = "cc",
            buildTask = ":blake3Jni:buildBlake3JniMacosUniversal2",
            sourcePath = "build/native/macosUniversal2/libblake3_jni.dylib",
            resourceDir = "macos-universal",
            availability = { enableMacos },
            skipReason = "macOS build not available (requires macOS host or osxcross)",
        ),
    )

    jniDlls.forEach { dll ->
        if (dll.availability()) {
            dependsOn(dll.buildTask)
            from(blake3Jni.file(dll.sourcePath)) {
                into("jp/juggler/konaArchive/native/${dll.resourceDir}")
            }
        } else {
            if (dll.required) {
                throw GradleException("Required compiler '${dll.compiler}' not found on PATH.")
            }
            logger.warn("konaCommonTestFatJar: skipping ${dll.resourceDir} (${dll.skipReason})")
        }
    }

    archiveFileName.set("konaCommonTest.jar")
    manifest {
        attributes["Main-Class"] = "jp.juggler.konaResource.test.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // jvmMainClasses の outputs が空のため、実クラスのある compileKotlinJvm の出力を同梱する。
    from(tasks.named("compileKotlinJvm").map { it.outputs.files })

    // 依存ライブラリ(common-jvm, okio, kotlinx-cli, stdlib)を展開して含める。
    from(
        configurations.named("jvmRuntimeClasspath").map { classpath ->
            classpath.files.map { dep ->
                if (dep.isDirectory) dep else zipTree(dep)
            }
        },
    )
}

// ネイティブ実行可能バイナリのリンクタスクと、deploy 時の表示名
data class CliNativeBinary(
    val displayName: String,
    val linkTaskName: String,
)

val cliNativeBinaries = buildList {
    add(CliNativeBinary("linuxX64", "linkDebugExecutableLinuxX64"))
    add(CliNativeBinary("linuxArm64", "linkDebugExecutableLinuxArm64"))
    add(CliNativeBinary("mingwX64", "linkDebugExecutableMingwX64"))
    if (enableMacos) {
        add(CliNativeBinary("macosArm64", "linkDebugExecutableMacosArm64"))
    }
}

// A(ネイティブバイナリ)と B(FatJar)をルートプロジェクトへコピーする。
tasks.register("deploy", DeployKonaCommonTestTask::class.java) {
    group = "build"
    description = "Copies the CLI native binaries and FatJar to the root project"
    dependsOn(fatJar)
    cliNativeBinaries.forEach { dependsOn(it.linkTaskName) }
    destinationDirectory.set(rootProject.layout.projectDirectory)
    fatJarFile.set(fatJar.flatMap { it.archiveFile })
    binaryFiles.from(
        cliNativeBinaries.map { binary ->
            tasks.named<KotlinNativeLink>(binary.linkTaskName).get().outputFile.get()
        },
    )
    deploySpecs.set(
        cliNativeBinaries.map { binary ->
            val binaryFile = tasks.named<KotlinNativeLink>(binary.linkTaskName).get().outputFile.get()
            DeployBinarySpec(displayName = binary.displayName, fileName = binaryFile.absolutePath)
        },
    )
}

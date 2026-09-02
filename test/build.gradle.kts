import jp.juggler.konaResource.buildlogic.DeployBinarySpec
import jp.juggler.konaResource.buildlogic.DeployKonaCommonTestTask
import jp.juggler.konaResource.buildlogic.availableKonaBuildTarget
import jp.juggler.konaResource.buildlogic.getKonaBuildHost
import jp.juggler.konaResource.buildlogic.konaTargets
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
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

val availableKonaBuildTargets = availableKonaBuildTarget()

val hostArch: String by lazy { getKonaBuildHost().targetName }

kotlin {
    konaTargets()
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    targets.withType<KotlinNativeTarget>().configureEach {
        val sourceSetKotlin = compilations.getByName("main").defaultSourceSet.kotlin
        // posixMain は default hierarchy にないため、POSIX ターゲット(linux/apple)の main に srcDir で追加する。
        // mingwMain は default hierarchy が自動生成するため(src/mingwMain/kotlin が既定ディレクトリ)、追加不要。
        if (name != "mingwX64") sourceSetKotlin.srcDir("src/posixMain/kotlin")

        // testSpecList の actual。KSP 生成の TestClassList はターゲット別のため、leaf ソースセットに追加する。
        // (nativeMain のような共有ソースセットでは metadata コンパイルに TestClassList が無く参照できない)
        sourceSetKotlin.srcDir("src/testSpecs/kotlin")

        // CLI のネイティブ実行可能バイナリ。ネイティブターゲットは commonJni(JNI) に依存しない。
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
            implementation(project(":utils"))
            implementation(libs.kotestFrameworkEngine)
            implementation(libs.kotestAssertions)
        }
    }
}

tasks.register("runDebug") {
    group = "run"
    description = "Runs the test CLI for the host architecture."
    dependsOn("runDebugExecutable$hostArch")
}

tasks.named<Exec>("runDebugExecutable$hostArch") {
    providers.gradleProperty("args").orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { arguments -> args(arguments.split(Regex("\\s+"))) }
}

gradle.projectsEvaluated {
    tasks.named<JavaExec>("jvmRun") {
        mainClass.set("jp.juggler.konaResource.test.MainKt")
    }
}

// KSP: テストクラス(Spec サブクラス)を列挙するプロセッサ。
// ターゲットごとの main コンパイルに TestClassList を生成する。
dependencies {
    add("kspJvm", project(":test-ksp"))
    availableKonaBuildTargets.forEach { target ->
        val targetSuffix = target.targetName.replaceFirstChar { it.uppercase() }
        add("ksp$targetSuffix", project(":test-ksp"))
    }
}

// FatJar(CLI)。common-jvm とその推移依存を展開して同梱する。
val fatJar = tasks.register<Jar>("konaCommonTestFatJar") {
    description = "テストモジュールのJVM Fat Jarを作成します"
    dependsOn("jvmMainClasses")

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
    availableKonaBuildTargets.forEach { target ->
        val targetSuffix = target.targetName.replaceFirstChar { it.uppercase() }
        add(CliNativeBinary(target.targetName, "linkDebugExecutable$targetSuffix"))
    }
}

// A(ネイティブバイナリ)と B(FatJar)をルートプロジェクトへコピーする。
tasks.register("deploy", DeployKonaCommonTestTask::class.java) {
    group = "build"
    description = "Copies the CLI native binaries and FatJar to rootProject/bin"
    dependsOn(fatJar)
    cliNativeBinaries.forEach { dependsOn(it.linkTaskName) }
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("bin"))
    deployedFiles.from(
        rootProject.file("bin/konaCommonTest.jar"),
        cliNativeBinaries.map { binary ->
            val binaryFile = tasks.named<KotlinNativeLink>(binary.linkTaskName).get().outputFile.get()
            val extension = binaryFile.extension.takeIf { it.isNotEmpty() && it != "kexe" }?.let { ".$it" } ?: ""
            rootProject.file("bin/konaCommonTest-${binary.displayName}$extension")
        },
    )
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

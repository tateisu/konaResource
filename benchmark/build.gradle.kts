import jp.juggler.konaResource.buildlogic.DeployBinarySpec
import jp.juggler.konaResource.buildlogic.DeployKonaNativeBinariesTask
import jp.juggler.konaResource.buildlogic.availableKonaBuildTarget
import jp.juggler.konaResource.buildlogic.konaTargets
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxBenchmark)
    id("jp.juggler.konaResource.buildlogic")
}

group = "jp.juggler.konaResource"
version = rootProject.version

val skipNativeTargets = System.getenv("SKIP_NATIVE_TARGETS")?.toBoolean() ?: false
val availableNativeTargets = if (skipNativeTargets) {
    emptyList()
} else {
    availableKonaBuildTarget()
}

kotlin {
    jvm()
    if (!skipNativeTargets) {
        konaTargets()
    }
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main").defaultSourceSet.dependencies {
            implementation(project(":utils"))
        }
        binaries.executable {
            entryPoint = "jp.juggler.konaResource.benchmark.main"
            baseName = "konaBenchmark"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":common"))
            implementation(project(":utils"))
            implementation(libs.kotlinxBenchmarkRuntime)
            implementation(libs.okio)
        }
        nativeMain.dependencies {
            implementation(project(":utils"))
        }
        jvmMain.dependencies {
            implementation(project(":utils"))
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("linuxX64")
    }
    configurations {
        named("main") {
            warmups = 3
            iterations = 3
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
        register("smoke") {
            // warmup iteration の回数
            // 1以上でないとiterationが実行されない
            warmups = 1
            // measurement 時間 ≈ iterations(回数) × (iterationTime*iterationTimeUnit)(時間)
            iterations = 1
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
    }
}

val fatJar = tasks.register<Jar>("konaBenchmarkFatJar") {
    description = "Creates a standalone JVM benchmark jar"
    dependsOn("jvmMainClasses")
    archiveFileName.set("konaBenchmark.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "jp.juggler.konaResource.benchmark.MainKt"
    }
    from(tasks.named("compileKotlinJvm").map { it.outputs.files })
    from(
        configurations.named("jvmRuntimeClasspath").map { classpath ->
            classpath.files.map { dependency ->
                if (dependency.isDirectory) dependency else zipTree(dependency)
            }
        },
    )
}

tasks.register("deploy", DeployKonaNativeBinariesTask::class.java) {
    group = "build"
    description = "Copies release benchmark binaries to the root project"
    dependsOn(fatJar)
    jarFile.set(fatJar.flatMap { it.archiveFile })
    availableNativeTargets.forEach { target ->
        val suffix = target.targetName.replaceFirstChar { it.uppercase() }
        dependsOn("linkReleaseExecutable$suffix")
    }
    destinationDirectory.set(rootProject.layout.projectDirectory)
    binaryFiles.from(
        availableNativeTargets.map { target ->
            val suffix = target.targetName.replaceFirstChar { it.uppercase() }
            tasks.named<KotlinNativeLink>("linkReleaseExecutable$suffix").get().outputFile.get()
        },
    )
    deploySpecs.set(
        availableNativeTargets.map { target ->
            val suffix = target.targetName.replaceFirstChar { it.uppercase() }
            val binaryFile = tasks.named<KotlinNativeLink>("linkReleaseExecutable$suffix").get().outputFile.get()
            DeployBinarySpec(displayName = target.targetName, fileName = binaryFile.absolutePath)
        },
    )
}

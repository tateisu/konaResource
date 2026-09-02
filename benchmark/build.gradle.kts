import jp.juggler.konaResource.buildlogic.DeployBinarySpec
import jp.juggler.konaResource.buildlogic.DeployKonaNativeBinariesTask
import jp.juggler.konaResource.buildlogic.availableKonaBuildTarget
import jp.juggler.konaResource.buildlogic.getKonaBuildHost
import jp.juggler.konaResource.buildlogic.konaTargets
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.kotlinMultiplatform)
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

val hostArch: String by lazy { getKonaBuildHost().targetName }

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

gradle.projectsEvaluated {
    tasks.named<JavaExec>("jvmRun") {
        mainClass.set("jp.juggler.konaResource.benchmark.MainKt")
    }
}

if (!skipNativeTargets) {
    tasks.register("runRelease") {
        group = "run"
        description = "Runs the release benchmark for the host architecture."
        dependsOn("runReleaseExecutable$hostArch")
    }
}

tasks.register<org.gradle.api.tasks.JavaExec>("runJvm") {
    group = "run"
    description = "Runs the JVM benchmark."
    dependsOn("jvmMainClasses")
    mainClass.set("jp.juggler.konaResource.benchmark.MainKt")
    classpath(
        tasks.named("compileKotlinJvm").map { it.outputs.files },
        configurations.named("jvmRuntimeClasspath"),
    )
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
    description = "Copies release benchmark binaries to rootProject/bin"
    dependsOn(fatJar)
    jarFile.set(fatJar.flatMap { it.archiveFile })
    availableNativeTargets.forEach { target ->
        val suffix = target.targetName.replaceFirstChar { it.uppercase() }
        dependsOn("linkReleaseExecutable$suffix")
    }
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("bin"))
    deployedFiles.from(
        rootProject.file("bin/konaBenchmark.jar"),
        availableNativeTargets.map { target ->
            val suffix = target.targetName.replaceFirstChar { it.uppercase() }
            val binaryFile = tasks.named<KotlinNativeLink>("linkReleaseExecutable$suffix").get().outputFile.get()
            val extension = binaryFile.extension.takeIf { it.isNotEmpty() && it != "kexe" }?.let { ".$it" } ?: ""
            rootProject.file("bin/konaBenchmark-${target.targetName}$extension")
        },
    )
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

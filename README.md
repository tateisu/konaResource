# konaResource

`konaResource` provides embed resource for Linux/x64 Kotlin/Native executables.
- `plugin` is Gradle plugin that update embed resource.
- `reader` is library to read embed resource.
- `sample1` is sample project that uses `plugin` and `reader`
- `common` is implementation of KonaArchive archive format.
- `cli` is cli tool for  KonaArchive archive format.

## plugin の導入
### 依存関係の追加
(公開したら書く)
### pluginのビルド時設定
- 使用例 `sample1/build.gradle.kts`


```
konaResource{
    // LZ4圧縮パラメータ。全てデフォルト値ありで、指定は必須ではない
    // LZ4F compression level。0 はデフォルト高速圧縮、正数は LZ4HC、負数は fast acceleration
    lz4CompressionLevel = 0
    lz4BlockSizeID = 1MB
    lz4BlockMode = "LZ4F_blockLinked"
    lz4ContentSizeFlag = true
    lz4ContentChecksumFlag	= true
    lz4blockChecksumFlag = true
    lz4AutoFlush = false
    lz4FavorDecSpeed = true

    // .o ファイルのファイル名やシンボル名に使われる
    val name1 = "resources"
    // リソースアーカイブの入力フォルダ
    val inDir1 = "src/resources"
    modules.add( name1 to inDir1 )

    // name と入力フォルダのペアは複数登録できる
    modules.add( "resourcesB" to "src/resourcesB" )
}
```

## reader の導入
- pluginを導入済みであること
- Linux/x64 Kotlin/Native 用のコードから使うこと
- 使用例 'sample1/src/linuxX64Main/kotlin/jp/juggler/konaResource/sample1/Main.kt'

## cliの使用
Run the CLI with `./gradlew :cli:run --args='list archive.bin'`.

## JitPack からの利用
`common`、`reader`、Gradle plugin は JitPack から取得できます。JitPack は GitHub の tag から artifact をビルドするため、認証は不要です。

`sample1` はデフォルトで公開 artifact を使用します。プロジェクトを開発するときだけ、`-PuseLocalArtifacts=true` を指定すると plugin と reader に兄弟モジュールを使用します。

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.tateisu.konaResource")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "jp.juggler.konaResource") {
                useModule("com.github.tateisu.konaResource:plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.tateisu.konaResource")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("jp.juggler.konaResource") version "v0.1.1"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.tateisu.konaResource:reader:v0.1.1")
        }
    }
}
```

## ビルド
// TODO

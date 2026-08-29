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

## GitHub Packages からの利用
`common`、`reader`、Gradle plugin は GitHub Packages の Maven repository に公開されます。
GitHub Packages は取得にも認証が必要なため、`GITHUB_ACTOR` と `GITHUB_TOKEN`（`read:packages` 権限）を設定してください。

```kotlin
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/tateisu/konaResource")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").get()
                password = providers.environmentVariable("GITHUB_TOKEN").get()
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/tateisu/konaResource")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").get()
                password = providers.environmentVariable("GITHUB_TOKEN").get()
            }
        }
        mavenCentral()
    }
}

plugins {
    id("jp.juggler.konaResource") version "0.1.0"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.kona.resource:reader:0.1.0")
        }
    }
}
```

## ビルド
// TODO

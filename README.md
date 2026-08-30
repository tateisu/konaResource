# konaResource

`konaResource` provides embed resource for Linux/x64 Kotlin/Native executables.
- `plugin` is Gradle plugin that update embed resource.
- `common` contains the KonaArchive archive format and the library to read embedded resources.
- `sample1` is sample project that uses `plugin` and `common`
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

## common の導入
- pluginを導入済みであること
- Linux/x64 Kotlin/Native 用のコードから使うこと
- 使用例 'sample1/src/linuxX64Main/kotlin/jp/juggler/konaResource/sample1/Main.kt'

## cliの使用
Run the CLI with `./gradlew :cli:run --args='list archive.bin'`.

## 開発時の依存関係
`sample1` はデフォルトで plugin と common に兄弟モジュールを使用します。

Maven Central に公開された artifact を使う場合は、`-PuseLocalArtifacts=false` を指定します。

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "jp.juggler.konaResource") {
                useModule("jp.juggler.konaResource:plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("jp.juggler.konaResource") version "$version"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("jp.juggler.konaResource:common:$version")
        }
    }
}
```

## Maven Central への公開
Maven Central の user token は Gradle の project directory に保存せず、CI では GitHub Environment secrets から渡します。

必要な Environment secrets は次の通りです。

```text
CENTRAL_PORTAL_USERNAME
CENTRAL_PORTAL_PASSWORD
GPG_KEY_01C52FD776E9651B84D63971A8E469517CB52830
SIGNING_PASSWORD
```

`maven-central` Environment を設定した tag を push すると、GitHub Actions が Central Portal へ upload します。`GPG_KEY_01C52FD776E9651B84D63971A8E469517CB52830` は ASCII-armored 形式の GPG 秘密鍵です。

## ビルド
// TODO

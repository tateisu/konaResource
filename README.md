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

## ビルド

```shell
# ビルド
./gradlew build

# テストを実行
./gradlew check

# sample1 の debug 実行ファイルをビルドして実行
./gradlew sample1:runDebugExecutableLinuxX64

# sample1 の release 実行ファイルをビルドして確認
./gradlew sample1:linkReleaseExecutableLinuxX64
```

### sample1が使用するアーティファクトの切り替え
- `sample1` はデフォルトではプロジェクト中の兄弟モジュールを使用します。
- Gradle に `-Psample1Artifact=0.1.3` のように指定すると Maven Central で公開済みの artifact を使用します。

例:
```
./gradlew -Psample1Artifact=0.1.3 clean sample1:runDebugExecutableLinuxX64
```

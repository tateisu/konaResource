
# プロジェクトの目標
- Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin と アプリからそれを使うライブラリの2つを提供することです。

# 技術スタック
- Compatibility guide for Kotlin Multiplatform https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#version-compatibility の新しい方の構成
- Gradle バージョンカタログ
- okio
- kotest
- lz4 cinterop // implementation("com.ensody.nativebuilds:lz4:1.10.0.2")

# プロジェクト構成
## ルートプロジェクト
- 名前 konaResource
- Kotlin Multiplatform 関連のプラグインを apply false で組み込む
- このプロジェクト自体は何もしない。ビルドターゲットもない。

## common モジュール
- リソースアーカイブの共通コード
- ビルドターゲットに依存しないKotlinコード

## plugin モジュール
- Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin

## reader モジュール
- 自身の .kexe に埋め込まれたリソースを読み出すライブラリ
- ビルドターゲットは Linux/x64 

## cli モジュール
- リソースアーカイブを操作するCLIアプリ

## sample1 モジュール
- 上記 plugin モジュール, reader モジュールを使ってリソースにアクセスするアプリ

- ビルドスクリプトで 以下のような指定を書くと、plugin によって konaResource.o ファイルが生成され、.kexe に 埋め込まれる

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
    val name1 = "konaResource1"
    // リソースアーカイブの入力フォルダ
    val inDir1 = "src/konaResource1"
    modules.add( name1 to inDir1 )

    // name と入力フォルダのペアは複数登録できる
    modules.add( "konaResource2" to "src/konaResource2" )
}
```

- アプリコードは シンボル名を指定すると reader モジュールのコードがアーカイブへのreadアクセス各種を提供する
```
val randomAccess = embedRandomAccess(name)
val konaArchive = randomAccess.decodeKonaArchive(
    createKonaSha256 = { KonaSha256Linux() }
)
println( konaArchive.root.get(0).name )
println( konaArchive.root.get("name").name )
```

# リソースアーカイブ
- .o ファイルに埋め込まれる
- ビルド中間データにアーカイブファイルを持ち、入力ファイルとSHA256が一致するなら圧縮データを再利用する

## ファイル構造

```
- MAGIC 0x0123CDEF
- compressedData[contentCount]
- contentMeta[contentCount]
  - compressedStart: Int32 // compressedData 開始位置のファイル先頭からのバイトオフセット
  - compressedSha256 :Byte[32]
  - compressedSize:Int32
  - unconmpressedSha256 :Byte[32]
  - uncompressedSize: Int32
- names:byte[]
   - :list of pair(nameBytesLength,nameBytes[nameBytesLength])
- dirItems[dirItemsCount]
  要素は以下の2種類
  - FileItem(
        // names要素のファイル先頭からのバイトオフセット
        nameOffset:  Int32 
        // entries要素のファイル先頭からのバイトオフセット
        // 最上位ビットが0であることが DirectoryItem であることを示す
        entryOffset: Int32 
        reserved: Int32  // entries要素のファイル先頭からのバイトオフセット
    )
  - DirectoryItem(
        nameOffset: Int32 // names要素のファイル先頭からのバイトオフセット
        // dirItemsのインデクス or 0x80000000
        // 最上位ビットが1であることが DirectoryItem であることを示す
        dirIndex: Int32
        dirSize: Int32 // フォルダ中の要素のサイズ。
        // Note: 要素数0のディレクトリは空ディレクトリなので dirIndex は常に0となる
    )
- header
  - compressedDataStart:Int32 // compressedData先頭の、ファイル先頭からのバイトオフセット
  - contentMetaStart:Int32 // contentMeta先頭の、ファイル先頭からのバイトオフセット
  - contentMetaSha256 // contentMeta 全体のSHA256ハッシュ
  - namesStart:Int32 // names先頭の、ファイル先頭からのバイトオフセット
  - namesSha256 // contentMeta 全体のSHA256ハッシュ
  - dirItemsStart:Int32 // dirItems先頭の、ファイル先頭からのバイトオフセット
  - dirItemsCount // dirItems 配列の要素数
  - dirItemsSha256 
  - rootDirIndex:Int32 // ルートディレクトリ要素リストの開始位置。dirItems要素のインデクス。
  - rootDirSize:Int32 // ルートディレクトリ要素リストの数。
- headerSha256: byte[32]
- version:Int32 // データスキーマバージョン。現状は1のみが有効。
```

- 圧縮アルゴリズムは LZ4固定。圧縮パラメータはpluginやcliに指定可能
- ファイル構造はメタデータ後置パターン。
- dirItemsはフラットな配列にしているが、ディレクトリごとにどの範囲を参照するかが異なる

## 出力時のロジック
- 一時ファイルをr/wモードで開き先頭に移動

### magic の出力
- MAGIC を出力
### コンテンツの出力
- 出力ファイルの現在の位置を compressedDataStart にメモしておく
- ファイルツリーを文字コード順ソートして深さ優先巡回
- ファイルのSHA256ハッシュを取得
- 出力済みデータの(SHA256 to metadataIndex)マップに既に要素があれば、(filePath to metadataIndex) マップを更新してcontinue
- インクリメンタル更新の場合、前回のアーカイブからコンテンツSHA256が一致するものを探してあれば圧縮工程をスキップ。 compressedData をコピー、metadataListに要素を追加、 (SHA256 to metadataIndex)マップと (filePath to metadataIndex) マップを更新
- 上記のいずれでもなければ データを圧縮してファイルに書き込み、 metadataListに要素を追加、 (SHA256 to metadataIndex)マップと (filePath to metadataIndex) マップを更新

### メタデータの出力
- 出力ファイルの現在の位置を dirItemsStart にメモしておく
- metadataList を順に書き出し、つつ (metadataIndex to metadataStart) リストを構築
- contentMetaStartから出力ファイルの現在の位置までの内容を contentMetaSha256 に計算しておく
- 再び出力ファイル末端にseek
- 全部終わったら (filePath to metadataStart) を計算して、(filePath to metadataIndex) と(metadataIndex to metadataStart) は捨てていい

### names の出力
- 出力ファイルの現在の位置を namesStart にメモしておく
- ファイルツリーを文字コード順ソートして深さ優先巡回
- 登場した名前(pathではなくディレクトリエントリ。不正なUTF-8チェックしてNGならエラー)をまだ書いていなければ pair(nameBytesLength,nameBytes[nameBytesLength]) を書き (name to nameOffset) に追加する。
- namesStartから出力ファイルの現在の位置までの内容を namesSha256 に計算しておく
- 再び出力ファイル末端にseek

## dirItems の出力
- 出力ファイルの現在の位置を dirItemsStart にメモしておく
- フォルダツリーを文字コード順ソートして深さ優先巡回
  フォルダ内エントリに以下の計算を行う
  - dirItemsに書いた数をメモしておく
  - フォルダ内エントリの (name to nameOffset) を読む
  - フォルダ内ファイルの (filePath to metadataStart) を読み、FileItem を書く
  - フォルダ内ディレクトリの (dirPath to pair(dirIndex,dirSize))を読み、DirectoryItemを書く
  - 現在のフォルダの文の (dirPath to pair(dirIndex,dirSize)) を更新する
- dirItemsCount をメモする
- dirItemsStart から出力ファイルの現在の位置までの内容を dirItemsSha256 に計算しておく
## header の出力
- 出力ファイルの現在の位置を headerStart にメモしておく
- メモしておいた各種の値を出力する
- headerStart から出力ファイルの現在の位置までの内容を headerSha256 に計算しておく
## headerSha256の出力
- headerSha256 を出力
## version の出力
- データスキーマバージョンを出力する

# 実装状況

## 達成したこと

- Gradle 4.4.1 の初期雛形を Gradle 8.10 / Kotlin 2.4.10 のマルチプロジェクト構成へ更新した。
- `common` にアーカイブのエンコード・デコード、Okio による SHA-256、`dev.kona.resource.lz4.Lz4Codec` の Buffer callback 抽象化、重複コンテンツ参照、末尾ヘッダーと各セクションの検証を実装した。
- `common` の `linuxX64Main` に `com.ensody.nativebuilds:lz4:1.10.0.2` を接続し、LZ4 frame の圧縮・展開を Native API で実装した。JVM は Gradle Plugin/CLI のビルド補助用途に限定している。
- `plugin` に `konaResource` 拡張、リソース走査、アーカイブ生成、既存アーカイブからの圧縮データ再利用、GNU assembler による ELF `.o` 生成を実装した。
- Kotlin/Native の Linux/x64 バイナリに対して、生成したオブジェクトを linker option として追加する処理を実装した。
- `reader` に `dlsym` で埋め込みシンボルを探索する Linux/x64 Reader と、ファイル列挙・prefix 検索・ハッシュ・サイズ・入力ストリーム API を実装した。
- `cli` に `pack`、`list`、`extract` コマンドを実装した。
- `sample1` の利用例と、共通アーカイブのラウンドトリップ・SHA-256 のテストを追加した。

## 困ったこと・制約

- 追加時点のリポジトリには `settings.gradle` と Gradle wrapper 以外の実装がなかったため、モジュールと API は仕様書から新規作成した。
- 既存の Gradle 4.4.1 wrapper は Java 21 と Kotlin Multiplatform の現行構成に対応しないため、Gradle 8.10 に更新した。
- Kotest は JVM 専用依存を commonTest に置くと Linux/x64 Native テストの依存解決に失敗するため、commonTest は `kotlin.test`、Kotest は JVM テスト用依存に限定した。
- この環境では Kotlin/Native の `expect`/`actual` クラスが Beta 警告を出す。Reader の Native コンパイル自体は成功している。
- LZ4 は独自実装せず、NativeBuilds の LZ4 static library と cinterop を利用している。
- プラグインを同一マルチプロジェクト内の `sample1` から plugin marker 経由で直接解決するには、公開済み Maven リポジトリまたは composite build が必要なため、sample1 のビルドスクリプトでは Reader のコンパイル例までを定義している。公開後は README の plugin 適用例で接続する。

## 検証結果

- `./gradlew :common:jvmTest` 成功。
- `./gradlew :reader:compileKotlinLinuxX64` 成功。
- `./gradlew :plugin:validatePlugins` 成功。
- `./gradlew build` 成功。
- CLI の pack/list/extract を sample1 のリソースで実行し、成功。

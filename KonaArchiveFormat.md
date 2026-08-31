# KonaArchive file format

## version 2 ファイル構造

```
- MAGIC 0x0123CDEF
- compressedData: byte[contentMeta][*] // contentMeta が参照する圧縮データの領域
- contentMeta[contentCount] // 1要素 76 bytes
  - compressedStart: Int32 // compressedData 開始位置のファイル先頭からのバイトオフセット
  - compressedDigest: Byte[32]
  - compressedSize: Int32
  - uncompressedDigest: Byte[32]
  - uncompressedSize: Int32
- names: pair(nameBytesLength:Int32, nameBytes[nameBytesLength])[*]
   - list of pair(nameBytesLength:Int32, nameBytes[nameBytesLength])
   - nameBytes はUTF-8エンコード
- dirItems[dirItemsCount] // 1要素 12 bytes
  **フォルダごとに** 文字コード順にソート済み。bsearch可能
  要素は以下の2種類
  - FileItem(
        // names要素のファイル先頭からのバイトオフセット
        nameOffset: Int32
        // contentMeta要素のファイル先頭からのバイトオフセット
        // 最上位ビットが0であることを示す (FileItem)
        entryOffset: Int32
        reserved: Int32  // 未使用。常に0
    )
  - DirectoryItem(
        nameOffset: Int32 // names要素のファイル先頭からのバイトオフセット
        // dirItemsのインデクス or 0x80000000
        // 最上位ビットが1であることが DirectoryItem であることを示す
        storedDirIndex :Int32 = dirIndex | 0x80000000
        dirSize: Int32 // フォルダ中の要素数
        // Note: 要素数0のディレクトリは空ディレクトリなので dirIndex は常に0となる

    )
- header
  - compressedDataStart:Int32 // compressedData先頭の、ファイル先頭からのバイトオフセット
  - contentMetaStart:Int32 // contentMeta先頭の、ファイル先頭からのバイトオフセット
  - contentMetaDigest: Byte[32] // contentMeta 全体のハッシュダイジェスト
  - namesStart:Int32 // names先頭の、ファイル先頭からのバイトオフセット
  - namesDigest: Byte[32] // names 全体のハッシュダイジェスト
  - dirItemsStart:Int32 // dirItems先頭の、ファイル先頭からのバイトオフセット
  - dirItemsCount:Int32 // dirItems 配列の要素数
  - dirItemsDigest: Byte[32] // dirItems 全体のハッシュダイジェスト
  - rootDirIndex:Int32 // ルートディレクトリ要素リストの開始位置。dirItems要素のインデクス。
  - rootDirSize:Int32 // ルートディレクトリ要素リストの要素数。
- headerDigest: Byte[32]
- version: Int32 // データスキーマバージョン。現状は1のみが有効。
```

- ファイル構造はメタデータ後置パターン。
- Int32 はすべて little-endian で格納する。
- 圧縮アルゴリズムは LZ4 固定
- ダイジェストアルゴリズムはバージョン2以降は BLAKE3-256、バージョン1はSHA-256。
- contentCount は重複排除後のユニークなコンテンツ数。
- dirItemsはフラットな配列にしているが、ディレクトリごとにどの範囲を参照するかが異なる

## version 1 =>2 ファイル構造とマイグレーション

version2とversion1の違いの違いは以下の通り。
- ダイジェストアルゴリズムはバージョン2以降は BLAKE3-256、バージョン1はSHA-256。

マイグレーションは以下のようにした。
- pluginやcliは常にversion2を出力する。
- cliは旧バージョンのファイルを読める。SHA-256ダイジェスト計算は引き続きJVM実装のものを使う。
- LinuxX64でcommonモジュールからversion1のアーカイブを読む時はOpenSSLではなく代替実装を使う。

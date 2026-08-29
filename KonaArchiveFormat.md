# KonaArchive format description

## ファイル構造

```
- MAGIC 0x0123CDEF
- compressedData: byte[contentMeta][*] // contentMeta が参照する圧縮データの領域
- contentMeta[contentCount] // 1要素 76 bytes
  - compressedStart: Int32 // compressedData 開始位置のファイル先頭からのバイトオフセット
  - compressedSha256: Byte[32]
  - compressedSize: Int32
  - uncompressedSha256: Byte[32]
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
  - contentMetaSha256: Byte[32] // contentMeta 全体のSHA256ハッシュ
  - namesStart:Int32 // names先頭の、ファイル先頭からのバイトオフセット
  - namesSha256: Byte[32] // names 全体のSHA256ハッシュ
  - dirItemsStart:Int32 // dirItems先頭の、ファイル先頭からのバイトオフセット
  - dirItemsCount:Int32 // dirItems 配列の要素数
  - dirItemsSha256: Byte[32] // dirItems 全体のSHA256ハッシュ
  - rootDirIndex:Int32 // ルートディレクトリ要素リストの開始位置。dirItems要素のインデクス。
  - rootDirSize:Int32 // ルートディレクトリ要素リストの要素数。
- headerSha256: Byte[32]
- version: Int32 // データスキーマバージョン。現状は1のみが有効。
```

- contentCount は重複排除後のユニークなコンテンツ数。
- ファイル構造はメタデータ後置パターン。
- Int32 はすべて little-endian で格納する。
- dirItemsはフラットな配列にしているが、ディレクトリごとにどの範囲を参照するかが異なる
- 圧縮アルゴリズムは LZ4 固定

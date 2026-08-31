# KonaArchive file format

## Version 2 File Structure

```
- MAGIC 0x0123CDEF
- compressedData: byte[contentMeta][*] // Region of compressed data referenced by contentMeta
- contentMeta[contentCount] // 76 bytes per element
  - compressedStart: Int32 // Byte offset from the beginning of the file where compressedData starts
  - compressedDigest: Byte[32]
  - compressedSize: Int32
  - uncompressedDigest: Byte[32]
  - uncompressedSize: Int32
- names: pair(nameBytesLength:Int32, nameBytes[nameBytesLength])[*]
   - list of pair(nameBytesLength:Int32, nameBytes[nameBytesLength])
   - nameBytes is encoded in UTF-8
- dirItems[dirItemsCount] // 12 bytes per element
   **Sorted in character-code order for each folder; binary-searchable.**
   There are two kinds of elements:
  - FileItem(
        // Byte offset from the beginning of the file to a names element
        nameOffset: Int32
        // Byte offset from the beginning of the file to a contentMeta element
        // The most significant bit is 0 (FileItem)
        entryOffset: Int32
        reserved: Int32  // Unused; always 0
    )
  - DirectoryItem(
         nameOffset: Int32 // Byte offset from the beginning of the file to a names element
         // dirItems index or 0x80000000
         // The most significant bit is 1 (DirectoryItem)
        storedDirIndex :Int32 = dirIndex | 0x80000000
         dirSize: Int32 // Number of elements in the folder
         // Note: An empty directory has no elements, so dirIndex is always 0.

    )
- header
  - compressedDataStart:Int32 // Byte offset from the beginning of the file to compressedData
  - contentMetaStart:Int32 // Byte offset from the beginning of the file to contentMeta
  - contentMetaDigest: Byte[32] // Hash digest of all contentMeta elements
  - namesStart:Int32 // Byte offset from the beginning of the file to names
  - namesDigest: Byte[32] // Hash digest of all names elements
  - dirItemsStart:Int32 // Byte offset from the beginning of the file to dirItems
  - dirItemsCount:Int32 // Number of elements in the dirItems array
  - dirItemsDigest: Byte[32] // Hash digest of all dirItems elements
  - rootDirIndex:Int32 // Starting index of the root directory element list in dirItems
  - rootDirSize:Int32 // Number of elements in the root directory element list
- headerDigest: Byte[32]
- version: Int32 // Data schema version; currently versions 1 and 2 are valid.
```

- The file structure follows a metadata-at-the-end pattern.
- All Int32 values are stored in little-endian order.
- The compression algorithm is fixed to LZ4.
- The digest algorithm is BLAKE3-256 for version 2 and later, and SHA-256 for version 1.
- contentCount is the number of unique contents after deduplication.
- dirItems is a flat array, but each directory references a different range within it.

## Version 1 to 2 File Structure and Migration

The difference between version 2 and version 1 is as follows:
- The digest algorithm is BLAKE3-256 for version 2 and later, and SHA-256 for version 1.

Migration is handled as follows:
- The plugin and CLI always output version 2.
- The CLI can read files from older versions. SHA-256 digest calculation continues to use the JVM implementation.
- When the common module reads a version 1 archive on Linux x64, it uses an alternative implementation instead of OpenSSL.

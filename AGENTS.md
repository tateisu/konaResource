
# 禁止事項
- CI関連の情報を文書に記載しない

# プロジェクトの目標
 Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin と
 アプリからそれを使うライブラリの2つを提供する

# 技術スタック
- Compatibility guide for Kotlin Multiplatform https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#version-compatibility の新しい方の構成
- Gradle バージョンカタログ
- okio
- Kotest
- lz4 cinterop or lz4-java

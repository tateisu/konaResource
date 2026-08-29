
# プロジェクトの目標
Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin と
アプリからそれを使うライブラリの2つを提供する

# 技術スタック
- Compatibility guide for Kotlin Multiplatform https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#version-compatibility の新しい方の構成
- Gradle バージョンカタログ
- okio
- Kotest
- lz4 cinterop or lz4-java

# プロジェクト構成
## ルートプロジェクト
- ルートプロジェクトは何もしない。ビルドターゲットもない

## common モジュール
- リソースアーカイブの共通コード

## plugin モジュール
- Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin

## reader モジュール
- 自身の .kexe に埋め込まれたリソースを読み出すライブラリ
- ビルドターゲットは Linux/x64 

## sample1 モジュール
- 上記 plugin モジュール, reader モジュールを使ってリソースにアクセスするアプリ

## cli モジュール
- リソースアーカイブを操作するCLIアプリ
- JVM


# 困ったこと・制約

- plugin は未公開のため、`sample1` は `settings.gradle.kts` の composite build で `plugin-build` を参照している。

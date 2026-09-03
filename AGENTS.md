# 禁止事項
- CI関連の情報を文書に記載しない
- ./gradlew build はリリースビルドを含むが、Kotlin/Nativeのそれはかなり重いのでなるべく避ける。特にtestモジュールはデバッグバイナリを作成してそれを動かすのが良い
- GitHub Workflow の構築で、最初から全ホストオーケストレーションを試すのはやめて。検証をホスト別に行い、最後にオーケストレーションを組んで。
- workflowResult フォルダを散らかすな。
  - ちゃんと workflowResult/{hostArch}/ に収集結果を置け。
  - {hostArch}-new やら {hostArch}-build-xxxx やら作られても最新の結果を自動収集できない。

# 推奨事項
- 作業が一段落したらCHANGELOG.md を更新する

# 資料
- `README.md` ユーザ向け説明
- `KonaArchiveFormat.md` KonaArchive file format
- `BuildMatrixJni.md` Build Matrix for `commonJni` module

# OSS.md 編集スタイル
- Runtime Dependency の確認対象はsample2とempty。
  - sample1は現在の実装ではなく公開済みアーティファクトを参照してしまう。
- セクションごとのソフトウェア一覧はアルファベット順(case insensitive)に並べる。
- ソフトウェアごとの箇条書きへ勝手に見出しを追加しない。

# Kotlin/Native ビルドホスト
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildHost.kt
- Kotlin/Native の制約なので、ビルド環境も自然とこのホスト種別に制約される

# Kotlin/Native ビルドターゲット
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildTarget.kt
- AndroidやiOSにはそれぞれ既定のリソースシステムがあるので、このプロジェクトの需要はなさそう

# JNI ビルドターゲット
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/JniBuildTarget.kt
- JNIのクロスビルド用に jdk/{arch}/ にJDKを置く場合は https://learn.microsoft.com/ja-jp/java/openjdk/download#openjdk-21 から取得する。
- jdk/{arch}/include/jni.h が存在するならそれを使い、
- またはビルドターゲットとビルドホストのアーキが一致するならGradleが使ってるjavaのホームディレクトリの/include/jni.h を存在確認してそれを使う
- どちらもないならそのビルドターゲットはavailableではないとみなす

# ビルドマトリクス(test,benchmark,sample2,empty)
`BuildMatrixNativeBinary.md`


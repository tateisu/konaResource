# 禁止事項
- CI関連の情報を文書に記載しない
- ./gradlew build はリリースビルドを含むが、Kotlin/Nativeのそれはかなり重いのでなるべく避ける。特にtestモジュールはデバッグバイナリを作成してそれを動かすのが良い

# 推奨事項
- 作業が一段落したらCHANGELOG.md を更新する

# 資料
- `README.md` ユーザ向け説明
- `KonaArchiveFormat.md` KonaArchive file format

# OSS.md 編集スタイル
- Runtime Dependency の確認対象はsample2とempty。
  - sample1は現在の実装ではなく公開済みアーティファクトを参照してしまう。
- セクションごとのソフトウェア一覧はアルファベット順(case insensitive)に並べる。
- ソフトウェアごとの箇条書きへ勝手に見出しを追加しない。

# Kotlin/Native ビルドホスト
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildHost.kt
- この一覧にないビルドホストはサポートしない

# Kotlin/Native ビルドターゲット
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildTarget.kt

# JNI ビルドターゲット
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/JniBuildTarget.kt
- JNIのクロスビルド用に jdk/{arch}/ にJDKを置く場合は https://learn.microsoft.com/ja-jp/java/openjdk/download#openjdk-21 から取得する。
- jdk/{arch}/include/jni.h が存在するならそれを使い、
- またはビルドターゲットとビルドホストのアーキが一致するならGradleが使ってるjavaのホームディレクトリの/include/jni.h を存在確認してそれを使う
- どちらもないならそのビルドターゲットはavailableではないとみなす

# ビルドマトリクス(JNI)
- KonaBuildHost と JniBuildTarget の組み合わせ表
- セルには未実施なら❓, ビルド成功したら ✅, 失敗ならマークダウン注釈参照を書く
- まず表をざっくり書き、セルは未実施の絵文字にする。
- その後、ホスト毎に Github workflow を使って試験してセルを埋めていく
- ホスト毎にクロスコンパイル環境の準備が必要
- 現在のワークツリーのjdkフォルダをどうにか Github workflowにアップロードできないか？
  - アップロード対象は include/ フォルダだけでも良いか？
- :common をビルドして、DLL入のJarファイルをダウンロードする
  - ダウンロード先は {rootProject}/workflowResult/{hostArch}/common.jar
  - ダウンロードしたjarに含まれるtargetアーキ用DLLをビルド成功とみなす。動作確認は別の機会
- 成功しなかったものはセルにマークダウン注釈参照を書き、注釈に失敗状況を説明する

TODO ここに表を書く

TODO ここに注釈で失敗状況を書く

# ビルドマトリクス(sample2)
- KonaBuildHost と KonaBuildTarget の組み合わせ表
- セルには未実施なら❓, ビルド成功したら ✅, 失敗ならマークダウン注釈参照を書く
- まず表をざっくり書き、セルは未実施の絵文字にする。
- その後、ホスト毎に Github workflow を使って試験してセルを埋めていく
- ホスト毎にcli,test,benchmark,sample2(クロスコンパイル)をビルドして、結果をダウンロードする
  - gradleを2回起動することになる。runSample2.sh を参考
  - 実行はしない。ビルドとダウンロードだけだ
- commonJniのビルドも動くが、ホストOSで動けばいいのでJNIクロスコンパイルの準備は不要なはず
- sample2 のバイナリ複数(クロスコンパイル)をビルドして、結果をダウンロードする
  - ダウンロード先の例 {rootProject}/workflowResult/{hostArch}/sample2なんたら
  - なんたらの部分は複数アーキで異なる？ jar もある？
  - ダウンロードできたsample2のターゲットarchごとにセルを成功とみなす
- 成功しなかったものはセルにマークダウン注釈参照を書き、注釈に失敗状況を説明する

TODO ここに表を書く

TODO ここに注釈で失敗状況を書く

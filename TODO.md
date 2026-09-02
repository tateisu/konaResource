# TODO
ひとつずつ順に実行して、終わったら `[x]` をつける

## クロスコンパイラから run_konan への移行
- see `RunKonan.md`
- JniBuildTarget をこれに合わせる
- ビルド可能アーキをコマンド１つで列挙できる
- commonJni や plugin の ビルド処理でこれを使えないか？

## testの改善
- testで日本語ファイル名/ディレクトリ名も確認したい。
  - CreateDirectoryAってANSIコードベージ依存じゃねーの？
  - ホストのコードページを仮定してるのはおかしい。
  - 大半のWindows PCのコードページはUTF-8ではない。

## benchmark CLI の 改善
- benchmark CLI の 定数をArgParserで指定可能にする
- デフォルト値も直すsmoke用になってて、warmupが少ないし試験時間も短すぎる

## AGENTS.md の ビルドマトリクスを作成する

## JniBuildTarget別に生成されたFatJarを試す
- ローカルで生成した common artifact は全アーキのDLLを含むはず
- test(jvm), cli(pack,extract), benchmark

## KonaBuildTarget別に生成された実行バイナリを試す
- empty, sample2, test, benchmark

## emptyとsample2のDLL依存差異をhost-targetペア毎に比較する

## JVMターゲットへのリソース埋め込み


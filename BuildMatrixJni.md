# ビルドマトリクス(JNI)

`JniBuildTarget` は Kotlin/Native の `run_konan` が扱う JNI ターゲットを表す。
`kotlinc-native -list-targets` の結果と JNI ヘッダの有無から、現在のホストで利用可能なターゲットを判定する。

| Host\Target | LinuxX64 | LinuxArm64 | MingwX64 | MacosX64 | MacosArm64 |
|-------------|----------|------------|----------|----------|------------|
| LinuxX64    | ✅       | ✅         | ✅       | ❌[A]    | ❌[A]      |
| MacosArm64  | ❓       | ❓         | ❓       | ✅       | ✅         |
| MacosX64    | ❓       | ❓         | ❓       | ✅       | ✅         |
| WindowsX64  | ❓       | ❓         | ✅       | ❓       | ❓         |

- ✅ は `run_konan` と JNI ヘッダが利用可能なターゲット
- ❌ は現在の Kotlin/Native distribution では利用できないターゲット。角括弧内は理由の注釈番号
- ❓ は host workflow での検証が未完了のターゲット

❌ セルの注釈:

- [A] LinuxX64 host の `kotlinc-native -list-targets` に macOS target が出現しないため。

利用可能なターゲットは以下で確認できる。

```shell
./gradlew :commonJni:listAvailableJniBuildTargets
```

## 複数ホストでビルドした JNI の収集

`commonJni` は `workflowResult/{hostArch}/common.jar` にある成果物から、現在のホストで利用できない JNI ライブラリを収集できる。

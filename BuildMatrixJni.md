# ビルドマトリクス (JNI)

| Host\Target | LinuxX64 | LinuxArm64 | MingwX64 | MacosX64         | MacosArm64       |
|-------------|----------|------------|----------|------------------|------------------|
| LinuxX64    | ✅       | ✅         | ✅       | ❌[^macos-cross] | ❌[^macos-cross] |
| MingwX64    | ✅       | ✅         | ✅       | ❌[^macos-cross] | ❌[^macos-cross] |
| MacosArm64  | ✅       | ✅         | ✅       | ✅               | ✅               |
| MacosX64    | ✅       | ✅         | ✅       | ✅               | ✅               |

- ✅ は `run_konan` とJNIヘッダがあればビルド可能。
- ❌ は ビルドできなかった。

[^macos-cross]: LinuxX64/MingwX64 host の `kotlinc-native -list-targets` に macOS target が含まれない

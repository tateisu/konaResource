#!/usr/bin/env bash
set -euo pipefail

output_file="$1"
java_home="$2"
script_directory="$(cd "$(dirname "$0")" && pwd)"
source_directory="$script_directory/src/main/c"
blake3_directory="$source_directory/blake3"
object_directory="$(dirname "$output_file")/objects"

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
    echo "blake3Jni currently supports Linux/x86-64 only" >&2
    exit 1
fi
if [[ ! -f "$java_home/include/jni.h" ]]; then
    echo "jni.h was not found under $java_home" >&2
    exit 1
fi

mkdir -p "$object_directory"
sources=(
    "$blake3_directory/blake3.c"
    "$blake3_directory/blake3_dispatch.c"
    "$blake3_directory/blake3_portable.c"
    "$blake3_directory/blake3_sse2_x86-64_unix.S"
    "$blake3_directory/blake3_sse41_x86-64_unix.S"
    "$blake3_directory/blake3_avx2_x86-64_unix.S"
    "$blake3_directory/blake3_avx512_x86-64_unix.S"
    "$source_directory/blake3_jni.c"
)
objects=()
for source in "${sources[@]}"; do
    object="$object_directory/$(basename "$source" | tr . _).o"
    cc -Wall -Wextra -O3 -fPIC -c "$source" \
        -I"$source_directory" \
        -I"$blake3_directory" \
        -I"$java_home/include" \
        -I"$java_home/include/linux" \
        -o "$object"
    objects+=("$object")
done

cc -shared -o "$output_file" "${objects[@]}"

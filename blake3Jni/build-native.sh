#!/usr/bin/env bash
set -euo pipefail

output_file="$1"
java_home="$2"
script_directory="$(cd "$(dirname "$0")" && pwd)"
source_directory="$script_directory/src/main/c"
blake3_directory="$source_directory/blake3"
object_directory="$(dirname "$output_file")/objects"

uname_s="$(uname -s)"
uname_m="$(uname -m)"

if [[ ! -f "$java_home/include/jni.h" ]]; then
    echo "jni.h was not found under $java_home" >&2
    exit 1
fi

mkdir -p "$object_directory"

common_sources=(
    "$blake3_directory/blake3.c"
    "$blake3_directory/blake3_dispatch.c"
    "$blake3_directory/blake3_portable.c"
    "$source_directory/blake3_jni.c"
)

if [[ "$uname_s" != "Linux" ]]; then
    echo "blake3Jni currently supports Linux only" >&2
    exit 1
fi

sources=("${common_sources[@]}")
cflags_common=("-Wall" "-Wextra" "-O3" "-fPIC")

if [[ "$uname_m" == "x86_64" ]]; then
    sources+=("$blake3_directory/blake3_sse2_x86-64_unix.S")
    sources+=("$blake3_directory/blake3_sse41_x86-64_unix.S")
    sources+=("$blake3_directory/blake3_avx2_x86-64_unix.S")
    sources+=("$blake3_directory/blake3_avx512_x86-64_unix.S")
    cflags_common+=("-mavx" "-mavx2" "-mavx512f" "-mavx512vl")
elif [[ "$uname_m" == "aarch64" ]]; then
    sources+=("$blake3_directory/blake3_neon.c")
    cflags_common+=("-DBLAKE3_USE_NEON=1")
else
    echo "Unsupported architecture: $uname_m (supported: x86_64, aarch64)" >&2
    exit 1
fi

include_paths="-I$source_directory"
include_paths+=" -I$blake3_directory"
include_paths+=" -I$java_home/include"

case "$uname_m" in
    x86_64)
        include_paths+=" -I$java_home/include/linux"
        ;;
    aarch64)
        include_paths+=" -I$java_home/include/linux"
        ;;
esac

objects=()
for source in "${sources[@]}"; do
    object="$object_directory/$(basename "$source").o"
    cc "${cflags_common[@]}" -c "$source" \
        $include_paths \
        -o "$object"
    objects+=("$object")
done

case "$uname_m" in
    x86_64)
        cc -shared -o "$output_file" "${objects[@]}"
        ;;
    aarch64)
        cc -shared -o "$output_file" "${objects[@]}"
        ;;
esac

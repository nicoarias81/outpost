# Compile-only check. No host or device execution, and no claim of an ARM APK build.
param()
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$ndk=Join-Path $project '.local/native/ndk/android-ndk-r28b'
$clang=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe'
$llama=Join-Path $project '.local/llama.cpp'
$native=Join-Path $project 'app/src/main/cpp'
$output=Join-Path $project '.local/portability'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$records=@()
foreach($target in @('x86_64-linux-android28','aarch64-linux-android28')) {
    foreach($name in @('cpu_caps.c','q2_dispatch.c','q2_kernel.c','q2_batch.c')) {
        $object=Join-Path $output "$target-$name.o"
        & $clang --target=$target -std=c11 -O2 -Wall -Wextra -Werror -ffp-contract=off `
            -isystem "$llama/ggml/include" -isystem "$llama/ggml/src" -isystem "$llama/ggml/src/ggml-cpu" `
            -c (Join-Path $native $name) -o $object
        if($LASTEXITCODE -ne 0) { throw "Compile failed: $target $name" }
        $records += [pscustomobject]@{target=$target;source=$name;compiled=$true}
    }
    $cpp=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
    & $cpp --target=$target -std=c++17 -O2 -Wall -Wextra -Werror -isystem "$llama/include" -isystem "$llama/ggml/include" `
        -c (Join-Path $native 'speculation.cpp') -o (Join-Path $output "$target-speculation.cpp.o")
    if($LASTEXITCODE -ne 0) { throw "Compile failed: $target speculation.cpp" }
    $records += [pscustomobject]@{target=$target;source='speculation.cpp';compiled=$true}
}
[pscustomobject]@{
    compiler='Android NDK r28b Clang'
    checks=$records
    limits='Object compilation only. No ARM execution, complete ARM library build, physical phone test, or VNNI kernel validation.'
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $project 'evidence/optimization/portability.json')
Write-Output 'PASS: 10 native objects compiled for Android x86_64 and arm64; no code executed.'

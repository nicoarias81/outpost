#!/usr/bin/env python3
"""Run a frozen manifest on the Android emulator; no host model inference."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import shutil
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--serial", default="emulator-5582")
    parser.add_argument("--manifest", default="eval/fixtures-v3.json")
    parser.add_argument("--model", choices=["qwen15", "bonsai17", "bonsai4"], default="bonsai4")
    parser.add_argument("--variants", default="baseline,candidate")
    parser.add_argument("--fixtures", default="")
    parser.add_argument("--max-tokens", type=int, default=96)
    parser.add_argument("--width", type=int, choices=[1, 2, 4, 8], default=4)
    parser.add_argument("--skip-install", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial): parser.error("Only emulator serials are allowed")
    if not 1 <= args.max_tokens <= 192: parser.error("Output budget must be 1..192")
    variants = args.variants.split(",")
    if not variants or len(set(variants)) != len(variants) or not set(variants) <= {"baseline", "candidate"}:
        parser.error("Variants must be baseline, candidate, or both without duplicates")
    adb = Path(args.sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    def command(*parts, binary=False, checked=True):
        return subprocess.run([str(adb), "-s", args.serial, *parts], capture_output=True,
                              text=not binary, encoding=None if binary else "utf-8", check=checked)
    def shell(*parts): return command("shell", *parts).stdout.strip()
    if shell("getprop", "ro.kernel.qemu") != "1" or shell("getprop", "ro.boot.qemu.avd_name") != "Outpost35":
        raise RuntimeError("The selected target is not the dedicated Outpost emulator")
    if shell("getprop", "sys.boot_completed") != "1": raise RuntimeError("Emulator boot is incomplete")
    if shell("getprop", "ro.product.cpu.abi") != "x86_64": raise RuntimeError("The current APK is x86_64")
    # Never enable networking, and fail rather than silently changing the test environment.
    state = {k: shell("settings", "get", "global", k) for k in ["airplane_mode_on", "wifi_on", "mobile_data"]}
    if state != {"airplane_mode_on": "1", "wifi_on": "0", "mobile_data": "0"}:
        raise RuntimeError("Set the dedicated emulator offline before running evaluation")
    powershell = shutil.which("pwsh") or shutil.which("powershell")
    if not powershell:
        raise RuntimeError("PowerShell is needed to verify the local build receipt")
    subprocess.run([powershell, "-NoProfile", "-File", str(ROOT / "eval/check-build.ps1")], check=True)
    manifest_path = (ROOT / args.manifest).resolve()
    manifest_bytes = manifest_path.read_bytes()
    manifest = json.loads(manifest_bytes.decode("utf-8-sig"))
    wanted = args.fixtures.split(",") if args.fixtures else [f["id"] for f in manifest["fixtures"]]
    if not set(wanted) <= {f["id"] for f in manifest["fixtures"]}: parser.error("Unknown fixture ID")
    run_id = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    output = ROOT / "evidence" / "runs" / run_id
    output.mkdir(parents=True, exist_ok=False)
    (output / "manifest.json").write_bytes(manifest_bytes)
    shutil.copy2(ROOT / ".local/build-receipt.json", output / "build-receipt.json")
    apks = {
        "dev.outpost.app": ROOT / "app/build/outputs/apk/debug/app-debug.apk",
        "dev.outpost.app.test": ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    }
    for package, apk in apks.items():
        if not apk.is_file(): raise RuntimeError("Build both APKs first")
        if not args.skip_install: command("install", "-r", str(apk))
        installed = shell("pm", "path", package)
        if not installed.startswith("package:") or "\n" in installed: raise RuntimeError("Unexpected installed APK layout")
        if shell("sha256sum", installed[8:]).split()[0] != digest(apk):
            raise RuntimeError(f"Installed {package} does not match the local build")
    # Read-only Git metadata; source hashes remain usable if host permissions hide .git.
    git = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True)
    source_hashes = {}
    for parent in ["app/src", "eval", "scripts"]:
        for path in (ROOT / parent).rglob("*"):
            if path.is_file() and "__pycache__" not in path.parts:
                source_hashes[path.relative_to(ROOT).as_posix()] = digest(path)
    for name in ["app/build.gradle", "build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.jar", "pdfbox-lock.json", "toolchain-lock.json", "project.json", "model-lock.json", "bonsai-lock.json", "judge-lock.json", "llama-revision.txt"]:
        source_hashes[name] = digest(ROOT / name)
    metadata = {
        "runId": run_id, "startedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "gitHead": git.stdout.strip() if git.returncode == 0 else None,
        "gitReadError": git.stderr.strip() if git.returncode else None,
        "apkSha256": {k: digest(v) for k, v in apks.items()}, "sourceSha256": source_hashes,
        "manifestSha256": hashlib.sha256(manifest_bytes).hexdigest(), "model": args.model,
        "serial": args.serial, "offlineState": state, "variants": variants,
        "selectedFixtures": wanted, "hostInference": False, "status": "preparing",
        "deviceFingerprint": shell("getprop", "ro.build.fingerprint"),
        "androidApi": shell("getprop", "ro.build.version.sdk"),
    }
    metadata_path = output / "run.json"
    def write_metadata(): metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    write_metadata()
    try:
        with zipfile.ZipFile(output / "source-snapshot.zip", "x", compression=zipfile.ZIP_DEFLATED) as archive:
            for relative, expected in source_hashes.items():
                data = (ROOT / relative).read_bytes()
                if hashlib.sha256(data).hexdigest() != expected:
                    raise RuntimeError("Source changed during provenance capture: " + relative)
                archive.writestr(relative, data)
        metadata["sourceSnapshotSha256"] = digest(output / "source-snapshot.zip")
    except Exception as error:
        metadata["status"] = "setup-failed"; metadata["error"] = str(error); write_metadata(); raise
    envelope = {"manifest": manifest, "manifestSha256": metadata["manifestSha256"],
                "selectedFixtures": wanted, "variants": variants, "model": args.model,
                "maxTokens": args.max_tokens, "matrixWidth": args.width}
    input_file = output / "input.json"
    input_file.write_text(json.dumps(envelope, ensure_ascii=False, indent=2), encoding="utf-8")
    device_dir = "/sdcard/Android/data/dev.outpost.app/files"
    try:
        command("shell", "mkdir", "-p", device_dir)
        command("push", str(input_file), device_dir + "/evaluation-" + run_id + ".json")
    except Exception as error:
        metadata["status"] = "setup-failed"; metadata["error"] = str(error); write_metadata(); raise
    metadata["status"] = "running"; write_metadata()
    print("Evaluation run:", run_id, flush=True)
    cmd = [str(adb), "-s", args.serial, "shell", "am", "instrument", "-w", "-e", "eval_run", run_id,
           "dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation"]
    status = 1
    try:
        with (output / "instrumentation.log").open("w", encoding="utf-8") as log:
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8")
            for line in process.stdout:
                print(line, end="", flush=True); log.write(line); log.flush()
            status = process.wait()
    finally:
        fetched = command("exec-out", "run-as", "dev.outpost.app", "cat", "files/evaluation/" + run_id + "/results.json",
                          binary=True, checked=False)
        if fetched.returncode == 0:
            (output / "results.json").write_bytes(fetched.stdout)
            result = json.loads(fetched.stdout.decode("utf-8"))
            metadata["status"] = "complete" if status == 0 and result.get("executionComplete") else "incomplete"
            if result.get("envelopeSha256") != digest(input_file) or result.get("manifestSha256") != metadata["manifestSha256"]:
                metadata["status"] = "input-integrity-failed"
        else:
            metadata["status"] = "retrieval-failed"
            (output / "retrieval-error.txt").write_bytes(fetched.stderr)
        metadata["finishedAtUtc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        write_metadata()
    print("Saved immutable run directory:", output, flush=True)
    return 0 if metadata["status"] == "complete" else 1

if __name__ == "__main__":
    sys.exit(main())

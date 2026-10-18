#!/usr/bin/env python3
"""Build a small Xposed APK with a Java 17 runtime; no Gradle or Android Studio needed."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
DEPENDENCIES = [
    ("platform.zip", "https://dl.google.com/android/repository/platform-35_r02.zip",
     "0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0", "platform"),
    ("build-tools.zip", "https://dl.google.com/android/repository/build-tools_r35_linux.zip",
     "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88", "build-tools"),
    ("ecj.jar", "https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar",
     "05cc22a24e7982970f63a405fc6c820bc80b806f27f3c5a6236fc475f8f7152b", None),
    ("xposed-api82.jar", "https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar",
     "f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25", None),
]


def run(*args):
    subprocess.run([str(x) for x in args], check=True)


def prepare(tools):
    tools.mkdir(parents=True, exist_ok=True)
    for name, url, expected, folder in DEPENDENCIES:
        archive = tools / name
        if not archive.exists():
            print("Downloading", name, flush=True)
            with urllib.request.urlopen(url, timeout=90) as source, archive.open("wb") as target:
                shutil.copyfileobj(source, target)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
            raise RuntimeError("Dependency checksum mismatch: " + name)
        if folder and not (tools / folder).exists():
            destination = (tools / folder).resolve()
            with zipfile.ZipFile(archive) as z:
                for item in z.infolist():
                    if not (destination / item.filename).resolve().is_relative_to(destination):
                        raise RuntimeError("Invalid archive path")
                z.extractall(destination)
    binaries = tools / "build-tools/android-15"
    for name in ["aapt2", "zipalign"]:
        (binaries / name).chmod(0o755)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tools", type=Path, default=ROOT / ".build-tools")
    parser.add_argument("--keystore", type=Path, default=ROOT / "signing/dev-signing.p12")
    parser.add_argument("--alias", default="dev")
    args = parser.parse_args()
    tools = args.tools.resolve()
    prepare(tools)
    java = shutil.which("java")
    keytool = shutil.which("keytool")
    if not java or not keytool:
        raise RuntimeError("Java 17+ runtime and keytool are required")
    sdk = tools / "build-tools/android-15"
    android = tools / "platform/android-35/android.jar"
    build = ROOT / "build"
    if build.exists():
        shutil.rmtree(build)
    (build / "classes").mkdir(parents=True)
    (build / "dex").mkdir()
    output = ROOT / "output"
    output.mkdir(exist_ok=True)
    run(sdk / "aapt2", "compile", "--dir", ROOT / "res", "-o", build / "resources.zip")
    run(sdk / "aapt2", "link", "-I", android, "--manifest", ROOT / "AndroidManifest.xml",
        "-A", ROOT / "assets", "-o", build / "base.apk", build / "resources.zip")
    source = sorted((ROOT / "src").rglob("*.java"))
    run(java, "-jar", tools / "ecj.jar", "-source", "1.8", "-target", "1.8",
        "-encoding", "UTF-8", "-classpath", os.pathsep.join([str(android), str(tools / "xposed-api82.jar")]),
        "-d", build / "classes", *source)
    classes = build / "classes.jar"
    with zipfile.ZipFile(classes, "w", zipfile.ZIP_DEFLATED) as z:
        for file in sorted((build / "classes").rglob("*.class")):
            z.write(file, file.relative_to(build / "classes").as_posix())
    run(java, "-cp", sdk / "lib/d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "34",
        "--lib", android, "--classpath", tools / "xposed-api82.jar", "--output", build / "dex", classes)
    with zipfile.ZipFile(build / "base.apk", "a", zipfile.ZIP_DEFLATED) as z:
        z.write(build / "dex/classes.dex", "classes.dex")
    run(sdk / "zipalign", "-f", "4", build / "base.apk", build / "aligned.apk")
    keystore = args.keystore.resolve()
    password = os.environ.get("RMSK_KEYSTORE_PASSWORD", "android")
    if not keystore.exists():
        if keystore != (ROOT / "signing/dev-signing.p12").resolve():
            raise RuntimeError("The specified private keystore does not exist")
        keystore.parent.mkdir(parents=True, exist_ok=True)
        run(keytool, "-genkeypair", "-noprompt", "-keystore", keystore,
            "-storetype", "PKCS12", "-storepass", password, "-keypass", password,
            "-alias", args.alias, "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
            "-dname", "CN=RedMagic Share Keep On Development, O=Local Development, C=CN")
    # Password travels through a task-specific environment variable, not shell interpolation.
    os.environ["RMSK_SIGNING_PASS"] = password
    version = ET.parse(ROOT / "AndroidManifest.xml").getroot().get("{http://schemas.android.com/apk/res/android}versionName")
    apk = output / ("RedMagicShareKeepOn-" + version + ".apk")
    run(java, "-jar", sdk / "lib/apksigner.jar", "sign", "--ks", keystore,
        "--ks-key-alias", args.alias, "--ks-pass", "env:RMSK_SIGNING_PASS",
        "--key-pass", "env:RMSK_SIGNING_PASS", "--v4-signing-enabled", "false", "--out", apk, build / "aligned.apk")
    run(java, "-jar", sdk / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", apk)
    run(sdk / "zipalign", "-c", "4", apk)
    with zipfile.ZipFile(apk) as z:
        assert z.read("assets/xposed_init").decode().strip() == "io.github.redmagic.sharekeepon.HookEntry"
        assert "classes.dex" in z.namelist()
        assert not any("de/robv/android/xposed" in n for n in z.namelist())
    print("APK:", apk)
    print("SHA-256:", hashlib.sha256(apk.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()

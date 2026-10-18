#!/usr/bin/env python3
"""Run Android API 35 launcher regression checks with Robolectric 4.14.1.

Requires a completed build.py run, Maven 3.9+, Java 17+, and Internet access
for the test dependencies and Android framework runtime. No phone is required.
"""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tools", type=Path, default=ROOT / ".build-tools")
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--maven-settings", type=Path)
    parser.add_argument("--legacy-classes", type=Path, help="Optional 1.0.0 class directory: expect the original NPE")
    args = parser.parse_args()
    tools = args.tools.resolve()
    work = tools / "startup-check"
    deps = work / "deps"
    classes = work / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    app = (args.legacy_classes or ROOT / "build/classes").resolve()
    if not app.is_dir():
        raise RuntimeError("Run build.py first")
    command = [args.maven, "-B", "-q", "-f", str(ROOT / "tests/pom.xml")]
    if args.maven_settings:
        command += ["-s", str(args.maven_settings.resolve())]
    subprocess.run(command + ["org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies",
                             "-DoutputDirectory=" + str(deps)], check=True)
    for aar in deps.glob("*.aar"):
        with zipfile.ZipFile(aar) as z:
            (deps / (aar.stem + "-classes.jar")).write_bytes(z.read("classes.jar"))
    android = tools / "platform/android-35/android.jar"
    dependencies = sorted(deps.glob("*.jar")) + [android]
    cp = os.pathsep.join(str(p) for p in [app] + dependencies)
    subprocess.run(["java", "-jar", str(tools / "ecj.jar"), "-source", "1.8", "-target", "1.8",
                    "-encoding", "UTF-8", "-classpath", cp, "-d", str(classes),
                    str(ROOT / "tests/StartupRegressionTest.java")], check=True)
    subprocess.run(["java", "-Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2",
                    "-DexpectOldCrash=" + str(bool(args.legacy_classes)).lower(), "-cp",
                    os.pathsep.join([str(classes), cp]), "org.junit.runner.JUnitCore",
                    "StartupRegressionTest"], check=True)


if __name__ == "__main__":
    main()

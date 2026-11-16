#!/usr/bin/env python3
"""Check HyperOS 4 classes and insets on a rooted device without installing live hooks.

Requires javac, jar, java, su, an Android SDK android.jar and an R8/D8 jar.
The Xposed shim records callbacks; Android framework objects are real.
"""

import argparse
from pathlib import Path
import shlex
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-jar", required=True, type=Path)
    parser.add_argument("--r8-jar", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    build = root / "app/build/tests/navbar"
    classes, dex = build / "classes", build / "dex"
    for directory in (classes, dex):
        shutil.rmtree(directory, ignore_errors=True)
        directory.mkdir(parents=True)

    sources = sorted((root / "tests/navbar/src").rglob("*.java"))
    sources.append(root / "app/src/main/java/moe/frisk/myhooks/systemui/HyperOs4NavBarHook.java")
    subprocess.run([
        "javac", "-encoding", "UTF-8", "--release", "11",
        "-cp", str(args.android_jar.resolve()), "-d", str(classes),
        *map(str, sources),
    ], check=True)
    compiled = build / "probe.jar"
    subprocess.run(["jar", "cf", str(compiled), "-C", str(classes), "."], check=True)
    subprocess.run([
        "java", "-cp", str(args.r8_jar.resolve()), "com.android.tools.r8.D8",
        "--min-api", "26", "--lib", str(args.android_jar.resolve()),
        "--output", str(dex), str(compiled),
    ], check=True)
    command = shlex.join([
        "env", f"CLASSPATH={dex / 'classes.dex'}", "app_process", "/system/bin",
        "moe.frisk.myhooks.systemui.NavBarProbe",
    ])
    result = subprocess.run(["su", "-c", command], text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT)
    report = root / "app/build/reports/navbar-adapt/device-probe.log"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(result.stdout)
    print(result.stdout, end="")
    result.check_returncode()


if __name__ == "__main__":
    main()

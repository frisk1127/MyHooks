"""Smoke-test the MyHooks release UI in an Android emulator."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True)


def screen():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/myhooks-window.xml")
    return ET.fromstring(adb("shell", "cat", "/data/local/tmp/myhooks-window.xml"))


def tap(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def find_text(root, label):
    return next((n for n in root.iter("node") if n.get("text") == label), None)


adb("install", "-r", sys.argv[1])
adb("shell", "wm", "size", "1080x2400")
adb("shell", "wm", "density", "320")
adb("shell", "am", "start", "-W", "-n", "moe.frisk.myhooks/.MainActivity")
root = screen()
for label in ("MyHooks", "System", "2", "Apps", "3"):
    assert find_text(root, label) is not None, (label, ET.tostring(root, encoding="unicode"))
rdp = "RDP 会话控件增强"
for attempt in range(4):
    title = find_text(root, rdp)
    if title is not None:
        break
    adb("shell", "input", "swipe", "520", "1950", "520", "650", "350")
    root = screen()
assert title is not None, "RDP is missing from the apps group"
assert any(n.get("content-desc") == rdp and n.get("checkable") == "true" for n in root.iter("node")), "RDP switch missing"
tap(title)
for _ in range(3):
    root = screen()
    if any("com.microsoft.rdc.androidx" in n.get("text", "") for n in root.iter("node")):
        break
    time.sleep(1)
else:
    raise AssertionError("RDP details did not show the Microsoft client package")
print("Grouped home, group counts, RDP toggle and target package verified")

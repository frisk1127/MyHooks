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


def bounds(node):
    left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    return left, top, right, bottom


def find_text(root, label):
    return next((n for n in root.iter("node") if n.get("text") == label), None)


adb("install", "-r", sys.argv[1])
adb("shell", "wm", "size", "1080x2400")
adb("shell", "wm", "density", "320")
adb("shell", "am", "start", "-W", "-n", "moe.frisk.myhooks/.MainActivity")
root = screen()
assert find_text(root, "MyHooks") is not None
for label, count in (("System", "4"), ("Apps", "3")):
    assert any([child.get("text") for child in node] == [label, count]
               for node in root.iter("node")), (label, ET.tostring(root, encoding="unicode"))
def rdp_switch(root):
    return next((n for n in root.iter("node") if n.get("checkable") == "true"
                 and n.get("content-desc", "").startswith("RDP")), None)


for attempt in range(5):
    switch = rdp_switch(root)
    if switch is not None:
        break
    print([(n.get("class"), n.get("text"), n.get("content-desc"), n.get("bounds"))
           for n in root.iter("node") if n.get("text") or n.get("content-desc")], flush=True)
    scroll = next(n for n in root.iter("node") if n.get("scrollable") == "true")
    left, top, right, bottom = bounds(scroll)
    x = str((left + right) // 2)
    adb("shell", "input", "swipe", x, str(bottom - 32), x, str(top + 32), "400")
    root = screen()
assert switch is not None, ET.tostring(root, encoding="unicode")
parents = {child: parent for parent in root.iter() for child in parent}
row = parents[switch]
while row.get("clickable") != "true":
    row = parents[row]
left, top, right, bottom = bounds(row)
adb("shell", "input", "tap", str((left + bounds(switch)[0]) // 2), str((top + bottom) // 2))
for _ in range(3):
    root = screen()
    if any("com.microsoft.rdc.androidx" in n.get("text", "") for n in root.iter("node")):
        break
    time.sleep(1)
else:
    raise AssertionError("RDP details did not show the Microsoft client package")
print("Grouped home, group counts, RDP toggle and target package verified")

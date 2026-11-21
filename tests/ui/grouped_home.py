"""Smoke-test the MyHooks settings and app picker in an Android emulator."""
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


def tap(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def wait_node(predicate):
    for _ in range(5):
        root = screen()
        node = next((n for n in root.iter("node") if predicate(n)), None)
        if node is not None:
            return node
    raise AssertionError(ET.tostring(root, encoding="unicode"))


def control(name):
    return wait_node(lambda n: n.get("resource-id") == "moe.frisk.myhooks:id/" + name)


def verify_app_picker():
    pkg = "moe.frisk.myhooks"

    def is_app(n):
        return n.get("checkable") == "true" and n.get("content-desc", "").endswith(pkg)

    tap(wait_node(lambda n: n.get("text") == "应用高刷与画质增强"))
    tap(control("app_search"))
    adb("shell", "input", "text", pkg)
    app = wait_node(is_app)
    was_selected = app.get("checked") == "true"
    if not was_selected:
        tap(app)
    tap(control("selected_apps_tab"))
    tap(wait_node(lambda n: is_app(n) and n.get("checked") == "true"))
    control("empty_list")
    tap(control("all_apps_tab"))
    app = wait_node(lambda n: is_app(n) and n.get("checked") == "false")
    if was_selected:
        tap(app)
    tap(control("clear_search"))
    assert control("app_search").get("text") != pkg
    tap(control("back"))
    wait_node(lambda n: n.get("text") == "MyHooks")


adb("install", "-r", sys.argv[1])
adb("shell", "wm", "size", "1080x2400")
adb("shell", "wm", "density", "320")
adb("shell", "am", "start", "-W", "-n", "moe.frisk.myhooks/.MainActivity")
root = screen()
assert find_text(root, "MyHooks") is not None
verify_app_picker()
root = screen()


def rdp_switch(root):
    return next((n for n in root.iter("node") if n.get("checkable") == "true"
                 and n.get("content-desc", "").startswith("RDP")), None)


expected_groups = {("System", "4"), ("Apps", "3")}
seen_groups = set()
for attempt in range(6):
    for node in root.iter("node"):
        labels = tuple(child.get("text") for child in node)
        if labels in expected_groups:
            seen_groups.add(labels)
    switch = rdp_switch(root)
    if switch is not None and seen_groups == expected_groups:
        break
    print([(n.get("class"), n.get("text"), n.get("content-desc"), n.get("bounds"))
           for n in root.iter("node") if n.get("text") or n.get("content-desc")], flush=True)
    scroll = next(n for n in root.iter("node") if n.get("scrollable") == "true")
    left, top, right, bottom = bounds(scroll)
    x = str((left + right) // 2)
    adb("shell", "input", "swipe", x, str(bottom - 32), x, str((top + bottom) // 2), "400")
    root = screen()
assert seen_groups == expected_groups, ("Missing group headers", expected_groups - seen_groups)
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

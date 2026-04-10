import glob
import os
import xml.etree.ElementTree as ET

ROOT = "/home/samujjwal/Developments/ghatana"
rows = []

pattern = os.path.join(ROOT, "**", "build", "test-results", "**", "TEST-*.xml")
for path in glob.glob(pattern, recursive=True):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        continue
    suite = root.attrib.get("name", "")
    module = os.path.relpath(path, ROOT).split("/build/test-results/")[0]
    for tc in root.findall("testcase"):
        node = tc.find("failure")
        node_type = "failure"
        if node is None:
            node = tc.find("error")
            node_type = "error"
        if node is None:
            continue
        msg = (node.attrib.get("message") or "").replace("\n", " ").strip()
        rows.append(
            {
                "module": module,
                "suite": suite,
                "class": tc.attrib.get("classname", ""),
                "test": tc.attrib.get("name", ""),
                "kind": node_type,
                "message": msg,
                "file": os.path.basename(path),
            }
        )

rows.sort(key=lambda r: (r["module"], r["class"], r["test"]))
if not rows:
    print("NO_FAILURES_FOUND")
else:
    for r in rows:
        print(
            f"{r['module']} | {r['class']} | {r['test']} | {r['kind']} | {r['message']} | {r['file']}"
        )


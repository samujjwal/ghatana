import xml.etree.ElementTree as ET
import sys
import glob

def process_file(file_path):
    print(f"--- File: {file_path} ---")
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        print(f"TestSuite: {root.get('name')} (Failures: {root.get('failures')}, Errors: {root.get('errors')})")
        for tc in root.findall('testcase'):
            issue = tc.find('failure') or tc.find('error')
            if issue is not None:
                print(f"Test: {tc.get('classname')}.{tc.get('name')}")
                print(f"Type: {issue.get('type')}")
                print(f"Message: {issue.get('message')}")
                stack = (issue.text or "").splitlines()[:25]
                print("Stacktrace (first 25 lines):")
                print("\n".join(stack))
                print("-" * 20)
    except Exception as e:
        print(f"Error: {e}")

files = sys.argv[1:]
for f in files:
    process_file(f)

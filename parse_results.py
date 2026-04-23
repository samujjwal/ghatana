import xml.etree.ElementTree as ET
import sys
import glob

def run(search_paths):
    count = 0
    for path in search_paths:
        for f in glob.glob(path):
            try:
                tree = ET.parse(f)
                root = tree.getroot()
                f_count = int(root.get('failures', 0))
                e_count = int(root.get('errors', 0))
                if f_count > 0 or e_count > 0:
                    count += 1
                    print(f"--- File: {f} ---")
                    print(f"TestSuite: {root.get('name')} (Failures: {f_count}, Errors: {e_count})")
                    for tc in root.findall('testcase'):
                        issue = tc.find('failure') or tc.find('error')
                        if issue is not None:
                            print(f"Test: {tc.get('classname')}.{tc.get('name')}")
                            print(f"Type: {issue.get('type')}")
                            print(f"Message: {issue.get('message')}")
                                                o                                                        t("           (first 25 li                                          ne                                                   print("-" * 20)
            except             except             ex
orchorchorchorchorchorchorchorchorchorchorchorchorc-resultsorchorchorchorchorchorchorchorchorchorchorchorep/servorchorchorchorchorchorchorchorchorchorchorcint(orchorchorchorchorchorchorcho
ooooooooooooooooooooooooooooooooooooooooooooooog ooooooooooooooooooooooooooooooooooooooooooooooog oooooooooooooooooooooooooooooooooooooooooooooooglureooooooooooooooooooooooooooooooooooooooooooooooog oooooal}")

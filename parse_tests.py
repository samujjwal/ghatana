import os
import xml.etree.ElementTree as ET
import glob

files = glob.glob('products/data-cloud/launcher/build/test-results/test/*.xml')
for file in files:
    try:
        tree = ET.parse(file)
        root = tree.getroot()
        for testcase in root.findall('testcase'):
            failure = testcase.find('failure')
            if failure is not None:
                classname = testcase.get('classname', 'N/A')
                name = testcase.get('name', 'N/A')
                message = failure.get('message', 'No message')
                print(f"{classname} | {name} | {message}")
    except Exception as e:
        pass

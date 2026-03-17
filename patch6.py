import re
path = "/Users/samujjwal/Development/ghatana/products/yappc/infrastructure/security/src/test/java/com/ghatana/yappc/infrastructure/security/SecurityScannerIntegrationTest.java"
with open(path, "r") as f:
    text = f.read()

# Pattern captures ruleId, Severity.XXXX, message ending before parenthesis or whitespace. Actually let's just match manually in code since it spans multilines.
pattern = r'new SecurityReport\.Finding\(\s*("[^"]+")\s*,\s*(SecurityReport\.Severity\.[A-Z]+)\s*,\s*("[^"]+")\s*\)'
text = re.sub(pattern, r'new SecurityReport.Finding(\1, \3, \2, "unknown")', text)

with open(path, "w") as f:
    f.write(text)

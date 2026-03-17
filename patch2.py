import re
with open("/Users/samujjwal/Development/ghatana/products/app-platform/kernel/audit-trail/src/test/java/com/ghatana/appplatform/audit/export/AuditEvidencePdfGeneratorTest.java", "r") as f:
    text = f.read()

text = text.replace("PDDocument.load(pdf)", "Loader.loadPDF(pdf)")
text = text.replace("PDDocument.load(baos.toByteArray())", "Loader.loadPDF(baos.toByteArray())")
text = text.replace("thenReturn(args[0], args[1], rest)", "thenReturn(args[0], args[1], java.util.Arrays.copyOf(rest, rest.length, Boolean[].class))")

with open("/Users/samujjwal/Development/ghatana/products/app-platform/kernel/audit-trail/src/test/java/com/ghatana/appplatform/audit/export/AuditEvidencePdfGeneratorTest.java", "w") as f:
    f.write(text)

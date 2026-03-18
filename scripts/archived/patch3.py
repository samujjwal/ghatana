import os
path = "/Users/samujjwal/Development/ghatana/products/app-platform/kernel/event-store/src/test/java/com/ghatana/appplatform/eventstore/replay/ProjectionRebuildEngineTest.java"
with open(path, "r") as f:
    text = f.read()

# find the split point
split_marker = "}\n\n\n/**\n * Unit tests for {@link ProjectionRebuildEngine}."

if split_marker in text:
    parts = text.split(split_marker)
    # remove the closing brace of the first class and the headers of the second class
    second_part = parts[1]
    # find where the second class methods start
    # it starts at @Test after projection
    test_idx = second_part.find("@Test")
    merged = parts[0] + "\n\n" + second_part[test_idx:]
    with open(path, "w") as f:
        f.write(merged)
        print("Merged successfully")
else:
    print("Could not find split marker")

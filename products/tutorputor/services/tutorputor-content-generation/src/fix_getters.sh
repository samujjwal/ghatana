#!/bin/bash
awk '
{
  while (match($0, /\.get([A-Z])([a-zA-Z0-9]*)\(\)/)) {
    char = substr($0, RSTART + 4, 1)
    rest = substr($0, RSTART + 5, RLENGTH - 7)
    char = tolower(char)
    $0 = substr($0, 1, RSTART - 1) "." char rest "()" substr($0, RSTART + RLENGTH)
  }
  print
}
' main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java > temp.java && mv temp.java main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java

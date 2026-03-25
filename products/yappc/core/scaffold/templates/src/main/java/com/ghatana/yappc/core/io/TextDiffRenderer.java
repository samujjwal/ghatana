package com.ghatana.yappc.core.io;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * TextDiffRenderer component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose TextDiffRenderer component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public final class TextDiffRenderer {

    private TextDiffRenderer() {
    }

    public static String render(String original, String revised) {
        List<String> origLines
                = original == null
                        ? Collections.emptyList()
                        : Arrays.asList(original.split("\r?\n", -1));
        List<String> revLines
                = revised == null
                        ? Collections.emptyList()
                        : Arrays.asList(revised.split("\r?\n", -1));

        Patch<String> patch = DiffUtils.diff(origLines, revLines);
        if (patch.getDeltas().isEmpty()) {
            return "(no changes)";
        }

        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (Delta<String> delta : patch.getDeltas()) {
            joiner.add(
                    "@@ "
                    + delta.getOriginal().getPosition()
                    + " -> "
                    + delta.getRevised().getPosition()
                    + " @@");
            delta.getOriginal().getLines().forEach(line -> joiner.add("- " + line));
            delta.getRevised().getLines().forEach(line -> joiner.add("+ " + line));
        }
        return joiner.toString();
    }
}

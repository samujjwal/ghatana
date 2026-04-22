/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DiffRenderer.

 * @doc.type class
 * @doc.purpose Handles diff renderer test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class DiffRendererTest {

    private DiffRenderer renderer;

    @BeforeEach
    void setUp() { // GH-90000
        renderer = new DiffRenderer(); // GH-90000
    }

    @Test
    @DisplayName("Render diff - identical texts [GH-90000]")
    void testRenderIdenticalTexts() { // GH-90000
        String original = "line1\nline2\nline3";
        String modified = "line1\nline2\nline3";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("--- original [GH-90000]"));
        assertTrue(diff.contains("+++ modified [GH-90000]"));
        assertTrue(diff.contains("  line1 [GH-90000]"));
        assertTrue(diff.contains("  line2 [GH-90000]"));
        assertTrue(diff.contains("  line3 [GH-90000]"));
        // Check that there are no addition or deletion markers (excluding the header lines) // GH-90000
        String[] lines = diff.split("\n [GH-90000]");
        for (String line : lines) { // GH-90000
            if (!line.startsWith("--- [GH-90000]") && !line.startsWith("+++ [GH-90000]")) {
                assertFalse(line.startsWith("+  [GH-90000]"), "Found unexpected addition: " + line);
                assertFalse(line.startsWith("-  [GH-90000]"), "Found unexpected deletion: " + line);
            }
        }
    }

    @Test
    @DisplayName("Render diff - line added [GH-90000]")
    void testRenderLineAdded() { // GH-90000
        String original = "line1\nline2";
        String modified = "line1\nline2\nline3";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("  line1 [GH-90000]"));
        assertTrue(diff.contains("  line2 [GH-90000]"));
        assertTrue(diff.contains("+ line3 [GH-90000]"));
    }

    @Test
    @DisplayName("Render diff - line removed [GH-90000]")
    void testRenderLineRemoved() { // GH-90000
        String original = "line1\nline2\nline3";
        String modified = "line1\nline2";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("  line1 [GH-90000]"));
        assertTrue(diff.contains("  line2 [GH-90000]"));
        assertTrue(diff.contains("- line3 [GH-90000]"));
    }

    @Test
    @DisplayName("Render diff - line changed [GH-90000]")
    void testRenderLineChanged() { // GH-90000
        String original = "line1\nline2\nline3";
        String modified = "line1\nmodified line2\nline3";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("  line1 [GH-90000]"));
        assertTrue(diff.contains("- line2 [GH-90000]"));
        assertTrue(diff.contains("+ modified line2 [GH-90000]"));
        assertTrue(diff.contains("  line3 [GH-90000]"));
    }

    @Test
    @DisplayName("Render diff - multiple changes [GH-90000]")
    void testRenderMultipleChanges() { // GH-90000
        String original = "line1\nline2\nline3\nline4";
        String modified = "line1\nmodified line2\nline3\nline5";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("  line1 [GH-90000]"));
        assertTrue(diff.contains("- line2 [GH-90000]"));
        assertTrue(diff.contains("+ modified line2 [GH-90000]"));
        assertTrue(diff.contains("  line3 [GH-90000]"));
        assertTrue(diff.contains("- line4 [GH-90000]"));
        assertTrue(diff.contains("+ line5 [GH-90000]"));
    }

    @Test
    @DisplayName("Render diff - empty original [GH-90000]")
    void testRenderEmptyOriginal() { // GH-90000
        String original = "";
        String modified = "line1\nline2";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("+ line1 [GH-90000]"));
        assertTrue(diff.contains("+ line2 [GH-90000]"));
    }

    @Test
    @DisplayName("Render diff - empty modified [GH-90000]")
    void testRenderEmptyModified() { // GH-90000
        String original = "line1\nline2";
        String modified = "";

        String diff = renderer.render(original, modified); // GH-90000

        assertNotNull(diff); // GH-90000
        assertTrue(diff.contains("- line1 [GH-90000]"));
        assertTrue(diff.contains("- line2 [GH-90000]"));
    }

    @Test
    @DisplayName("Render patch - empty list [GH-90000]")
    void testRenderPatchEmpty() { // GH-90000
        String result = renderer.renderPatch(List.of()); // GH-90000
        assertEquals("", result); // GH-90000
    }

    @Test
    @DisplayName("Render patch - null list [GH-90000]")
    void testRenderPatchNull() { // GH-90000
        String result = renderer.renderPatch(null); // GH-90000
        assertEquals("", result); // GH-90000
    }

    @Test
    @DisplayName("Render patch - with additions [GH-90000]")
    void testRenderPatchAdditions() { // GH-90000
        List<String> patches = List.of( // GH-90000
            "+added line",
            " unchanged line",
            "+another added line"
        );

        String result = renderer.renderPatch(patches); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.contains("[+] added line [GH-90000]"));
        assertTrue(result.contains("    unchanged line [GH-90000]"));
        assertTrue(result.contains("[+] another added line [GH-90000]"));
    }

    @Test
    @DisplayName("Render patch - with deletions [GH-90000]")
    void testRenderPatchDeletions() { // GH-90000
        List<String> patches = List.of( // GH-90000
            "-deleted line",
            " unchanged line",
            "-another deleted line"
        );

        String result = renderer.renderPatch(patches); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.contains("[-] deleted line [GH-90000]"));
        assertTrue(result.contains("    unchanged line [GH-90000]"));
        assertTrue(result.contains("[-] another deleted line [GH-90000]"));
    }

    @Test
    @DisplayName("Render patch - with hunk markers [GH-90000]")
    void testRenderPatchHunkMarkers() { // GH-90000
        List<String> patches = List.of( // GH-90000
            "@@ -1,3 +1,3 @@",
            " unchanged line",
            "-deleted line",
            "+added line"
        );

        String result = renderer.renderPatch(patches); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.contains("[@] @@ -1,3 +1,3 @@ [GH-90000]"));
        assertTrue(result.contains("    unchanged line [GH-90000]"));
        assertTrue(result.contains("[-] deleted line [GH-90000]"));
        assertTrue(result.contains("[+] added line [GH-90000]"));
    }

    @Test
    @DisplayName("Render patch - mixed content [GH-90000]")
    void testRenderPatchMixed() { // GH-90000
        List<String> patches = List.of( // GH-90000
            "@@ -1,5 +1,5 @@",
            " context line 1",
            "-old line",
            "+new line",
            " context line 2",
            "+added line"
        );

        String result = renderer.renderPatch(patches); // GH-90000

        assertNotNull(result); // GH-90000
        assertTrue(result.contains("[@] [GH-90000]"));
        assertTrue(result.contains("[+] [GH-90000]"));
        assertTrue(result.contains("[-] [GH-90000]"));
        assertTrue(result.contains("    context [GH-90000]"));
    }
}

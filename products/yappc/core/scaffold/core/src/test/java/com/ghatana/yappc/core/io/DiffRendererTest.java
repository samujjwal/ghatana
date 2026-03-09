/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
    void setUp() {
        renderer = new DiffRenderer();
    }

    @Test
    @DisplayName("Render diff - identical texts")
    void testRenderIdenticalTexts() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nline2\nline3";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("--- original"));
        assertTrue(diff.contains("+++ modified"));
        assertTrue(diff.contains("  line1"));
        assertTrue(diff.contains("  line2"));
        assertTrue(diff.contains("  line3"));
        // Check that there are no addition or deletion markers (excluding the header lines)
        String[] lines = diff.split("\n");
        for (String line : lines) {
            if (!line.startsWith("---") && !line.startsWith("+++")) {
                assertFalse(line.startsWith("+ "), "Found unexpected addition: " + line);
                assertFalse(line.startsWith("- "), "Found unexpected deletion: " + line);
            }
        }
    }

    @Test
    @DisplayName("Render diff - line added")
    void testRenderLineAdded() {
        String original = "line1\nline2";
        String modified = "line1\nline2\nline3";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("  line1"));
        assertTrue(diff.contains("  line2"));
        assertTrue(diff.contains("+ line3"));
    }

    @Test
    @DisplayName("Render diff - line removed")
    void testRenderLineRemoved() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nline2";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("  line1"));
        assertTrue(diff.contains("  line2"));
        assertTrue(diff.contains("- line3"));
    }

    @Test
    @DisplayName("Render diff - line changed")
    void testRenderLineChanged() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nmodified line2\nline3";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("  line1"));
        assertTrue(diff.contains("- line2"));
        assertTrue(diff.contains("+ modified line2"));
        assertTrue(diff.contains("  line3"));
    }

    @Test
    @DisplayName("Render diff - multiple changes")
    void testRenderMultipleChanges() {
        String original = "line1\nline2\nline3\nline4";
        String modified = "line1\nmodified line2\nline3\nline5";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("  line1"));
        assertTrue(diff.contains("- line2"));
        assertTrue(diff.contains("+ modified line2"));
        assertTrue(diff.contains("  line3"));
        assertTrue(diff.contains("- line4"));
        assertTrue(diff.contains("+ line5"));
    }

    @Test
    @DisplayName("Render diff - empty original")
    void testRenderEmptyOriginal() {
        String original = "";
        String modified = "line1\nline2";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("+ line1"));
        assertTrue(diff.contains("+ line2"));
    }

    @Test
    @DisplayName("Render diff - empty modified")
    void testRenderEmptyModified() {
        String original = "line1\nline2";
        String modified = "";
        
        String diff = renderer.render(original, modified);
        
        assertNotNull(diff);
        assertTrue(diff.contains("- line1"));
        assertTrue(diff.contains("- line2"));
    }

    @Test
    @DisplayName("Render patch - empty list")
    void testRenderPatchEmpty() {
        String result = renderer.renderPatch(List.of());
        assertEquals("", result);
    }

    @Test
    @DisplayName("Render patch - null list")
    void testRenderPatchNull() {
        String result = renderer.renderPatch(null);
        assertEquals("", result);
    }

    @Test
    @DisplayName("Render patch - with additions")
    void testRenderPatchAdditions() {
        List<String> patches = List.of(
            "+added line",
            " unchanged line",
            "+another added line"
        );
        
        String result = renderer.renderPatch(patches);
        
        assertNotNull(result);
        assertTrue(result.contains("[+] added line"));
        assertTrue(result.contains("    unchanged line"));
        assertTrue(result.contains("[+] another added line"));
    }

    @Test
    @DisplayName("Render patch - with deletions")
    void testRenderPatchDeletions() {
        List<String> patches = List.of(
            "-deleted line",
            " unchanged line",
            "-another deleted line"
        );
        
        String result = renderer.renderPatch(patches);
        
        assertNotNull(result);
        assertTrue(result.contains("[-] deleted line"));
        assertTrue(result.contains("    unchanged line"));
        assertTrue(result.contains("[-] another deleted line"));
    }

    @Test
    @DisplayName("Render patch - with hunk markers")
    void testRenderPatchHunkMarkers() {
        List<String> patches = List.of(
            "@@ -1,3 +1,3 @@",
            " unchanged line",
            "-deleted line",
            "+added line"
        );
        
        String result = renderer.renderPatch(patches);
        
        assertNotNull(result);
        assertTrue(result.contains("[@] @@ -1,3 +1,3 @@"));
        assertTrue(result.contains("    unchanged line"));
        assertTrue(result.contains("[-] deleted line"));
        assertTrue(result.contains("[+] added line"));
    }

    @Test
    @DisplayName("Render patch - mixed content")
    void testRenderPatchMixed() {
        List<String> patches = List.of(
            "@@ -1,5 +1,5 @@",
            " context line 1",
            "-old line",
            "+new line",
            " context line 2",
            "+added line"
        );
        
        String result = renderer.renderPatch(patches);
        
        assertNotNull(result);
        assertTrue(result.contains("[@]"));
        assertTrue(result.contains("[+]"));
        assertTrue(result.contains("[-]"));
        assertTrue(result.contains("    context"));
    }
}

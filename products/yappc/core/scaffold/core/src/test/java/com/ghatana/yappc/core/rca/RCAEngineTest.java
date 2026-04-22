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

package com.ghatana.yappc.core.rca;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RCAEngine pattern matching.

 * @doc.type class
 * @doc.purpose Handles rca engine test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class RCAEngineTest {

    private RCAEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new RCAEngine(); // GH-90000
    }

    @Test
    @DisplayName("Analyze null failure [GH-90000]")
    void testAnalyzeNull() { // GH-90000
        RCAResult result = engine.analyze(null); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Unknown", result.getMetadata().get("category [GH-90000]"));
        assertEquals("No failure information provided", result.getExplanation()); // GH-90000
    }

    @Test
    @DisplayName("Analyze empty failure [GH-90000]")
    void testAnalyzeEmpty() { // GH-90000
        RCAResult result = engine.analyze(" [GH-90000]");

        assertNotNull(result); // GH-90000
        assertEquals("Unknown", result.getMetadata().get("category [GH-90000]"));
    }

    @Test
    @DisplayName("Analyze compilation error - cannot find symbol [GH-90000]")
    void testAnalyzeCompilationErrorSymbol() { // GH-90000
        String failure = "error: cannot find symbol\n" +
                        "  symbol:   class MyClass\n" +
                        "  location: package com.example";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Compilation Error", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Missing dependency or incorrect import", result.getExplanation()); // GH-90000
        assertFalse(result.getFixSuggestions().isEmpty()); // GH-90000
        assertEquals("Check dependencies and import statements", // GH-90000
                    result.getFixSuggestions().get(0).getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Analyze compilation error - package does not exist [GH-90000]")
    void testAnalyzeCompilationErrorPackage() { // GH-90000
        String failure = "error: package com.example.missing does not exist\n" +
                        "import com.example.missing.MyClass;";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Compilation Error", result.getMetadata().get("category [GH-90000]"));
        assertTrue(result.getExplanation().contains("Missing dependency [GH-90000]"));
    }

    @Test
    @DisplayName("Analyze null pointer exception [GH-90000]")
    void testAnalyzeNullPointer() { // GH-90000
        String failure = "java.lang.NullPointerException\n" +
                        "    at com.example.MyClass.myMethod(MyClass.java:42)\n" + // GH-90000
                        "    at com.example.Main.main(Main.java:10)"; // GH-90000

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Null Pointer", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Null reference accessed", result.getExplanation()); // GH-90000
        assertEquals("Add null checks or ensure proper initialization", // GH-90000
                    result.getFixSuggestions().get(0).getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Analyze class not found [GH-90000]")
    void testAnalyzeClassNotFound() { // GH-90000
        String failure = "java.lang.ClassNotFoundException: com.example.MyClass\n" +
                        "    at java.net.URLClassLoader.findClass(URLClassLoader.java:382)"; // GH-90000

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Class Not Found", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Missing class in classpath", result.getExplanation()); // GH-90000
        assertEquals("Verify dependencies and build configuration", // GH-90000
                    result.getFixSuggestions().get(0).getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Analyze NoClassDefFoundError [GH-90000]")
    void testAnalyzeNoClassDefFound() { // GH-90000
        String failure = "java.lang.NoClassDefFoundError: com/example/MyClass\n" +
                        "    at com.example.Main.main(Main.java:10)"; // GH-90000

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Class Not Found", result.getMetadata().get("category [GH-90000]"));
    }

    @Test
    @DisplayName("Analyze port conflict - address already in use [GH-90000]")
    void testAnalyzePortConflictAddress() { // GH-90000
        String failure = "java.net.BindException: Address already in use\n" +
                        "    at java.net.PlainSocketImpl.socketBind(Native Method)"; // GH-90000

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Port Conflict", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Port already in use by another process", result.getExplanation()); // GH-90000
        assertEquals("Stop conflicting process or use different port", // GH-90000
                    result.getFixSuggestions().get(0).getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Analyze port conflict - port already bound [GH-90000]")
    void testAnalyzePortConflictBound() { // GH-90000
        String failure = "Error: port 8080 is already bound to another process";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Port Conflict", result.getMetadata().get("category [GH-90000]"));
    }

    @Test
    @DisplayName("Analyze memory error - OutOfMemoryError [GH-90000]")
    void testAnalyzeMemoryError() { // GH-90000
        String failure = "java.lang.OutOfMemoryError: Java heap space\n" +
                        "    at java.util.Arrays.copyOf(Arrays.java:3332)"; // GH-90000

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Memory Error", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Insufficient memory allocated", result.getExplanation()); // GH-90000
        assertEquals("Increase heap size with -Xmx flag", // GH-90000
                    result.getFixSuggestions().get(0).getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Analyze permission denied [GH-90000]")
    void testAnalyzePermissionDenied() { // GH-90000
        String failure = "java.io.IOException: Permission denied\n" +
                        "    at java.io.UnixFileSystem.createFileExclusively(Native Method)"; // GH-90000

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Permission Denied", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Insufficient file system permissions", result.getExplanation()); // GH-90000
        assertEquals("Check file permissions and user access rights", // GH-90000
                    result.getFixSuggestions().get(0).getDescription()); // GH-90000
    }

    @Test
    @DisplayName("Analyze access denied [GH-90000]")
    void testAnalyzeAccessDenied() { // GH-90000
        String failure = "Error: Access is denied\n" +
                        "Cannot write to file: /protected/file.txt";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Permission Denied", result.getMetadata().get("category [GH-90000]"));
    }

    @Test
    @DisplayName("Analyze unknown failure [GH-90000]")
    void testAnalyzeUnknownFailure() { // GH-90000
        String failure = "Some random error that doesn't match any pattern";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertEquals("Unknown", result.getMetadata().get("category [GH-90000]"));
        assertEquals("Unable to determine root cause", result.getExplanation()); // GH-90000
    }

    @Test
    @DisplayName("RCA result has proper structure [GH-90000]")
    void testRCAResultStructure() { // GH-90000
        String failure = "NullPointerException at line 42";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertNotNull(result); // GH-90000
        assertNotNull(result.getAnalysisId()); // GH-90000
        assertNotNull(result.getTimestamp()); // GH-90000
        assertNotNull(result.getRootCause()); // GH-90000
        assertNotNull(result.getExplanation()); // GH-90000
        assertNotNull(result.getFixSuggestions()); // GH-90000
        assertNotNull(result.getMetadata()); // GH-90000
        assertTrue(result.getConfidence() > 0); // GH-90000
        assertTrue(result.getConfidence() <= 1.0); // GH-90000
    }

    @Test
    @DisplayName("Fix suggestions have proper priority [GH-90000]")
    void testFixSuggestionsPriority() { // GH-90000
        String failure = "cannot find symbol: MyClass";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertFalse(result.getFixSuggestions().isEmpty()); // GH-90000
        RCAResult.FixSuggestion suggestion = result.getFixSuggestions().get(0); // GH-90000
        assertNotNull(suggestion.getPriority()); // GH-90000
        assertEquals(RCAResult.FixSuggestion.Priority.MEDIUM, suggestion.getPriority()); // GH-90000
    }

    @Test
    @DisplayName("Fix suggestions have proper category [GH-90000]")
    void testFixSuggestionsCategory() { // GH-90000
        String failure = "ClassNotFoundException: MyClass";

        RCAResult result = engine.analyze(failure); // GH-90000

        assertFalse(result.getFixSuggestions().isEmpty()); // GH-90000
        RCAResult.FixSuggestion suggestion = result.getFixSuggestions().get(0); // GH-90000
        assertNotNull(suggestion.getCategory()); // GH-90000
        assertEquals(RCAResult.FixSuggestion.Category.CODE_FIX, suggestion.getCategory()); // GH-90000
    }
}

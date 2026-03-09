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
    void setUp() {
        engine = new RCAEngine();
    }

    @Test
    @DisplayName("Analyze null failure")
    void testAnalyzeNull() {
        RCAResult result = engine.analyze(null);
        
        assertNotNull(result);
        assertEquals("Unknown", result.getMetadata().get("category"));
        assertEquals("No failure information provided", result.getExplanation());
    }

    @Test
    @DisplayName("Analyze empty failure")
    void testAnalyzeEmpty() {
        RCAResult result = engine.analyze("");
        
        assertNotNull(result);
        assertEquals("Unknown", result.getMetadata().get("category"));
    }

    @Test
    @DisplayName("Analyze compilation error - cannot find symbol")
    void testAnalyzeCompilationErrorSymbol() {
        String failure = "error: cannot find symbol\n" +
                        "  symbol:   class MyClass\n" +
                        "  location: package com.example";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Compilation Error", result.getMetadata().get("category"));
        assertEquals("Missing dependency or incorrect import", result.getExplanation());
        assertFalse(result.getFixSuggestions().isEmpty());
        assertEquals("Check dependencies and import statements", 
                    result.getFixSuggestions().get(0).getDescription());
    }

    @Test
    @DisplayName("Analyze compilation error - package does not exist")
    void testAnalyzeCompilationErrorPackage() {
        String failure = "error: package com.example.missing does not exist\n" +
                        "import com.example.missing.MyClass;";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Compilation Error", result.getMetadata().get("category"));
        assertTrue(result.getExplanation().contains("Missing dependency"));
    }

    @Test
    @DisplayName("Analyze null pointer exception")
    void testAnalyzeNullPointer() {
        String failure = "java.lang.NullPointerException\n" +
                        "    at com.example.MyClass.myMethod(MyClass.java:42)\n" +
                        "    at com.example.Main.main(Main.java:10)";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Null Pointer", result.getMetadata().get("category"));
        assertEquals("Null reference accessed", result.getExplanation());
        assertEquals("Add null checks or ensure proper initialization",
                    result.getFixSuggestions().get(0).getDescription());
    }

    @Test
    @DisplayName("Analyze class not found")
    void testAnalyzeClassNotFound() {
        String failure = "java.lang.ClassNotFoundException: com.example.MyClass\n" +
                        "    at java.net.URLClassLoader.findClass(URLClassLoader.java:382)";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Class Not Found", result.getMetadata().get("category"));
        assertEquals("Missing class in classpath", result.getExplanation());
        assertEquals("Verify dependencies and build configuration",
                    result.getFixSuggestions().get(0).getDescription());
    }

    @Test
    @DisplayName("Analyze NoClassDefFoundError")
    void testAnalyzeNoClassDefFound() {
        String failure = "java.lang.NoClassDefFoundError: com/example/MyClass\n" +
                        "    at com.example.Main.main(Main.java:10)";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Class Not Found", result.getMetadata().get("category"));
    }

    @Test
    @DisplayName("Analyze port conflict - address already in use")
    void testAnalyzePortConflictAddress() {
        String failure = "java.net.BindException: Address already in use\n" +
                        "    at java.net.PlainSocketImpl.socketBind(Native Method)";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Port Conflict", result.getMetadata().get("category"));
        assertEquals("Port already in use by another process", result.getExplanation());
        assertEquals("Stop conflicting process or use different port",
                    result.getFixSuggestions().get(0).getDescription());
    }

    @Test
    @DisplayName("Analyze port conflict - port already bound")
    void testAnalyzePortConflictBound() {
        String failure = "Error: port 8080 is already bound to another process";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Port Conflict", result.getMetadata().get("category"));
    }

    @Test
    @DisplayName("Analyze memory error - OutOfMemoryError")
    void testAnalyzeMemoryError() {
        String failure = "java.lang.OutOfMemoryError: Java heap space\n" +
                        "    at java.util.Arrays.copyOf(Arrays.java:3332)";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Memory Error", result.getMetadata().get("category"));
        assertEquals("Insufficient memory allocated", result.getExplanation());
        assertEquals("Increase heap size with -Xmx flag",
                    result.getFixSuggestions().get(0).getDescription());
    }

    @Test
    @DisplayName("Analyze permission denied")
    void testAnalyzePermissionDenied() {
        String failure = "java.io.IOException: Permission denied\n" +
                        "    at java.io.UnixFileSystem.createFileExclusively(Native Method)";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Permission Denied", result.getMetadata().get("category"));
        assertEquals("Insufficient file system permissions", result.getExplanation());
        assertEquals("Check file permissions and user access rights",
                    result.getFixSuggestions().get(0).getDescription());
    }

    @Test
    @DisplayName("Analyze access denied")
    void testAnalyzeAccessDenied() {
        String failure = "Error: Access is denied\n" +
                        "Cannot write to file: /protected/file.txt";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Permission Denied", result.getMetadata().get("category"));
    }

    @Test
    @DisplayName("Analyze unknown failure")
    void testAnalyzeUnknownFailure() {
        String failure = "Some random error that doesn't match any pattern";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertEquals("Unknown", result.getMetadata().get("category"));
        assertEquals("Unable to determine root cause", result.getExplanation());
    }

    @Test
    @DisplayName("RCA result has proper structure")
    void testRCAResultStructure() {
        String failure = "NullPointerException at line 42";
        
        RCAResult result = engine.analyze(failure);
        
        assertNotNull(result);
        assertNotNull(result.getAnalysisId());
        assertNotNull(result.getTimestamp());
        assertNotNull(result.getRootCause());
        assertNotNull(result.getExplanation());
        assertNotNull(result.getFixSuggestions());
        assertNotNull(result.getMetadata());
        assertTrue(result.getConfidence() > 0);
        assertTrue(result.getConfidence() <= 1.0);
    }

    @Test
    @DisplayName("Fix suggestions have proper priority")
    void testFixSuggestionsPriority() {
        String failure = "cannot find symbol: MyClass";
        
        RCAResult result = engine.analyze(failure);
        
        assertFalse(result.getFixSuggestions().isEmpty());
        RCAResult.FixSuggestion suggestion = result.getFixSuggestions().get(0);
        assertNotNull(suggestion.getPriority());
        assertEquals(RCAResult.FixSuggestion.Priority.MEDIUM, suggestion.getPriority());
    }

    @Test
    @DisplayName("Fix suggestions have proper category")
    void testFixSuggestionsCategory() {
        String failure = "ClassNotFoundException: MyClass";
        
        RCAResult result = engine.analyze(failure);
        
        assertFalse(result.getFixSuggestions().isEmpty());
        RCAResult.FixSuggestion suggestion = result.getFixSuggestions().get(0);
        assertNotNull(suggestion.getCategory());
        assertEquals(RCAResult.FixSuggestion.Category.CODE_FIX, suggestion.getCategory());
    }
}

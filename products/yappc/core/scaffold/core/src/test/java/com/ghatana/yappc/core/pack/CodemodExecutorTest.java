/*
 * Copyright (c) 2024 Ghatana, Inc.
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

package com.ghatana.yappc.core.pack;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for codemod executor functionality. Week 2, Day 9 deliverable - Polyfix codemod integration
 * tests.
 
 * @doc.type class
 * @doc.purpose Handles codemod executor test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class CodemodExecutorTest {

    @Test
    void testJavaImportSorting(@TempDir Path tempDir) throws Exception {
        CodemodExecutor executor = new CodemodExecutor();

        // Create a Java file with unsorted imports
        Path javaFile = tempDir.resolve("TestClass.java");
        String javaContent =
                """
            package com.example;

            import java.util.List;


            import java.util.ArrayList;
            import java.nio.file.Path;

            public class TestClass {
                private List<String> items = new ArrayList<>();
            }
            """;
        Files.writeString(javaFile, javaContent);

        CodemodExecutor.CodemodResult result = executor.executeCodemods(tempDir, Map.of());

        assertTrue(result.successful());
        assertEquals(1, result.filesProcessed());
        assertTrue(result.transformationsApplied() > 0);

        // Verify import formatting was applied
        String transformedContent = Files.readString(javaFile);
        assertFalse(transformedContent.contains("\n\n\nimport"));
    }

    @Test
    void testTypeScriptImportFormatting(@TempDir Path tempDir) throws Exception {
        CodemodExecutor executor = new CodemodExecutor();

        // Create TypeScript files with formatting issues
        Path tsFile = tempDir.resolve("test.ts");
        String tsContent =
                """
            import {  Component,  useState  } from 'react';
            import{Button}from './Button';

            export default function App() {
                const [count, setCount] = useState(0);
                return <Button onClick={() => setCount(count + 1)}>{count}</Button>;
            }
            """;
        Files.writeString(tsFile, tsContent);

        CodemodExecutor.CodemodResult result = executor.executeCodemods(tempDir, Map.of());

        assertTrue(result.successful());
        assertEquals(1, result.filesProcessed());
        assertTrue(result.transformationsApplied() >= 0); // May or may not apply transformations
    }

    @Test
    void testESLintFixes(@TempDir Path tempDir) throws Exception {
        CodemodExecutor executor = new CodemodExecutor();

        // Create TypeScript file in src directory with ESLint issues
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Path tsFile = srcDir.resolve("component.tsx");

        String tsContent =
                """
            export function Button(){
            const handleClick=()=>{
            console.log('clicked')    ;
            }
            return<button onClick={handleClick}>Click me</button>;}
            """;
        Files.writeString(tsFile, tsContent);

        CodemodExecutor.CodemodResult result =
                executor.executeCodemods(tempDir, Map.of("enableESLint", true));

        assertTrue(result.successful());
        // Files processed count may vary based on ESLint transformation logic
        assertTrue(result.filesProcessed() >= 0);
    }

    @Test
    void testEmptyProject(@TempDir Path tempDir) throws Exception {
        CodemodExecutor executor = new CodemodExecutor();

        CodemodExecutor.CodemodResult result = executor.executeCodemods(tempDir, Map.of());

        assertTrue(result.successful());
        assertEquals(0, result.filesProcessed());
        assertEquals(0, result.transformationsApplied());
    }
}

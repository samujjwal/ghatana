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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Day 26: Tests for build log normalizer functionality 
 * @doc.type class
 * @doc.purpose Handles build log normalizer service test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class BuildLogNormalizerServiceTest {

    @Test
    void testGradleBuildLogDetection() throws IOException {
        String gradleLog =
                """
            > Task :core:compileJava
            > Task :core:classes UP-TO-DATE
            BUILD SUCCESSFUL in 5s
            """;

        BuildLogNormalizerService service = new BuildLogNormalizerService();
        NormalizedBuildLog normalized = service.normalize(gradleLog);

        assertEquals(NormalizedBuildLog.BuildTool.GRADLE, normalized.getTool());
        assertEquals(NormalizedBuildLog.BuildStatus.SUCCESS, normalized.getStatus());
        assertFalse(normalized.getTasks().isEmpty());
    }

    @Test
    void testNxBuildLogDetection() throws IOException {
        String nxLog =
                """
            > nx run webapp:build
            Successfully ran target build for project webapp
            """;

        BuildLogNormalizerService service = new BuildLogNormalizerService();
        NormalizedBuildLog normalized = service.normalize(nxLog);

        assertEquals(NormalizedBuildLog.BuildTool.NX, normalized.getTool());
        assertEquals(NormalizedBuildLog.BuildStatus.SUCCESS, normalized.getStatus());
    }

    @Test
    void testSupportedTools() {
        BuildLogNormalizerService service = new BuildLogNormalizerService();
        List<NormalizedBuildLog.BuildTool> supportedTools = service.getSupportedTools();

        assertTrue(supportedTools.contains(NormalizedBuildLog.BuildTool.GRADLE));
        assertTrue(supportedTools.contains(NormalizedBuildLog.BuildTool.NX));
    }

    @Test
    void testUnsupportedLogThrowsException() {
        String unknownLog = "This is not a recognized build log format";
        BuildLogNormalizerService service = new BuildLogNormalizerService();

        assertThrows(IOException.class, () -> service.normalize(unknownLog));
    }

    @Test
    void testEmptyLogThrowsException() {
        BuildLogNormalizerService service = new BuildLogNormalizerService();

        assertThrows(IOException.class, () -> service.normalize(""));
        assertThrows(IOException.class, () -> service.normalize((String) null));
    }
}

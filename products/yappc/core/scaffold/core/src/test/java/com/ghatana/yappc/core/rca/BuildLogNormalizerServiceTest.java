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
    void testGradleBuildLogDetection() throws IOException { // GH-90000
        String gradleLog =
                """
            > Task :core:compileJava
            > Task :core:classes UP-TO-DATE
            BUILD SUCCESSFUL in 5s
            """;

        BuildLogNormalizerService service = new BuildLogNormalizerService(); // GH-90000
        NormalizedBuildLog normalized = service.normalize(gradleLog); // GH-90000

        assertEquals(NormalizedBuildLog.BuildTool.GRADLE, normalized.getTool()); // GH-90000
        assertEquals(NormalizedBuildLog.BuildStatus.SUCCESS, normalized.getStatus()); // GH-90000
        assertFalse(normalized.getTasks().isEmpty()); // GH-90000
    }

    @Test
    void testNxBuildLogDetection() throws IOException { // GH-90000
        String nxLog =
                """
            > nx run webapp:build
            Successfully ran target build for project webapp
            """;

        BuildLogNormalizerService service = new BuildLogNormalizerService(); // GH-90000
        NormalizedBuildLog normalized = service.normalize(nxLog); // GH-90000

        assertEquals(NormalizedBuildLog.BuildTool.NX, normalized.getTool()); // GH-90000
        assertEquals(NormalizedBuildLog.BuildStatus.SUCCESS, normalized.getStatus()); // GH-90000
    }

    @Test
    void testSupportedTools() { // GH-90000
        BuildLogNormalizerService service = new BuildLogNormalizerService(); // GH-90000
        List<NormalizedBuildLog.BuildTool> supportedTools = service.getSupportedTools(); // GH-90000

        assertTrue(supportedTools.contains(NormalizedBuildLog.BuildTool.GRADLE)); // GH-90000
        assertTrue(supportedTools.contains(NormalizedBuildLog.BuildTool.NX)); // GH-90000
    }

    @Test
    void testUnsupportedLogThrowsException() { // GH-90000
        String unknownLog = "This is not a recognized build log format";
        BuildLogNormalizerService service = new BuildLogNormalizerService(); // GH-90000

        assertThrows(IOException.class, () -> service.normalize(unknownLog)); // GH-90000
    }

    @Test
    void testEmptyLogThrowsException() { // GH-90000
        BuildLogNormalizerService service = new BuildLogNormalizerService(); // GH-90000

        assertThrows(IOException.class, () -> service.normalize(" [GH-90000]"));
        assertThrows(IOException.class, () -> service.normalize((String) null)); // GH-90000
    }
}

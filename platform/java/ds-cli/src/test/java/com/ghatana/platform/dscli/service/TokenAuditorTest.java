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
package com.ghatana.platform.dscli.service;

import com.ghatana.platform.dscli.model.TokenFile;
import com.ghatana.platform.dscli.model.ValidationIssue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TokenAuditor}.
 *
 * @doc.type class
 * @doc.purpose Verify token file auditing for duplicates, a11y, and naming conventions
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Token Auditor Tests [GH-90000]")
class TokenAuditorTest {

    private final TokenAuditor auditor = new TokenAuditor(); // GH-90000

    @Test
    @DisplayName("should detect no files warning [GH-90000]")
    void shouldDetectNoFilesWarning() { // GH-90000
        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); // GH-90000
        
        List<ValidationIssue> issues = auditor.audit(files); // GH-90000

        assertThat(issues).hasSize(1); // GH-90000
        assertThat(issues.get(0).code()).isEqualTo("NO_FILES [GH-90000]");
    }

    @Test
    @DisplayName("should detect duplicate values [GH-90000]")
    void shouldDetectDuplicateValues() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        Map<String, Object> color1 = new HashMap<>(); // GH-90000
        color1.put("$value", "#000000"); // GH-90000
        Map<String, Object> color2 = new HashMap<>(); // GH-90000
        color2.put("$value", "#000000"); // GH-90000
        tokenFile.setToken("color-primary", color1); // GH-90000
        tokenFile.setToken("color-secondary", color2); // GH-90000

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); // GH-90000
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json [GH-90000]"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); // GH-90000

        assertThat(issues).anyMatch(issue -> issue.code().equals("DUPLICATE_VALUE [GH-90000]"));
    }

    @Test
    @DisplayName("should detect missing a11y pairs [GH-90000]")
    void shouldDetectMissingA11yPairs() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        Map<String, Object> color = new HashMap<>(); // GH-90000
        color.put("$value", "#000000"); // GH-90000
        tokenFile.setToken("color-primary", color); // GH-90000

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); // GH-90000
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json [GH-90000]"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); // GH-90000

        assertThat(issues).anyMatch(issue -> issue.code().equals("MISSING_A11Y_PAIRS [GH-90000]"));
    }

    @Test
    @DisplayName("should detect naming convention violations [GH-90000]")
    void shouldDetectNamingConventionViolations() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        Map<String, Object> color = new HashMap<>(); // GH-90000
        color.put("$value", "#000000"); // GH-90000
        tokenFile.setToken("color_primary", color); // underscore instead of hyphen // GH-90000

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); // GH-90000
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json [GH-90000]"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); // GH-90000

        assertThat(issues).anyMatch(issue -> issue.code().equals("NAMING_CONVENTION [GH-90000]"));
    }

    @Test
    @DisplayName("should pass audit with no issues [GH-90000]")
    void shouldPassAuditWithNoIssues() { // GH-90000
        TokenFile tokenFile = new TokenFile(); // GH-90000
        Map<String, Object> color = new HashMap<>(); // GH-90000
        color.put("$value", "#000000"); // GH-90000
        Map<String, Object> onColor = new HashMap<>(); // GH-90000
        onColor.put("$value", "#ffffff"); // GH-90000
        tokenFile.setToken("color-primary", color); // GH-90000
        tokenFile.setToken("on-color-primary", onColor); // GH-90000

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); // GH-90000
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json [GH-90000]"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); // GH-90000

        // Should not have errors, only optional warnings
        assertThat(issues).noneMatch(issue -> issue.code().equals("DUPLICATE_VALUE [GH-90000]") ||
                                         issue.code().equals("MISSING_A11Y_PAIRS [GH-90000]") ||
                                         issue.code().equals("NAMING_CONVENTION [GH-90000]"));
    }
}

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
@DisplayName("Token Auditor Tests")
class TokenAuditorTest {

    private final TokenAuditor auditor = new TokenAuditor(); 

    @Test
    @DisplayName("should detect no files warning")
    void shouldDetectNoFilesWarning() { 
        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); 
        
        List<ValidationIssue> issues = auditor.audit(files); 

        assertThat(issues).hasSize(1); 
        assertThat(issues.get(0).code()).isEqualTo("NO_FILES");
    }

    @Test
    @DisplayName("should detect duplicate values")
    void shouldDetectDuplicateValues() { 
        TokenFile tokenFile = new TokenFile(); 
        Map<String, Object> color1 = new HashMap<>(); 
        color1.put("$value", "#000000"); 
        Map<String, Object> color2 = new HashMap<>(); 
        color2.put("$value", "#000000"); 
        tokenFile.setToken("color-primary", color1); 
        tokenFile.setToken("color-secondary", color2); 

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); 
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); 

        assertThat(issues).anyMatch(issue -> issue.code().equals("DUPLICATE_VALUE"));
    }

    @Test
    @DisplayName("should detect missing a11y pairs")
    void shouldDetectMissingA11yPairs() { 
        TokenFile tokenFile = new TokenFile(); 
        Map<String, Object> color = new HashMap<>(); 
        color.put("$value", "#000000"); 
        tokenFile.setToken("color-primary", color); 

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); 
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); 

        assertThat(issues).anyMatch(issue -> issue.code().equals("MISSING_A11Y_PAIRS"));
    }

    @Test
    @DisplayName("should detect naming convention violations")
    void shouldDetectNamingConventionViolations() { 
        TokenFile tokenFile = new TokenFile(); 
        Map<String, Object> color = new HashMap<>(); 
        color.put("$value", "#000000"); 
        tokenFile.setToken("color_primary", color); // underscore instead of hyphen 

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); 
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); 

        assertThat(issues).anyMatch(issue -> issue.code().equals("NAMING_CONVENTION"));
    }

    @Test
    @DisplayName("should pass audit with no issues")
    void shouldPassAuditWithNoIssues() { 
        TokenFile tokenFile = new TokenFile(); 
        Map<String, Object> color = new HashMap<>(); 
        color.put("$value", "#000000"); 
        Map<String, Object> onColor = new HashMap<>(); 
        onColor.put("$value", "#ffffff"); 
        tokenFile.setToken("color-primary", color); 
        tokenFile.setToken("on-color-primary", onColor); 

        List<TokenFileLoader.LoadedTokenFile> files = new ArrayList<>(); 
        files.add(new TokenFileLoader.LoadedTokenFile(Path.of("tokens.json"), tokenFile));

        List<ValidationIssue> issues = auditor.audit(files); 

        // Should not have errors, only optional warnings
        assertThat(issues).noneMatch(issue -> issue.code().equals("DUPLICATE_VALUE") ||
                                         issue.code().equals("MISSING_A11Y_PAIRS") ||
                                         issue.code().equals("NAMING_CONVENTION"));
    }
}

/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.analytics;

import io.activej.promise.Promise;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Regex-based {@link PromptInjectionDetector} covering common injection patterns.
 *
 * <p>Detection rules include:
 * <ul>
 *   <li>Role-override phrases: "ignore previous instructions", "you are now", etc.</li>
 *   <li>System-prompt injection markers: "SYSTEM:", "[SYSTEM]", etc.</li>
 *   <li>Jailbreak prefixes: "DAN", "Developer Mode", "STAN" patterns.</li>
 *   <li>Instruction-boundary attacks: "---\\nNEW INSTRUCTIONS", etc.</li>
 * </ul>
 *
 * <p>This is a defence-in-depth measure, not a complete solution. LLM providers
 * may have additional native protections.
 *
 * @doc.type class
 * @doc.purpose Regex-based prompt injection detection for common attack patterns
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class RegexPromptInjectionDetector implements PromptInjectionDetector {

    private record Rule(Pattern pattern, String name, double confidence) {}

    private static final List<Rule> RULES = List.of(
        rule("(?i)ignore\\s+(previous|above|all)\\s+(instructions|directives|rules|constraints)",
            "ignore-instructions", 0.95),
        rule("(?i)(you\\s+are\\s+now|act\\s+as|pretend\\s+(you\\s+are|to\\s+be))\\s+\\w",
            "role-override", 0.85),
        rule("(?i)(\\[SYSTEM\\]|<SYSTEM>|SYSTEM:|\\[INST\\])",
            "system-marker", 0.90),
        rule("(?i)(developer\\s+mode|DAN\\s+mode|jailbreak|STAN\\s+mode)",
            "jailbreak-keyword", 0.92),
        rule("(?i)(disregard|override|bypass|unlock)\\s+(your|all|the)\\s+(system\\s+)?prompt",
            "prompt-override", 0.95),
        rule("(?i)(---+|===+|###)\\s*\\n\\s*(new instruction|ignore above|system prompt)",
            "boundary-injection", 0.88),
        rule("(?i)(;\\s*drop\\s+table|\\bselect\\s+\\*\\s+from\\b|\\bunion\\s+select\\b|\\bexec\\s+sp_executesql\\b)",
            "sql-injection-signature", 0.98),
        rule("(?i)<script\\b[^>]*>",
            "xss-script-tag", 0.90)
    );

    @Override
    public Promise<DetectionResult> detect(String tenantId, String input) {
        if (input == null || input.isBlank()) {
            return Promise.of(DetectionResult.safe());
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(input).find()) {
                return Promise.of(DetectionResult.detected(rule.name(), rule.confidence()));
            }
        }
        return Promise.of(DetectionResult.safe());
    }

    /**
     * Synchronous check for suspicious prompts (used by tests).
     * @param input the input to check
     * @return true if suspicious patterns are detected
     */
    public boolean isSuspiciousPrompt(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    private static Rule rule(String regex, String name, double confidence) {
        return new Rule(Pattern.compile(regex), name, confidence);
    }
}

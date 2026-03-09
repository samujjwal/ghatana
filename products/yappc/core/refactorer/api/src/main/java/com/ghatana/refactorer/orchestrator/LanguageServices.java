package com.ghatana.refactorer.orchestrator;

import com.ghatana.refactorer.shared.PolyfixConfig;
import com.ghatana.refactorer.shared.service.LanguageService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Language services are declared here for EPIC-02; future epics will move to SPI discovery.
/**
 * @doc.type class
 * @doc.purpose Handles language services operations
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public final class LanguageServices {
    private static final Logger log = LogManager.getLogger(LanguageServices.class);

    private LanguageServices() {}

    public static List<LanguageService> load(PolyfixConfig cfg) {
        final Set<String> allow = new HashSet<>();
        if (cfg != null && cfg.languages() != null && !cfg.languages().isEmpty()) {
            allow.addAll(cfg.languages());
        }

        List<LanguageService> all = new ArrayList<>();
        try {
            all.add(new com.ghatana.refactorer.diagnostics.java.JavaLanguageService());
        } catch (Throwable t) {
            log.warn("JavaLanguageService unavailable: {}", t.toString());
        }
        try {
            all.add(new com.ghatana.refactorer.diagnostics.jsonyaml.JsonYamlLanguageService());
        } catch (Throwable t) {
            log.warn("JsonYamlLanguageService unavailable: {}", t.toString());
        }

        if (allow.isEmpty()) return all;
        return all.stream().filter(ls -> allow.contains(ls.id())).toList();
    }
}

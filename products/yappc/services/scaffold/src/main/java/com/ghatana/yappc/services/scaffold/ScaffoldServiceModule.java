/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Scaffold Service Module
 */
package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.core.orchestrator.PolyglotBuildOrchestrator;
import com.ghatana.yappc.core.services.ProjectAnalysisService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC Scaffold services.
 *
 * <p>Provides bindings for code generation, project scaffolding,
 * and build orchestration:
 * <ul>
 *   <li>{@link ProjectAnalysisService} — Project structure detection
 *       (languages, frameworks, build tools)</li>
 *   <li>{@link PolyglotBuildOrchestrator} — Multi-language build
 *       file generation (Gradle, Maven, Cargo, Go, npm)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DI module for scaffold/code-generation services
 * @doc.layer product
 * @doc.pattern Module
 */
public class ScaffoldServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(ScaffoldServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Scaffold Service DI bindings");
    }

    /**
     * Provides ProjectAnalysisService for analyzing existing project structure.
     *
     * <p>Detects languages, frameworks, build tools, and CI/CD patterns
     * for intelligent scaffold generation.</p>
     */
    @Provides
    ProjectAnalysisService projectAnalysisService() {
        logger.info("Creating ProjectAnalysisService");
        return new ProjectAnalysisService();
    }

    /**
     * Provides PolyglotBuildOrchestrator for multi-language build generation.
     *
     * <p>Generates build files for Java (Gradle/Maven), Rust (Cargo),
     * Go (go.mod), Node.js (package.json), and more.</p>
     */
    @Provides
    PolyglotBuildOrchestrator polyglotBuildOrchestrator() {
        logger.info("Creating PolyglotBuildOrchestrator");
        return new PolyglotBuildOrchestrator();
    }
}

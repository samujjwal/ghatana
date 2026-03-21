/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for AI code generation and test generation services.
 *
 * @doc.type class
 * @doc.purpose AI codegen / testgen DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class AiCodegenModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(AiCodegenModule.class);

  @Provides
  com.ghatana.ai.AIIntegrationService aiIntegrationService(
      com.ghatana.ai.llm.LLMGateway llmGateway) {
    logger.info("Creating LLMGatewayAIIntegrationService");
    return new com.ghatana.yappc.api.ai.LLMGatewayAIIntegrationService(llmGateway);
  }

  @Provides
  com.ghatana.yappc.api.codegen.CodeGenerationService codeGenerationService(
      com.ghatana.ai.AIIntegrationService aiIntegrationService) {
    logger.info("Creating CodeGenerationService");
    return new com.ghatana.yappc.api.codegen.CodeGenerationService(aiIntegrationService);
  }

  @Provides
  com.ghatana.yappc.api.codegen.CodeGenerationController codeGenerationController(
      com.ghatana.yappc.api.codegen.CodeGenerationService codeGenerationService) {
    return new com.ghatana.yappc.api.codegen.CodeGenerationController(codeGenerationService);
  }

  @Provides
  com.ghatana.yappc.api.testing.TestGenerationService testGenerationService(
      com.ghatana.ai.AIIntegrationService aiIntegrationService) {
    logger.info("Creating TestGenerationService");
    return new com.ghatana.yappc.api.testing.TestGenerationService(aiIntegrationService);
  }

  @Provides
  com.ghatana.yappc.api.testing.TestGenerationController testGenerationController(
      com.ghatana.yappc.api.testing.TestGenerationService testGenerationService) {
    return new com.ghatana.yappc.api.testing.TestGenerationController(testGenerationService);
  }
}

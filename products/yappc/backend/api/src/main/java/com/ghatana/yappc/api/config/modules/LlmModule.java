/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config.modules;

import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DI sub-module for LLM gateway, completion service, and suggestion generator.
 *
 * <p>Configures multi-provider LLM gateway (OpenAI / Anthropic) with cost enforcement, HTTP client
 * for provider communication, completion service adapter, and suggestion generator.
 *
 * @doc.type class
 * @doc.purpose LLM integration DI bindings
 * @doc.layer api
 * @doc.pattern Module, Dependency Injection
 */
public class LlmModule extends AbstractModule {

  private static final Logger logger = LoggerFactory.getLogger(LlmModule.class);

  @Provides
  HttpClient llmHttpClient(Eventloop eventloop) {
    IDnsClient dnsClient = DnsClient.builder(eventloop, InetAddress.getLoopbackAddress()).build();
    return HttpClient.create(eventloop, dnsClient);
  }

  @Provides
  com.ghatana.ai.llm.LLMGateway llmGateway(
      MetricsCollector metricsCollector, HttpClient llmHttpClient) {
    DefaultLLMGateway.Builder builder = DefaultLLMGateway.builder().metrics(metricsCollector);
    List<String> providers = new ArrayList<>();

    logger.info("LLMGateway: cost enforcement enabled (default 10M tokens/tenant)");

    String openAiApiKey = normalizedEnv("OPENAI_API_KEY");
    if (openAiApiKey != null) {
      LLMConfiguration openAiConfig =
          LLMConfiguration.builder()
              .apiKey(openAiApiKey)
              .baseUrl(normalizedEnv("OPENAI_BASE_URL"))
              .modelName(envOrDefault("OPENAI_MODEL", "gpt-4o-mini"))
              .temperature(parseDoubleEnv("LLM_TEMPERATURE", 0.7))
              .maxTokens(parseIntEnv("LLM_MAX_TOKENS", 2000))
              .timeoutSeconds(parseIntEnv("LLM_TIMEOUT_SECONDS", 30))
              .maxRetries(parseIntEnv("LLM_MAX_RETRIES", 3))
              .build();

      builder.addProvider(
          "openai",
          new ToolAwareOpenAICompletionService(openAiConfig, llmHttpClient, metricsCollector));
      providers.add("openai");
      logger.info("LLMGateway: OpenAI provider enabled with cost enforcement");
    }

    String anthropicApiKey = normalizedEnv("ANTHROPIC_API_KEY");
    if (anthropicApiKey != null) {
      LLMConfiguration anthropicConfig =
          LLMConfiguration.builder()
              .apiKey(anthropicApiKey)
              .baseUrl(normalizedEnv("ANTHROPIC_BASE_URL"))
              .modelName(envOrDefault("ANTHROPIC_MODEL", "claude-3-5-sonnet-20241022"))
              .temperature(parseDoubleEnv("LLM_TEMPERATURE", 0.7))
              .maxTokens(parseIntEnv("LLM_MAX_TOKENS", 2000))
              .timeoutSeconds(parseIntEnv("LLM_TIMEOUT_SECONDS", 30))
              .maxRetries(parseIntEnv("LLM_MAX_RETRIES", 3))
              .build();

      builder.addProvider(
          "anthropic",
          new ToolAwareAnthropicCompletionService(
              anthropicConfig, llmHttpClient, metricsCollector));
      providers.add("anthropic");
      logger.info("LLMGateway: Anthropic provider enabled with cost enforcement");
    }

    if (providers.isEmpty()) {
      throw new IllegalStateException(
          "No production LLM provider configured. Set OPENAI_API_KEY and/or ANTHROPIC_API_KEY.");
    }

    String primaryProvider = envOrDefault("LLM_PRIMARY_PROVIDER", providers.get(0));
    if (!providers.contains(primaryProvider)) {
      logger.warn(
          "Configured LLM_PRIMARY_PROVIDER '{}' is unavailable, using '{}'",
          primaryProvider,
          providers.get(0));
      primaryProvider = providers.get(0);
    }

    builder.defaultProvider(primaryProvider).fallbackOrder(providers);
    logger.info(
        "Creating production LLMGateway with providers={} primary={}", providers, primaryProvider);
    return builder.build();
  }

  @Provides
  com.ghatana.ai.llm.CompletionService completionService(
      com.ghatana.ai.llm.LLMGateway llmGateway, MetricsCollector metricsCollector) {
    logger.info("Creating GatewayCompletionServiceAdapter backed by LLMGateway");
    return new com.ghatana.yappc.api.ai.GatewayCompletionServiceAdapter(
        llmGateway, metricsCollector);
  }

  @Provides
  com.ghatana.yappc.api.service.LLMSuggestionGenerator llmSuggestionGenerator(
      com.ghatana.ai.llm.CompletionService completionService) {
    logger.info("Creating LLMSuggestionGenerator");
    return new com.ghatana.yappc.api.service.LLMSuggestionGenerator(completionService);
  }

  private static String normalizedEnv(String key) {
    String value = System.getenv(key);
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String envOrDefault(String key, String defaultValue) {
    String value = normalizedEnv(key);
    return value != null ? value : defaultValue;
  }

  private static int parseIntEnv(String key, int defaultValue) {
    String value = normalizedEnv(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static double parseDoubleEnv(String key, double defaultValue) {
    String value = normalizedEnv(key);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}

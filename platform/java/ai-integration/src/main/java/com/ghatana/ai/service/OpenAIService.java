package com.ghatana.ai.service;

import io.activej.promise.Promise;
import io.activej.eventloop.Eventloop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @doc.type class
 * @doc.purpose OpenAI implementation of the LLMService.
 * @doc.layer core
 * @doc.pattern Service
 */
public class OpenAIService implements LLMService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    private final String apiKey;
    private final Executor executor = Executors.newCachedThreadPool();

    public OpenAIService(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Promise<String> generate(String prompt) {
        return Promise.ofBlocking(executor, () -> {
            logger.info("Calling OpenAI with prompt: {}", prompt);
            // TODO: Replace with real OpenAI client call
            return "AI Generated Content for: " + prompt;
        });
    }

    @Override
    public Promise<String> generateStructured(String prompt) {
        return generate(prompt);
    }
}

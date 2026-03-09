/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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

package com.ghatana.datacloud.client;

import com.ghatana.datacloud.record.Record;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * AI aspect for automatic classification and tagging of records.
 *
 * <p>This aspect uses AI/ML models to automatically classify
 * incoming data and add tags/labels. Runs in POST phase.
 *
 * <h2>Classification Types</h2>
 * <ul>
 *   <li><b>Category</b> - Single-label classification</li>
 *   <li><b>Tags</b> - Multi-label classification</li>
 *   <li><b>Sentiment</b> - Positive/Negative/Neutral</li>
 *   <li><b>Priority</b> - High/Medium/Low</li>
 * </ul>
 *
 * <h2>Context Attributes</h2>
 * <ul>
 *   <li>{@code classification.category} - Primary category</li>
 *   <li>{@code classification.tags} - List of tags</li>
 *   <li>{@code classification.confidence} - Confidence score</li>
 * </ul>
 *
 * @see AIAspect
 * @see ClassificationService
 * @doc.type class
 * @doc.purpose Classification aspect
 * @doc.layer core
 * @doc.pattern Aspect, Strategy
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class ClassificationAspect implements AIAspect<Record, Record> {

    /** Context key for primary category */
    public static final String ATTR_CATEGORY = "classification.category";
    /** Context key for tags list */
    public static final String ATTR_TAGS = "classification.tags";
    /** Context key for confidence score */
    public static final String ATTR_CONFIDENCE = "classification.confidence";

    private final ClassificationService classificationService;
    private final Function<Record, String> textExtractor;
    private final int priority;
    private final boolean async;

    private ClassificationAspect(Builder builder) {
        this.classificationService = builder.classificationService;
        this.textExtractor = builder.textExtractor;
        this.priority = builder.priority;
        this.async = builder.async;
    }

    @Override
    public String name() {
        return "classification";
    }

    @Override
    public Phase phase() {
        return Phase.POST;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    @Override
    public boolean isApplicable(OperationType operation, AIAspectContext context) {
        return operation == OperationType.CREATE || operation == OperationType.UPDATE;
    }

    @Override
    public Promise<Record> process(Record input, AIAspectContext context) {
        String text = textExtractor.apply(input);
        
        if (text == null || text.isBlank()) {
            return Promise.of(input);
        }

        return classificationService.classify(text)
                .map(result -> {
                    context.setAttribute(ATTR_CATEGORY, result.category());
                    context.setAttribute(ATTR_TAGS, result.tags());
                    context.setAttribute(ATTR_CONFIDENCE, result.confidence());
                    return input;
                });
    }

    /**
     * Creates a new builder.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ClassificationAspect.
     */
    public static final class Builder {
        private ClassificationService classificationService;
        private Function<Record, String> textExtractor = record -> record.data().toString();
        private int priority = 110; // After embedding
        private boolean async = true;

        private Builder() {
        }

        public Builder classificationService(ClassificationService service) {
            this.classificationService = service;
            return this;
        }

        public Builder textExtractor(Function<Record, String> extractor) {
            this.textExtractor = extractor;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public ClassificationAspect build() {
            if (classificationService == null) {
                throw new IllegalStateException("ClassificationService is required");
            }
            return new ClassificationAspect(this);
        }
    }

    /**
     * Service for classification.
     */
    public interface ClassificationService {

        /**
         * Classifies the given text.
         *
         * @param text text to classify
         * @return classification result
         */
        Promise<ClassificationResult> classify(String text);

        /**
         * Returns available categories.
         *
         * @return list of category names
         */
        List<String> availableCategories();
    }

    /**
     * Classification result.
     *
     * @param category primary category
     * @param tags list of tags
     * @param confidence confidence score (0.0-1.0)
     * @param allScores scores for all categories
     */
    public record ClassificationResult(
            String category,
            List<String> tags,
            double confidence,
            Map<String, Double> allScores
    ) {
        /**
         * Creates a simple result with just category.
         *
         * @param category the category
         * @param confidence confidence score
         * @return classification result
         */
        public static ClassificationResult simple(String category, double confidence) {
            return new ClassificationResult(category, List.of(), confidence, Map.of(category, confidence));
        }
    }
}

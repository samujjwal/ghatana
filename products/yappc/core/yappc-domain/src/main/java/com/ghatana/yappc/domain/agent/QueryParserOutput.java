package com.ghatana.products.yappc.domain.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Output from the Query Parser Agent.
 *
 * @doc.type record
 * @doc.purpose Query parser agent output
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record QueryParserOutput(
        @NotNull ParsedQuery parsed,
        @NotNull List<String> suggestions,
        @NotNull List<ParsedQuery> alternatives
) {

    /**
     * A parsed query with intent and entities.
     */
    public record ParsedQuery(
            @NotNull QueryIntent intent,
            @NotNull List<ExtractedEntity> entities,
            @NotNull ItemFilter filters,
            @Nullable AIAction action,
            double confidence
    ) {

        /**
         * Query intent types.
         */
        public enum QueryIntent {
            SEARCH,
            FILTER,
            COMMAND,
            QUESTION,
            NAVIGATE
        }
    }

    /**
     * An entity extracted from the query.
     */
    public record ExtractedEntity(
            @NotNull String type,
            @NotNull String value,
            @Nullable String normalizedValue,
            int startIndex,
            int endIndex,
            double confidence
    ) {
        /**
         * Common entity types.
         */
        public static final String TYPE_ITEM_ID = "item_id";
        public static final String TYPE_PHASE = "phase";
        public static final String TYPE_STATUS = "status";
        public static final String TYPE_PRIORITY = "priority";
        public static final String TYPE_ASSIGNEE = "assignee";
        public static final String TYPE_DATE = "date";
        public static final String TYPE_TAG = "tag";
        public static final String TYPE_NUMBER = "number";
    }

    /**
     * Item filters extracted from the query.
     */
    public record ItemFilter(
            @Nullable List<String> statuses,
            @Nullable List<String> priorities,
            @Nullable List<String> phases,
            @Nullable List<String> assignees,
            @Nullable List<String> tags,
            @Nullable String searchText,
            @Nullable DateRange dateRange,
            @Nullable Map<String, Object> customFilters
    ) {
        public static ItemFilter empty() {
            return new ItemFilter(null, null, null, null, null, null, null, null);
        }

        public record DateRange(
                @Nullable String from,
                @Nullable String to
        ) {}
    }

    /**
     * An AI-suggested action.
     */
    public record AIAction(
            @NotNull String type,
            @NotNull String description,
            @NotNull Map<String, Object> parameters,
            boolean requiresConfirmation,
            double confidence
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ParsedQuery parsed;
        private List<String> suggestions = List.of();
        private List<ParsedQuery> alternatives = List.of();

        public Builder parsed(ParsedQuery parsed) {
            this.parsed = parsed;
            return this;
        }

        public Builder suggestions(List<String> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public Builder alternatives(List<ParsedQuery> alternatives) {
            this.alternatives = alternatives;
            return this;
        }

        public QueryParserOutput build() {
            return new QueryParserOutput(parsed, suggestions, alternatives);
        }
    }
}

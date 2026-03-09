package com.ghatana.tutorputor.agent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Response from the tutoring agent to a learner action.
 *
 * @doc.type record
 * @doc.purpose Tutoring response DTO
 * @doc.layer product
 * @doc.pattern Value Object
 *
 * @param responseType the type of response
 * @param message the main response message
 * @param feedbackText detailed feedback for the learner
 * @param hintText hint text if providing a hint
 * @param explanationText explanation text if providing explanation
 * @param nextAction suggested next action for the learner
 * @param masteryLevel current mastery level (0.0 to 1.0)
 * @param adjustedDifficulty the adjusted difficulty level
 * @param suggestedContent list of suggested content IDs
 * @param encouragementMessage motivational message
 * @param metadata additional response metadata
 */
public record TutoringResponse(
    @NotNull ResponseType responseType,
    @NotNull String message,
    @Nullable String feedbackText,
    @Nullable String hintText,
    @Nullable String explanationText,
    @NotNull NextAction nextAction,
    double masteryLevel,
    @NotNull String adjustedDifficulty,
    @Nullable List<String> suggestedContent,
    @Nullable String encouragementMessage,
    @Nullable Map<String, Object> metadata
) {
    /**
     * Creates a builder for TutoringResponse.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Types of tutoring responses.
     */
    public enum ResponseType {
        /** Feedback for correct answer */
        CORRECT_FEEDBACK,
        /** Feedback for incorrect answer */
        INCORRECT_FEEDBACK,
        /** Hint for the current question */
        HINT,
        /** Detailed explanation */
        EXPLANATION,
        /** Encouragement message */
        ENCOURAGEMENT,
        /** Next question presentation */
        NEXT_QUESTION,
        /** Mastery achieved notification */
        MASTERY_ACHIEVED,
        /** Remediation content */
        REMEDIATION
    }

    /**
     * Suggested next actions.
     */
    public enum NextAction {
        /** Continue with current topic */
        CONTINUE,
        /** Try again */
        RETRY,
        /** Move to next question */
        NEXT_QUESTION,
        /** Move to next topic */
        NEXT_TOPIC,
        /** Review prerequisite content */
        REVIEW_PREREQUISITE,
        /** Take a break */
        TAKE_BREAK,
        /** Complete assessment */
        TAKE_ASSESSMENT,
        /** Celebrate achievement */
        CELEBRATE
    }

    /**
     * Builder for TutoringResponse.
     */
    public static class Builder {
        private ResponseType responseType = ResponseType.NEXT_QUESTION;
        private String message = "";
        private String feedbackText;
        private String hintText;
        private String explanationText;
        private NextAction nextAction = NextAction.CONTINUE;
        private double masteryLevel = 0.0;
        private String adjustedDifficulty = "medium";
        private List<String> suggestedContent;
        private String encouragementMessage;
        private Map<String, Object> metadata;

        public Builder responseType(ResponseType responseType) {
            this.responseType = responseType;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder feedbackText(String feedbackText) {
            this.feedbackText = feedbackText;
            return this;
        }

        public Builder hintText(String hintText) {
            this.hintText = hintText;
            return this;
        }

        public Builder explanationText(String explanationText) {
            this.explanationText = explanationText;
            return this;
        }

        public Builder nextAction(NextAction nextAction) {
            this.nextAction = nextAction;
            return this;
        }

        public Builder masteryLevel(double masteryLevel) {
            this.masteryLevel = masteryLevel;
            return this;
        }

        public Builder adjustedDifficulty(String adjustedDifficulty) {
            this.adjustedDifficulty = adjustedDifficulty;
            return this;
        }

        public Builder suggestedContent(List<String> suggestedContent) {
            this.suggestedContent = suggestedContent;
            return this;
        }

        public Builder encouragementMessage(String encouragementMessage) {
            this.encouragementMessage = encouragementMessage;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public TutoringResponse build() {
            return new TutoringResponse(
                responseType, message, feedbackText, hintText,
                explanationText, nextAction, masteryLevel,
                adjustedDifficulty, suggestedContent,
                encouragementMessage, metadata
            );
        }
    }
}

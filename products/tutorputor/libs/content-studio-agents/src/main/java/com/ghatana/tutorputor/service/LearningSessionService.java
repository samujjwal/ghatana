package com.ghatana.tutorputor.service;

import com.ghatana.tutorputor.agent.LearnerAction;
import com.ghatana.tutorputor.agent.TutoringResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Learning session manager that tracks and orchestrates learner sessions.
 * 
 * <p>Features:
 * <ul>
 *   <li>Session lifecycle management (start, pause, resume, end)</li>
 *   <li>Progress tracking across multiple topics</li>
 *   <li>Adaptive pacing based on learner performance</li>
 *   <li>Session analytics and reporting</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Learning session management
 * @doc.layer product
 * @doc.pattern SessionFacade
 */
public class LearningSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(LearningSessionService.class);

    private final UnifiedContentService contentService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, LearningSession> activeSessions;
    
    // Metrics
    private final Timer sessionDurationTimer;

    /**
     * Creates a new learning session service.
     *
     * @param contentService the unified content service
     * @param meterRegistry the metrics registry
     */
    public LearningSessionService(
            @NotNull UnifiedContentService contentService,
            @NotNull MeterRegistry meterRegistry) {
        this.contentService = contentService;
        this.meterRegistry = meterRegistry;
        this.activeSessions = new ConcurrentHashMap<>();
        
        this.sessionDurationTimer = Timer.builder("tutorputor.sessions.duration")
            .description("Learning session duration")
            .register(meterRegistry);
        
        LOG.info("LearningSessionService initialized");
    }

    /**
     * Starts a new learning session.
     *
     * @param tenantId the tenant ID
     * @param learnerId the learner ID
     * @param topics the topics to cover
     * @param targetDuration the target session duration
     * @return the session ID
     */
    public String startSession(
            @NotNull String tenantId,
            @NotNull String learnerId,
            @NotNull List<String> topics,
            @NotNull Duration targetDuration) {
        
        String sessionId = UUID.randomUUID().toString();
        
        LearningSession session = new LearningSession(
            sessionId,
            tenantId,
            learnerId,
            new ArrayList<>(topics),
            targetDuration,
            Instant.now()
        );
        
        activeSessions.put(sessionId, session);
        
        LOG.info("Started learning session: {} for learner {} with {} topics", 
            sessionId, learnerId, topics.size());
        
        return sessionId;
    }

    /**
     * Gets the next content item for a session.
     *
     * @param sessionId the session ID
     * @return the next content item, or null if session complete
     */
    public Promise<SessionContent> getNextContent(@NotNull String sessionId) {
        LearningSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Promise.of(null);
        }
        
        // Check if session should end
        if (session.isComplete() || session.hasExceededDuration()) {
            return Promise.of(new SessionContent(
                null, null, null, true, session.getProgress()
            ));
        }
        
        String currentTopic = session.getCurrentTopic();
        if (currentTopic == null) {
            session.markComplete();
            return Promise.of(new SessionContent(
                null, null, null, true, session.getProgress()
            ));
        }
        
        // Generate content for current topic
        return contentService.generateClaims(
            session.tenantId,
            currentTopic,
            5, // grade level - would be personalized in production
            session.learnerId
        ).map(claims -> {
            if (claims.isEmpty()) {
                session.advanceToNextTopic();
                return new SessionContent(
                    currentTopic,
                    null,
                    null,
                    false,
                    session.getProgress()
                );
            }
            
            String claim = claims.get(0);
            session.setCurrentClaim(claim);
            
            return new SessionContent(
                currentTopic,
                claim,
                SessionContent.ContentType.CLAIM,
                false,
                session.getProgress()
            );
        });
    }

    /**
     * Submits a learner response in a session.
     *
     * @param sessionId the session ID
     * @param response the learner's response
     * @return the tutoring feedback
     */
    public Promise<SessionFeedback> submitResponse(
            @NotNull String sessionId,
            @NotNull String response) {
        
        LearningSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Promise.of(new SessionFeedback(
                false, "Session not found", null, null
            ));
        }
        
        String currentClaim = session.getCurrentClaim();
        if (currentClaim == null) {
            return Promise.of(new SessionFeedback(
                false, "No active content", null, session.getProgress()
            ));
        }
        
        LearnerAction action = new LearnerAction(
            session.learnerId,
            session.getCurrentTopic() != null ? session.getCurrentTopic() : currentClaim,
            LearnerAction.ActionType.ANSWER_SUBMITTED,
            response,
            null,
            session.getTimeOnCurrentItem() != null ? session.getTimeOnCurrentItem().toMillis() : null,
            null,
            session.getAttemptsOnCurrent() + 1,
            null,
            Map.of("tenantId", session.tenantId, "claim", currentClaim)
        );
        
        return contentService.processLearnerAction(action)
            .map(tutorResponse -> {
                if (tutorResponse == null) {
                    return new SessionFeedback(
                        false, "Unable to evaluate", null, session.getProgress()
                    );
                }
                
                boolean correct = tutorResponse.responseType() == TutoringResponse.ResponseType.CORRECT_FEEDBACK
                    || tutorResponse.responseType() == TutoringResponse.ResponseType.MASTERY_ACHIEVED;
                
                session.recordAttempt(correct);
                
                // Decide whether to advance
                if (correct || session.getAttemptsOnCurrent() >= 3) {
                    if (session.shouldAdvanceTopic()) {
                        session.advanceToNextTopic();
                    } else {
                        session.advanceToNextClaim();
                    }
                }
                
                return new SessionFeedback(
                    correct,
                    tutorResponse.message(),
                    tutorResponse.nextAction(),
                    session.getProgress()
                );
            });
    }

    /**
     * Requests a hint in the current session.
     *
     * @param sessionId the session ID
     * @return the hint
     */
    public Promise<String> requestHint(@NotNull String sessionId) {
        LearningSession session = activeSessions.get(sessionId);
        if (session == null) {
            return Promise.of("Session not found");
        }
        
        String currentClaim = session.getCurrentClaim();
        if (currentClaim == null) {
            return Promise.of("No active content");
        }
        
        int hintLevel = session.incrementHintCount();
        
        return contentService.generateHint(
            session.tenantId,
            session.learnerId,
            currentClaim,
            hintLevel
        );
    }

    /**
     * Pauses a learning session.
     *
     * @param sessionId the session ID
     */
    public void pauseSession(@NotNull String sessionId) {
        LearningSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.pause();
            LOG.info("Paused session: {}", sessionId);
        }
    }

    /**
     * Resumes a paused session.
     *
     * @param sessionId the session ID
     */
    public void resumeSession(@NotNull String sessionId) {
        LearningSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.resume();
            LOG.info("Resumed session: {}", sessionId);
        }
    }

    /**
     * Ends a learning session.
     *
     * @param sessionId the session ID
     * @return session summary
     */
    public SessionSummary endSession(@NotNull String sessionId) {
        LearningSession session = activeSessions.remove(sessionId);
        if (session == null) {
            return null;
        }
        
        session.end();
        
        Duration duration = session.getTotalDuration();
        sessionDurationTimer.record(duration);
        
        SessionSummary summary = new SessionSummary(
            sessionId,
            session.learnerId,
            duration,
            session.getTopicsCompleted(),
            session.getTotalAttempts(),
            session.getCorrectAttempts(),
            session.getHintsUsed(),
            session.calculateMasteryGain()
        );
        
        LOG.info("Ended session: {} - {} topics completed, {}% accuracy", 
            sessionId, 
            summary.topicsCompleted(),
            summary.totalAttempts() > 0 ? 
                (summary.correctAttempts() * 100 / summary.totalAttempts()) : 0);
        
        return summary;
    }

    /**
     * Gets session information.
     *
     * @param sessionId the session ID
     * @return session info, or null if not found
     */
    public SessionInfo getSessionInfo(@NotNull String sessionId) {
        LearningSession session = activeSessions.get(sessionId);
        if (session == null) {
            return null;
        }
        
        return new SessionInfo(
            sessionId,
            session.learnerId,
            session.getState(),
            session.getTotalDuration(),
            session.getProgress(),
            session.getCurrentTopic()
        );
    }

    /**
     * Lists active sessions for a learner.
     *
     * @param learnerId the learner ID
     * @return list of active session IDs
     */
    public List<String> getActiveSessionsForLearner(@NotNull String learnerId) {
        return activeSessions.entrySet().stream()
            .filter(e -> e.getValue().learnerId.equals(learnerId))
            .filter(e -> e.getValue().getState() != SessionState.ENDED)
            .map(Map.Entry::getKey)
            .toList();
    }

    // =========================================================================
    // Internal Session Class
    // =========================================================================

    private static class LearningSession {
        final String sessionId;
        final String tenantId;
        final String learnerId;
        final List<String> topics;
        final Duration targetDuration;
        final Instant startTime;
        
        private int currentTopicIndex = 0;
        private String currentClaim;
        private Instant currentItemStartTime;
        private int attemptsOnCurrent = 0;
        private int hintsOnCurrent = 0;
        private int totalAttempts = 0;
        private int correctAttempts = 0;
        private int totalHints = 0;
        private int topicsCompleted = 0;
        private double initialMastery = 0.0;
        private double currentMastery = 0.0;
        
        private SessionState state = SessionState.ACTIVE;
        private Instant pauseTime;
        private Duration pausedDuration = Duration.ZERO;

        LearningSession(String sessionId, String tenantId, String learnerId,
                       List<String> topics, Duration targetDuration, Instant startTime) {
            this.sessionId = sessionId;
            this.tenantId = tenantId;
            this.learnerId = learnerId;
            this.topics = topics;
            this.targetDuration = targetDuration;
            this.startTime = startTime;
            this.currentItemStartTime = startTime;
        }

        String getCurrentTopic() {
            if (currentTopicIndex >= topics.size()) return null;
            return topics.get(currentTopicIndex);
        }

        String getCurrentClaim() { return currentClaim; }
        void setCurrentClaim(String claim) { 
            this.currentClaim = claim;
            this.currentItemStartTime = Instant.now();
            this.attemptsOnCurrent = 0;
            this.hintsOnCurrent = 0;
        }

        Duration getTimeOnCurrentItem() {
            return Duration.between(currentItemStartTime, Instant.now());
        }

        int getAttemptsOnCurrent() { return attemptsOnCurrent; }
        
        void recordAttempt(boolean correct) {
            attemptsOnCurrent++;
            totalAttempts++;
            if (correct) {
                correctAttempts++;
                currentMastery += 0.05; // Simple mastery increment
            }
        }

        int incrementHintCount() {
            hintsOnCurrent++;
            totalHints++;
            return Math.min(hintsOnCurrent, 3);
        }

        boolean shouldAdvanceTopic() {
            // Advance after demonstrating mastery on current topic
            return correctAttempts > 0 && 
                   (double) correctAttempts / totalAttempts > 0.7;
        }

        void advanceToNextClaim() {
            currentClaim = null;
            attemptsOnCurrent = 0;
            hintsOnCurrent = 0;
        }

        void advanceToNextTopic() {
            topicsCompleted++;
            currentTopicIndex++;
            currentClaim = null;
            attemptsOnCurrent = 0;
            hintsOnCurrent = 0;
        }

        boolean isComplete() {
            return currentTopicIndex >= topics.size() || state == SessionState.ENDED;
        }

        boolean hasExceededDuration() {
            return getTotalDuration().compareTo(targetDuration) > 0;
        }

        void markComplete() {
            state = SessionState.ENDED;
        }

        Duration getTotalDuration() {
            Duration active = Duration.between(startTime, 
                state == SessionState.PAUSED ? pauseTime : Instant.now());
            return active.minus(pausedDuration);
        }

        void pause() {
            if (state == SessionState.ACTIVE) {
                state = SessionState.PAUSED;
                pauseTime = Instant.now();
            }
        }

        void resume() {
            if (state == SessionState.PAUSED) {
                pausedDuration = pausedDuration.plus(
                    Duration.between(pauseTime, Instant.now()));
                state = SessionState.ACTIVE;
            }
        }

        void end() {
            state = SessionState.ENDED;
        }

        SessionState getState() { return state; }
        int getTopicsCompleted() { return topicsCompleted; }
        int getTotalAttempts() { return totalAttempts; }
        int getCorrectAttempts() { return correctAttempts; }
        int getHintsUsed() { return totalHints; }
        
        double calculateMasteryGain() {
            return currentMastery - initialMastery;
        }

        SessionProgress getProgress() {
            return new SessionProgress(
                currentTopicIndex,
                topics.size(),
                topicsCompleted,
                correctAttempts,
                totalAttempts,
                getTotalDuration(),
                targetDuration
            );
        }
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Session state.
     */
    public enum SessionState {
        ACTIVE,
        PAUSED,
        ENDED
    }

    /**
     * Session progress.
     */
    public record SessionProgress(
        int currentTopicIndex,
        int totalTopics,
        int topicsCompleted,
        int correctAnswers,
        int totalAnswers,
        Duration elapsed,
        Duration target
    ) {
        public int progressPercent() {
            if (totalTopics == 0) return 0;
            return (topicsCompleted * 100) / totalTopics;
        }

        public int accuracyPercent() {
            if (totalAnswers == 0) return 0;
            return (correctAnswers * 100) / totalAnswers;
        }
    }

    /**
     * Content to present in session.
     */
    public record SessionContent(
        String topic,
        String content,
        ContentType contentType,
        boolean sessionComplete,
        SessionProgress progress
    ) {
        public enum ContentType {
            CLAIM,
            EXAMPLE,
            EXERCISE,
            SIMULATION
        }
    }

    /**
     * Feedback after a learner response.
     */
    public record SessionFeedback(
        boolean correct,
        String message,
        TutoringResponse.NextAction nextAction,
        SessionProgress progress
    ) {}

    /**
     * Session summary at end.
     */
    public record SessionSummary(
        String sessionId,
        String learnerId,
        Duration duration,
        int topicsCompleted,
        int totalAttempts,
        int correctAttempts,
        int hintsUsed,
        double masteryGain
    ) {}

    /**
     * Session information.
     */
    public record SessionInfo(
        String sessionId,
        String learnerId,
        SessionState state,
        Duration elapsed,
        SessionProgress progress,
        String currentTopic
    ) {}
}

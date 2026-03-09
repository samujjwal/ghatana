package com.ghatana.virtualorg.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Pull request metadata for code review workflows.
 *
 * <p><b>Purpose</b><br>
 * Represents a pull request in the code review workflow.
 * Tracks title, description, author, reviewers, and status.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PullRequest pr = PullRequest.builder()
 *     .pullRequestId("pr-1234")
 *     .title("Add user authentication feature")
 *     .description("Implements OAuth2 authentication with JWT tokens")
 *     .author("engineer@company.com")
 *     .targetBranch("main")
 *     .sourceBranch("feature/auth")
 *     .addReviewer("senior@company.com")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @param pullRequestId Unique PR identifier
 * @param title PR title
 * @param description Detailed description of changes
 * @param author PR author email/username
 * @param sourceBranch Source branch name
 * @param targetBranch Target branch name (e.g., "main", "develop")
 * @param reviewers List of required reviewers
 * @param createdAt When PR was created
 *
 * @doc.type record
 * @doc.purpose Pull request metadata for code review workflows
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record PullRequest(
    String pullRequestId,
    String title,
    String description,
    String author,
    String sourceBranch,
    String targetBranch,
    List<String> reviewers,
    Instant createdAt,
    int linesOfCode,
    int filesChanged,
    boolean hasArchitecturalChanges,
    boolean introducesNewPatterns,
    double testCoveragePct,
    String codeDiff,
    String architecturalChangesDescription,
    String newPatternsDescription,
    String systemImpactDescription,
    int unitTestsCount,
    int integrationTestsCount,
    int e2ETestsCount,
    String optionId
) {
    /**
     * Compact constructor for validation.
     */
    public PullRequest {
        Objects.requireNonNull(pullRequestId, "pullRequestId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(sourceBranch, "sourceBranch must not be null");
        Objects.requireNonNull(targetBranch, "targetBranch must not be null");
        reviewers = reviewers != null ? List.copyOf(reviewers) : List.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Gets PR ID (convenience getter).
     */
    public String getId() {
        return pullRequestId;
    }

    /**
     * Gets author (convenience getter).
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Gets title (convenience getter).
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets lines of code changed.
     */
    public int getLinesOfCode() {
        return linesOfCode;
    }

    /**
     * Gets number of files changed.
     */
    public int getFilesChanged() {
        return filesChanged;
    }

    /**
     * Checks if PR has architectural changes.
     */
    public boolean hasArchitecturalChanges() {
        return hasArchitecturalChanges;
    }

    /**
     * Checks if PR introduces new design patterns.
     */
    public boolean introducesNewPatterns() {
        return introducesNewPatterns;
    }

    /**
     * Gets test coverage percentage.
     */
    public double getTestCoveragePct() {
        return testCoveragePct;
    }

    /**
     * Gets description (convenience getter).
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets code diff (convenience getter).
     */
    public String getCodeDiff() {
        return codeDiff;
    }

    /**
     * Gets architectural changes description (convenience getter).
     */
    public String getArchitecturalChangesDescription() {
        return architecturalChangesDescription;
    }

    /**
     * Gets new patterns description (convenience getter).
     */
    public String getNewPatternsDescription() {
        return newPatternsDescription;
    }

    /**
     * Gets system impact description (convenience getter).
     */
    public String getSystemImpactDescription() {
        return systemImpactDescription;
    }

    /**
     * Gets unit tests count (convenience getter).
     */
    public int getUnitTestsCount() {
        return unitTestsCount;
    }

    /**
     * Gets integration tests count (convenience getter).
     */
    public int getIntegrationTestsCount() {
        return integrationTestsCount;
    }

    /**
     * Gets E2E tests count (convenience getter).
     */
    public int getE2ETestsCount() {
        return e2ETestsCount;
    }

    /**
     * Gets option ID (convenience getter).
     */
    public String getOptionId() {
        return optionId;
    }
    
    /**
     * Creates a builder for PullRequest.
     *
     * @return New Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for PullRequest instances.
     */
    public static class Builder {
        private String pullRequestId;
        private String title;
        private String description;
        private String author;
        private String sourceBranch;
        private String targetBranch;
        private List<String> reviewers = List.of();
        private Instant createdAt;
        private int linesOfCode = 0;
        private int filesChanged = 0;
        private boolean hasArchitecturalChanges = false;
        private boolean introducesNewPatterns = false;
        private double testCoveragePct = 0.0;
        private String codeDiff = "";
        private String architecturalChangesDescription = "";
        private String newPatternsDescription = "";
        private String systemImpactDescription = "";
        private int unitTestsCount = 0;
        private int integrationTestsCount = 0;
        private int e2ETestsCount = 0;
        private String optionId = "";
        
        public Builder pullRequestId(String pullRequestId) {
            this.pullRequestId = pullRequestId;
            return this;
        }
        
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder sourceBranch(String sourceBranch) {
            this.sourceBranch = sourceBranch;
            return this;
        }
        
        public Builder targetBranch(String targetBranch) {
            this.targetBranch = targetBranch;
            return this;
        }
        
        public Builder reviewers(List<String> reviewers) {
            this.reviewers = reviewers;
            return this;
        }
        
        public Builder addReviewer(String reviewer) {
            if (this.reviewers.isEmpty()) {
                this.reviewers = new java.util.ArrayList<>();
            } else if (!(this.reviewers instanceof java.util.ArrayList)) {
                this.reviewers = new java.util.ArrayList<>(this.reviewers);
            }
            ((java.util.ArrayList<String>) this.reviewers).add(reviewer);
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder linesOfCode(int linesOfCode) {
            this.linesOfCode = linesOfCode;
            return this;
        }

        public Builder filesChanged(int filesChanged) {
            this.filesChanged = filesChanged;
            return this;
        }

        public Builder hasArchitecturalChanges(boolean hasArchitecturalChanges) {
            this.hasArchitecturalChanges = hasArchitecturalChanges;
            return this;
        }

        public Builder introducesNewPatterns(boolean introducesNewPatterns) {
            this.introducesNewPatterns = introducesNewPatterns;
            return this;
        }

        public Builder testCoveragePct(double testCoveragePct) {
            this.testCoveragePct = testCoveragePct;
            return this;
        }

        public Builder codeDiff(String codeDiff) {
            this.codeDiff = codeDiff;
            return this;
        }

        public Builder architecturalChangesDescription(String architecturalChangesDescription) {
            this.architecturalChangesDescription = architecturalChangesDescription;
            return this;
        }

        public Builder newPatternsDescription(String newPatternsDescription) {
            this.newPatternsDescription = newPatternsDescription;
            return this;
        }

        public Builder systemImpactDescription(String systemImpactDescription) {
            this.systemImpactDescription = systemImpactDescription;
            return this;
        }

        public Builder unitTestsCount(int unitTestsCount) {
            this.unitTestsCount = unitTestsCount;
            return this;
        }

        public Builder integrationTestsCount(int integrationTestsCount) {
            this.integrationTestsCount = integrationTestsCount;
            return this;
        }

        public Builder e2ETestsCount(int e2ETestsCount) {
            this.e2ETestsCount = e2ETestsCount;
            return this;
        }

        public Builder optionId(String optionId) {
            this.optionId = optionId;
            return this;
        }
        
        public PullRequest build() {
            return new PullRequest(
                pullRequestId, title, description, author,
                sourceBranch, targetBranch, reviewers, createdAt,
                linesOfCode, filesChanged, hasArchitecturalChanges,
                introducesNewPatterns, testCoveragePct,
                codeDiff, architecturalChangesDescription, newPatternsDescription,
                systemImpactDescription, unitTestsCount, integrationTestsCount,
                e2ETestsCount, optionId
            );
        }
    }
}

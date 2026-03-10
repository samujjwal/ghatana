package com.ghatana.yappc.core.release;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automated Release Management System Week 11 Day 51: Release automation with semantic versioning
 * and changelog generation
 *
 * @doc.type class
 * @doc.purpose Automated Release Management System - release automation with semantic versioning
 * @doc.layer platform
 * @doc.pattern Manager
 */
public class ReleaseAutomationManager {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseAutomationManager.class);

    /**
     * Pattern that commit messages must match to prevent argument injection. Allows conventional
     * commit message characters: alphanumeric, spaces, hyphens, underscores, colons, parens,
     * brackets, periods, exclamation marks, slashes (for scope paths), and newlines.
     */
    private static final Pattern SAFE_COMMIT_MSG_PATTERN =
            Pattern.compile("^[\\w\\s\\-_\\[\\]().,:/!#*+@=<>{}|~`^%&\\n]{1,500}$");

    @SuppressWarnings("unused")
    private final Path projectRoot;

    private final VersionManager versionManager;
    private final ChangelogGenerator changelogGenerator;
    private final GitOperations gitOps;

    public ReleaseAutomationManager(Path projectRoot) {
        this.projectRoot = projectRoot;
        this.versionManager = new VersionManager(projectRoot);
        this.changelogGenerator = new ChangelogGenerator(projectRoot);
        this.gitOps = new GitOperations(projectRoot);
    }

    /**
 * Perform automated release with version bumping and changelog generation */
    public ReleaseResult performRelease(ReleaseType releaseType, boolean dryRun) {
        try {
            logger.info("Starting {} release process (dry-run: {})", releaseType, dryRun);

            // Pre-release validations
            validatePreReleaseConditions();

            // Get current version
            SemanticVersion currentVersion = versionManager.getCurrentVersion();
            logger.info("Current version: {}", currentVersion);

            // Calculate next version
            SemanticVersion nextVersion = calculateNextVersion(currentVersion, releaseType);
            logger.info("Next version: {}", nextVersion);

            // Generate changelog
            ChangelogEntry changelogEntry =
                    changelogGenerator.generateChangelog(
                            currentVersion.toString(), nextVersion.toString());

            if (!dryRun) {
                // Update version files
                versionManager.updateVersion(nextVersion);

                // Update changelog
                changelogGenerator.updateChangelogFile(changelogEntry);

                // Commit changes
                gitOps.commitRelease(nextVersion, changelogEntry);

                // Create tag
                gitOps.createTag(nextVersion);

                // Push changes
                gitOps.pushRelease(nextVersion);
            }

            return ReleaseResult.success(currentVersion, nextVersion, changelogEntry, dryRun);

        } catch (Exception e) {
            logger.error("Release process failed", e);
            return ReleaseResult.failure(e.getMessage());
        }
    }

    /**
 * Validate pre-release conditions */
    private void validatePreReleaseConditions() throws ReleaseException {
        // Check if working directory is clean
        if (gitOps.hasUncommittedChanges()) {
            throw new ReleaseException("Working directory has uncommitted changes");
        }

        // Check if on main/master branch
        String currentBranch = gitOps.getCurrentBranch();
        if (!isReleaseBranch(currentBranch)) {
            throw new ReleaseException("Not on a release branch. Current branch: " + currentBranch);
        }

        // Check if remote is up to date
        if (gitOps.hasUnpushedCommits()) {
            throw new ReleaseException(
                    "Local branch is ahead of remote. Please push or pull first.");
        }
    }

    private boolean isReleaseBranch(String branch) {
        return "main".equals(branch) || "master".equals(branch) || branch.startsWith("release/");
    }

    /**
 * Calculate next version based on release type */
    private SemanticVersion calculateNextVersion(SemanticVersion current, ReleaseType releaseType) {
        return switch (releaseType) {
            case MAJOR -> current.incrementMajor();
            case MINOR -> current.incrementMinor();
            case PATCH -> current.incrementPatch();
            case SNAPSHOT -> current.toSnapshot();
            case RELEASE -> current.toRelease();
        };
    }

    /**
 * Version Manager for handling semantic versioning */
    public static class VersionManager {
        private final Path projectRoot;
        private final Pattern versionPattern =
                Pattern.compile("version\\s*=\\s*['\"]([^'\"]+)['\"]");

        public VersionManager(Path projectRoot) {
            this.projectRoot = projectRoot;
        }

        public SemanticVersion getCurrentVersion() throws IOException {
            // Check gradle.properties first
            Path gradleProps = projectRoot.resolve("gradle.properties");
            if (Files.exists(gradleProps)) {
                return parseVersionFromFile(gradleProps);
            }

            // Check build.gradle
            Path buildGradle = projectRoot.resolve("build.gradle");
            if (Files.exists(buildGradle)) {
                return parseVersionFromFile(buildGradle);
            }

            // Default version if none found
            return new SemanticVersion(0, 1, 0);
        }

        private SemanticVersion parseVersionFromFile(Path file) throws IOException {
            String content = Files.readString(file);
            Matcher matcher = versionPattern.matcher(content);

            if (matcher.find()) {
                String versionStr = matcher.group(1);
                return SemanticVersion.parse(versionStr);
            }

            return new SemanticVersion(0, 1, 0);
        }

        public void updateVersion(SemanticVersion newVersion) throws IOException {
            updateVersionInFile(projectRoot.resolve("gradle.properties"), newVersion);
            updateVersionInFile(projectRoot.resolve("build.gradle"), newVersion);
        }

        private void updateVersionInFile(Path file, SemanticVersion newVersion) throws IOException {
            if (!Files.exists(file)) {
                return;
            }

            String content = Files.readString(file);
            String newContent =
                    versionPattern
                            .matcher(content)
                            .replaceAll("version = \"" + newVersion.toString() + "\"");

            Files.writeString(file, newContent);
        }
    }

    /**
 * Changelog Generator */
    public static class ChangelogGenerator {
        private final Path projectRoot;
        private final GitOperations gitOps;

        public ChangelogGenerator(Path projectRoot) {
            this.projectRoot = projectRoot;
            this.gitOps = new GitOperations(projectRoot);
        }

        public ChangelogEntry generateChangelog(String fromVersion, String toVersion) {
            List<CommitInfo> commits = gitOps.getCommitsSince(fromVersion);

            List<String> features = new ArrayList<>();
            List<String> bugfixes = new ArrayList<>();
            List<String> breakingChanges = new ArrayList<>();
            List<String> other = new ArrayList<>();

            for (CommitInfo commit : commits) {
                categorizeCommit(commit, features, bugfixes, breakingChanges, other);
            }

            return new ChangelogEntry(
                    toVersion, LocalDateTime.now(), features, bugfixes, breakingChanges, other);
        }

        private void categorizeCommit(
                CommitInfo commit,
                List<String> features,
                List<String> bugfixes,
                List<String> breakingChanges,
                List<String> other) {
            String message = commit.getMessage().toLowerCase();

            if (message.startsWith("feat") || message.contains("feature")) {
                features.add(formatCommitMessage(commit));
            } else if (message.startsWith("fix") || message.contains("bug")) {
                bugfixes.add(formatCommitMessage(commit));
            } else if (message.contains("breaking") || message.contains("!:")) {
                breakingChanges.add(formatCommitMessage(commit));
            } else {
                other.add(formatCommitMessage(commit));
            }
        }

        private String formatCommitMessage(CommitInfo commit) {
            return String.format(
                    "- %s (%s)", commit.getMessage(), commit.getHash().substring(0, 7));
        }

        public void updateChangelogFile(ChangelogEntry entry) throws IOException {
            Path changelogFile = projectRoot.resolve("CHANGELOG.md");

            String newSection = formatChangelogEntry(entry);

            if (Files.exists(changelogFile)) {
                String existingContent = Files.readString(changelogFile);
                String newContent = insertChangelogEntry(existingContent, newSection);
                Files.writeString(changelogFile, newContent);
            } else {
                String content =
                        "# Changelog\n\n"
                                + "All notable changes to this project will be documented in this"
                                + " file.\n\n"
                                + newSection;
                Files.writeString(changelogFile, content);
            }
        }

        private String formatChangelogEntry(ChangelogEntry entry) {
            StringBuilder sb = new StringBuilder();

            sb.append("## [")
                    .append(entry.getVersion())
                    .append("] - ")
                    .append(entry.getDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .append("\n\n");

            if (!entry.getBreakingChanges().isEmpty()) {
                sb.append("### 💥 BREAKING CHANGES\n");
                entry.getBreakingChanges().forEach(change -> sb.append(change).append("\n"));
                sb.append("\n");
            }

            if (!entry.getFeatures().isEmpty()) {
                sb.append("### ✨ Features\n");
                entry.getFeatures().forEach(feature -> sb.append(feature).append("\n"));
                sb.append("\n");
            }

            if (!entry.getBugfixes().isEmpty()) {
                sb.append("### 🐛 Bug Fixes\n");
                entry.getBugfixes().forEach(bugfix -> sb.append(bugfix).append("\n"));
                sb.append("\n");
            }

            if (!entry.getOther().isEmpty()) {
                sb.append("### 📝 Other Changes\n");
                entry.getOther().forEach(change -> sb.append(change).append("\n"));
                sb.append("\n");
            }

            return sb.toString();
        }

        private String insertChangelogEntry(String existingContent, String newEntry) {
            // Insert after the header but before the first existing entry
            int insertIndex = existingContent.indexOf("## [");
            if (insertIndex == -1) {
                // No existing entries, append to end
                return existingContent + "\n" + newEntry;
            } else {
                // Insert before first existing entry
                return existingContent.substring(0, insertIndex)
                        + newEntry
                        + "\n"
                        + existingContent.substring(insertIndex);
            }
        }
    }

    /**
 * Git Operations wrapper */
    public static class GitOperations {
        private final Path projectRoot;

        public GitOperations(Path projectRoot) {
            this.projectRoot = projectRoot;
        }

        public boolean hasUncommittedChanges() {
            try {
                ProcessBuilder pb = new ProcessBuilder("git", "status", "--porcelain");
                pb.directory(projectRoot.toFile());
                Process process = pb.start();
                process.waitFor();

                return process.getInputStream().available() > 0;
            } catch (Exception e) {
                return true; // Assume changes if we can't check
            }
        }

        public String getCurrentBranch() {
            try {
                ProcessBuilder pb = new ProcessBuilder("git", "branch", "--show-current");
                pb.directory(projectRoot.toFile());
                Process process = pb.start();
                process.waitFor();

                return new String(process.getInputStream().readAllBytes()).trim();
            } catch (Exception e) {
                return "unknown";
            }
        }

        public boolean hasUnpushedCommits() {
            try {
                ProcessBuilder pb =
                        new ProcessBuilder("git", "log", "HEAD", "^origin/HEAD", "--oneline");
                pb.directory(projectRoot.toFile());
                Process process = pb.start();
                process.waitFor();

                return process.getInputStream().available() > 0;
            } catch (Exception e) {
                return false;
            }
        }

        public List<CommitInfo> getCommitsSince(String fromVersion) {
            List<CommitInfo> commits = new ArrayList<>();

            try {
                String refSpec = fromVersion.isEmpty() ? "HEAD" : fromVersion + "..HEAD";
                ProcessBuilder pb =
                        new ProcessBuilder("git", "log", refSpec, "--oneline", "--no-merges");
                pb.directory(projectRoot.toFile());
                Process process = pb.start();
                process.waitFor();

                String output = new String(process.getInputStream().readAllBytes());
                String[] lines = output.split("\n");

                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        String[] parts = line.split(" ", 2);
                        if (parts.length >= 2) {
                            commits.add(new CommitInfo(parts[0], parts[1]));
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get commits since {}: {}", fromVersion, e.getMessage());
            }

            return commits;
        }

        public void commitRelease(SemanticVersion version, ChangelogEntry changelog)
                throws IOException {
            try {
                // Add changed files
                ProcessBuilder addPb =
                        new ProcessBuilder(
                                "git", "add", "gradle.properties", "build.gradle", "CHANGELOG.md");
                addPb.directory(projectRoot.toFile());
                addPb.start().waitFor();

                // Build commit message from structured components — never from raw user input
                String versionStr = sanitizeVersionString(version.toString());
                String commitMessage =
                        String.format(
                                "chore: release version %s\n\n%s",
                                versionStr, formatCommitBody(changelog));

                // Security: validate final commit message before passing to git
                validateCommitMessage(commitMessage);

                // Pass message as a separate argument (not shell-interpolated)
                ProcessBuilder commitPb = new ProcessBuilder("git", "commit", "-m", commitMessage);
                commitPb.directory(projectRoot.toFile());
                commitPb.start().waitFor();

            } catch (SecurityException se) {
                throw new IOException("Commit message security validation failed: " + se.getMessage(), se);
            } catch (Exception e) {
                throw new IOException("Failed to commit release", e);
            }
        }

        /**
         * Validates that a commit message does not contain characters that could be used for
         * argument injection when passed to git via ProcessBuilder.
         *
         * @param commitMessage the message to validate
         * @throws SecurityException if the message contains unsafe characters
         */
        private static void validateCommitMessage(String commitMessage) {
            if (commitMessage == null || commitMessage.isBlank()) {
                throw new SecurityException("Commit message must not be null or blank");
            }
            if (commitMessage.length() > 2000) {
                throw new SecurityException("Commit message exceeds maximum length of 2000 characters");
            }
            // Reject null bytes and other control characters (except newline and tab)
            for (char c : commitMessage.toCharArray()) {
                if (c == '\0' || (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r')) {
                    throw new SecurityException(
                            "Commit message contains illegal control character: 0x"
                                    + Integer.toHexString(c));
                }
            }
        }

        /**
         * Sanitizes a semver string to contain only safe characters (digits, dots, hyphens,
         * alphanumeric). Guards against version strings being injected via configuration files.
         */
        private static String sanitizeVersionString(String version) {
            if (!version.matches("[0-9a-zA-Z.\\-+]+")) {
                throw new SecurityException("Version string contains unsafe characters: " + version);
            }
            return version;
        }

        private String formatCommitBody(ChangelogEntry changelog) {
            StringBuilder sb = new StringBuilder();

            if (!changelog.getFeatures().isEmpty()) {
                sb.append("Features:\n");
                changelog.getFeatures().forEach(f -> sb.append("- ").append(f).append("\n"));
            }

            if (!changelog.getBugfixes().isEmpty()) {
                sb.append("Bug Fixes:\n");
                changelog.getBugfixes().forEach(f -> sb.append("- ").append(f).append("\n"));
            }

            return sb.toString();
        }

        public void createTag(SemanticVersion version) throws IOException {
            try {
                ProcessBuilder pb =
                        new ProcessBuilder(
                                "git",
                                "tag",
                                "-a",
                                "v" + version.toString(),
                                "-m",
                                "Release " + version.toString());
                pb.directory(projectRoot.toFile());
                pb.start().waitFor();
            } catch (Exception e) {
                throw new IOException("Failed to create tag", e);
            }
        }

        public void pushRelease(SemanticVersion version) throws IOException {
            try {
                // Push commits
                ProcessBuilder pushPb = new ProcessBuilder("git", "push");
                pushPb.directory(projectRoot.toFile());
                pushPb.start().waitFor();

                // Push tags
                ProcessBuilder pushTagsPb = new ProcessBuilder("git", "push", "--tags");
                pushTagsPb.directory(projectRoot.toFile());
                pushTagsPb.start().waitFor();

            } catch (Exception e) {
                throw new IOException("Failed to push release", e);
            }
        }
    }

    // Data classes and enums

    public enum ReleaseType {
        MAJOR,
        MINOR,
        PATCH,
        SNAPSHOT,
        RELEASE
    }

    public static class SemanticVersion {
        private final int major;
        private final int minor;
        private final int patch;
        private final String suffix;

        public SemanticVersion(int major, int minor, int patch) {
            this(major, minor, patch, "");
        }

        public SemanticVersion(int major, int minor, int patch, String suffix) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.suffix = suffix != null ? suffix : "";
        }

        public static SemanticVersion parse(String version) {
            String cleanVersion = version.startsWith("v") ? version.substring(1) : version;

            String[] parts = cleanVersion.split("\\.");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid version format: " + version);
            }

            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);

            // Handle patch with potential suffix (e.g., "0-SNAPSHOT")
            String patchStr = parts[2];
            int patch;
            String suffix = "";

            int dashIndex = patchStr.indexOf('-');
            if (dashIndex > 0) {
                patch = Integer.parseInt(patchStr.substring(0, dashIndex));
                suffix = patchStr.substring(dashIndex);
            } else {
                patch = Integer.parseInt(patchStr);
            }

            return new SemanticVersion(major, minor, patch, suffix);
        }

        public SemanticVersion incrementMajor() {
            return new SemanticVersion(major + 1, 0, 0);
        }

        public SemanticVersion incrementMinor() {
            return new SemanticVersion(major, minor + 1, 0);
        }

        public SemanticVersion incrementPatch() {
            return new SemanticVersion(major, minor, patch + 1);
        }

        public SemanticVersion toSnapshot() {
            return new SemanticVersion(major, minor, patch, "-SNAPSHOT");
        }

        public SemanticVersion toRelease() {
            return new SemanticVersion(major, minor, patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch + suffix;
        }

        // Getters
        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public int getPatch() {
            return patch;
        }

        public String getSuffix() {
            return suffix;
        }
    }

    public static class CommitInfo {
        private final String hash;
        private final String message;

        public CommitInfo(String hash, String message) {
            this.hash = hash;
            this.message = message;
        }

        public String getHash() {
            return hash;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class ChangelogEntry {
        private final String version;
        private final LocalDateTime date;
        private final List<String> features;
        private final List<String> bugfixes;
        private final List<String> breakingChanges;
        private final List<String> other;

        public ChangelogEntry(
                String version,
                LocalDateTime date,
                List<String> features,
                List<String> bugfixes,
                List<String> breakingChanges,
                List<String> other) {
            this.version = version;
            this.date = date;
            this.features = features;
            this.bugfixes = bugfixes;
            this.breakingChanges = breakingChanges;
            this.other = other;
        }

        // Getters
        public String getVersion() {
            return version;
        }

        public LocalDateTime getDate() {
            return date;
        }

        public List<String> getFeatures() {
            return features;
        }

        public List<String> getBugfixes() {
            return bugfixes;
        }

        public List<String> getBreakingChanges() {
            return breakingChanges;
        }

        public List<String> getOther() {
            return other;
        }
    }

    public static class ReleaseResult {
        private final boolean success;
        private final SemanticVersion currentVersion;
        private final SemanticVersion newVersion;
        private final ChangelogEntry changelog;
        private final boolean dryRun;
        private final String errorMessage;

        private ReleaseResult(
                boolean success,
                SemanticVersion currentVersion,
                SemanticVersion newVersion,
                ChangelogEntry changelog,
                boolean dryRun,
                String errorMessage) {
            this.success = success;
            this.currentVersion = currentVersion;
            this.newVersion = newVersion;
            this.changelog = changelog;
            this.dryRun = dryRun;
            this.errorMessage = errorMessage;
        }

        public static ReleaseResult success(
                SemanticVersion currentVersion,
                SemanticVersion newVersion,
                ChangelogEntry changelog,
                boolean dryRun) {
            return new ReleaseResult(true, currentVersion, newVersion, changelog, dryRun, null);
        }

        public static ReleaseResult failure(String errorMessage) {
            return new ReleaseResult(false, null, null, null, false, errorMessage);
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public SemanticVersion getCurrentVersion() {
            return currentVersion;
        }

        public SemanticVersion getNewVersion() {
            return newVersion;
        }

        public ChangelogEntry getChangelog() {
            return changelog;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static class ReleaseException extends Exception {
        public ReleaseException(String message) {
            super(message);
        }

        public ReleaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.release.ReleaseAutomationManager;
import com.ghatana.yappc.core.release.ReleaseAutomationManager.ReleaseResult;
import com.ghatana.yappc.core.release.ReleaseAutomationManager.ReleaseType;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Release Command for automated release management Week 11 Day 51: Release automation CLI */
@Command(
        name = "release",
        description = "Automated release management with semantic versioning",
        subcommands = {
            ReleaseCommand.CreateCommand.class,
            ReleaseCommand.StatusCommand.class,
            ReleaseCommand.ValidateCommand.class
        })
/**
 * ReleaseCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose ReleaseCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ReleaseCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ReleaseCommand.class);

    @Override
    public Integer call() {
        logger.info("YAPPC Release Automation");
        logger.info("Available commands:");
        logger.info("  create    - Create a new release");
        logger.info("  status    - Show current release status");
        logger.info("  validate  - Validate release readiness");
        return 0;
    }

    @Command(name = "create", description = "Create a new release")
    static class CreateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Release type: major, minor, patch")
        private String releaseTypeStr;

        @Option(
                names = {"-d", "--dry-run"},
                description = "Perform dry run without making changes")
        private boolean dryRun;

        @Option(
                names = {"-p", "--project-path"},
                description = "Project root path",
                defaultValue = ".")
        private String projectPath;

        @Override
        public Integer call() {
            try {
                ReleaseType releaseType = parseReleaseType(releaseTypeStr);

                ReleaseAutomationManager releaseManager =
                        new ReleaseAutomationManager(Paths.get(projectPath).toAbsolutePath());

                logger.info("🚀 Starting {} release...", releaseType.name().toLowerCase());
                if (dryRun) {
                    logger.info("📋 DRY RUN MODE - No changes will be made");
                }

                ReleaseResult result = releaseManager.performRelease(releaseType, dryRun);

                if (result.isSuccess()) {
                    printSuccessResult(result);
                    return 0;
                } else {
                    logger.error("❌ Release failed: {}", result.getErrorMessage());
                    return 1;
                }

            } catch (Exception e) {
                logger.error("Release command failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }

        private ReleaseType parseReleaseType(String typeStr) {
            return switch (typeStr.toLowerCase()) {
                case "major" -> ReleaseType.MAJOR;
                case "minor" -> ReleaseType.MINOR;
                case "patch" -> ReleaseType.PATCH;
                case "snapshot" -> ReleaseType.SNAPSHOT;
                case "release" -> ReleaseType.RELEASE;
                default -> throw new IllegalArgumentException("Invalid release type: " + typeStr);
            };
        }

        private void printSuccessResult(ReleaseResult result) {
            logger.info("✅ Release completed successfully!");
            logger.info("");;

            if (result.isDryRun()) {
                logger.info("📋 DRY RUN SUMMARY:");
            } else {
                logger.info("📦 RELEASE SUMMARY:");
            }

            logger.info("-".repeat(40));
            logger.info("Previous Version: {}", result.getCurrentVersion());
            logger.info("New Version:      {}", result.getNewVersion());
            logger.info("");;

            if (result.getChangelog() != null) {
                printChangelogSummary(result.getChangelog());
            }

            if (!result.isDryRun()) {
                logger.info("🏷️  Git tag created: v{}", result.getNewVersion());
                logger.info("📝 Changelog updated");
                logger.info("🔄 Changes pushed to remote");
            }
        }

        private void printChangelogSummary(ReleaseAutomationManager.ChangelogEntry changelog) {
            logger.info("📋 CHANGELOG SUMMARY:");

            if (!changelog.getBreakingChanges().isEmpty()) {
                logger.info("💥 Breaking Changes: {}", changelog.getBreakingChanges().size());
            }

            if (!changelog.getFeatures().isEmpty()) {
                logger.info("✨ Features: {}", changelog.getFeatures().size());
            }

            if (!changelog.getBugfixes().isEmpty()) {
                logger.info("🐛 Bug Fixes: {}", changelog.getBugfixes().size());
            }

            if (!changelog.getOther().isEmpty()) {
                logger.info("📝 Other Changes: {}", changelog.getOther().size());
            }

            logger.info("");;
        }
    }

    @Command(name = "status", description = "Show current release status")
    static class StatusCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--project-path"},
                description = "Project root path",
                defaultValue = ".")
        private String projectPath;

        @Override
        public Integer call() {
            try {
                // Get current version
                var versionManager =
                        new ReleaseAutomationManager.VersionManager(
                                Paths.get(projectPath).toAbsolutePath());

                var currentVersion = versionManager.getCurrentVersion();

                logger.info("📦 RELEASE STATUS");
                logger.info("-".repeat(30));
                logger.info("Current Version: {}", currentVersion);
                logger.info("Project Path:    {}", Paths.get(projectPath).toAbsolutePath());

                // Check git status
                var gitOps =
                        new ReleaseAutomationManager.GitOperations(
                                Paths.get(projectPath).toAbsolutePath());

                String branch = gitOps.getCurrentBranch();
                boolean hasUncommitted = gitOps.hasUncommittedChanges();
                boolean hasUnpushed = gitOps.hasUnpushedCommits();

                logger.info("Git Branch:      {}", branch);
                logger.info("Uncommitted:     {}", (hasUncommitted ? "❌ YES" : "✅ NO"));
                logger.info("Unpushed:        {}", (hasUnpushed ? "❌ YES" : "✅ NO"));

                boolean ready =
                        !hasUncommitted
                                && !hasUnpushed
                                && (branch.equals("main") || branch.equals("master"));

                logger.info("");;
                logger.info("Release Ready:   {}", (ready ? "✅ YES" : "❌ NO"));

                if (!ready) {
                    logger.info("");;
                    logger.info("⚠️  Issues preventing release:");
                    if (hasUncommitted) {
                        logger.info("   - Uncommitted changes present");
                    }
                    if (hasUnpushed) {
                        logger.info("   - Unpushed commits present");
                    }
                    if (!branch.equals("main") && !branch.equals("master")) {
                        logger.info("   - Not on main/master branch");
                    }
                }

                return 0;

            } catch (Exception e) {
                logger.error("Status command failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "validate", description = "Validate release readiness")
    static class ValidateCommand implements Callable<Integer> {

        @Option(
                names = {"-p", "--project-path"},
                description = "Project root path",
                defaultValue = ".")
        private String projectPath;

        @Override
        public Integer call() {
            try {
                logger.info("🔍 VALIDATING RELEASE READINESS");
                logger.info("-".repeat(35));

                var gitOps =
                        new ReleaseAutomationManager.GitOperations(
                                Paths.get(projectPath).toAbsolutePath());

                boolean allValid = true;

                // Check working directory
                System.out.print("Working directory clean:     ");
                boolean workingDirClean = !gitOps.hasUncommittedChanges();
                logger.info("{}", workingDirClean ? "✅ PASS" : "❌ FAIL");
                allValid &= workingDirClean;

                // Check branch
                System.out.print("On release branch:           ");
                String branch = gitOps.getCurrentBranch();
                boolean onReleaseBranch =
                        branch.equals("main")
                                || branch.equals("master")
                                || branch.startsWith("release/");
                logger.info("{}{})", onReleaseBranch ? "✅ PASS" : "❌ FAIL (", branch);
                allValid &= onReleaseBranch;

                // Check remote sync
                System.out.print("In sync with remote:         ");
                boolean inSync = !gitOps.hasUnpushedCommits();
                logger.info("{}", inSync ? "✅ PASS" : "❌ FAIL");
                allValid &= inSync;

                // Check version file exists
                System.out.print("Version file exists:         ");
                boolean versionExists =
                        java.nio.file.Files.exists(
                                        Paths.get(projectPath).resolve("gradle.properties"))
                                || java.nio.file.Files.exists(
                                        Paths.get(projectPath).resolve("build.gradle"));
                logger.info("{}", versionExists ? "✅ PASS" : "❌ FAIL");
                allValid &= versionExists;

                logger.info("");;
                logger.info("Overall Status: {}", (allValid ? "✅ READY TO RELEASE" : "❌ NOT READY"));

                if (!allValid) {
                    logger.info("");;
                    logger.info("🛠️  Run 'yappc release status' for detailed information");
                }

                return allValid ? 0 : 1;

            } catch (Exception e) {
                logger.error("Validation command failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }
    }
}

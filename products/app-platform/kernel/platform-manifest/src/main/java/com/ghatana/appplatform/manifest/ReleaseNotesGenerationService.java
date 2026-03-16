package com.ghatana.appplatform.manifest;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.regex.*;

/**
 * @doc.type    Service
 * @doc.purpose Parse conventional commits for a platform version and generate structured release notes.
 *              Sections: Highlights, Features, Bug Fixes, Breaking Changes, Security Fixes, Migration Notes, Version Table.
 *              Notes are published to the operator portal and delivered via tenant email notification.
 * @doc.layer   Platform Manifest (PU-004)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-PU004-004: Release notes generation from conventional commits
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS platform_release_notes (
 *   notes_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   version        TEXT NOT NULL UNIQUE,
 *   summary        TEXT NOT NULL,
 *   highlights     JSONB,              -- top-N most impactful commits
 *   features       JSONB,              -- feat: lines
 *   bug_fixes      JSONB,              -- fix: lines
 *   breaking_changes JSONB,            -- BREAKING CHANGE: lines
 *   security_fixes JSONB,              -- sec: lines
 *   migration_notes TEXT,
 *   version_table  JSONB,              -- key component version map
 *   published_at   TIMESTAMPTZ,
 *   notified_at    TIMESTAMPTZ,
 *   status         TEXT NOT NULL DEFAULT 'DRAFT'  -- DRAFT | PUBLISHED | NOTIFIED
 * );
 * </pre>
 */
public class ReleaseNotesGenerationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface CommitLogPort {
        /** Returns all commit messages between fromTag and toTag (inclusive). */
        List<String> getCommitMessages(String fromTag, String toTag) throws Exception;
    }

    public interface ComponentVersionPort {
        /** Returns current version map: component → version. */
        Map<String, String> getComponentVersions(String platformVersion) throws Exception;
    }

    public interface PortalPublishPort {
        /** Publishes release notes HTML/markdown to the operator portal. */
        void publishToPortal(String version, String renderedNotes) throws Exception;
    }

    public interface TenantNotificationPort {
        /** Sends release notes digest to all active tenants. */
        void notifyAllTenants(String version, String summary, boolean hasBreakingChanges) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String details) throws Exception;
    }

    // ── Conventional commit pattern ───────────────────────────────────────────

    private static final Pattern FEAT     = Pattern.compile("^feat(\\(.+\\))?!?:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIX      = Pattern.compile("^fix(\\(.+\\))?!?:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEC      = Pattern.compile("^sec(\\(.+\\))?!?:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BREAKING = Pattern.compile("BREAKING CHANGE:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCOPE    = Pattern.compile("^\\w+(\\((.+)\\))", Pattern.CASE_INSENSITIVE);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final CommitLogPort commits;
    private final ComponentVersionPort versions;
    private final PortalPublishPort portal;
    private final TenantNotificationPort notifier;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter notesGenerated;
    private final Counter notesPublished;

    public ReleaseNotesGenerationService(
        javax.sql.DataSource ds,
        CommitLogPort commits,
        ComponentVersionPort versions,
        PortalPublishPort portal,
        TenantNotificationPort notifier,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.commits       = commits;
        this.versions      = versions;
        this.portal        = portal;
        this.notifier      = notifier;
        this.audit         = audit;
        this.executor      = executor;
        this.notesGenerated = Counter.builder("manifest.release_notes.generated").register(registry);
        this.notesPublished = Counter.builder("manifest.release_notes.published").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generate release notes by parsing conventional commits between fromTag and toTag.
     * Persists as DRAFT — call {@link #publishAndNotify} to release.
     */
    public Promise<String> generate(String version, String fromTag, String toTag, String migrationNotes) {
        return Promise.ofBlocking(executor, () -> {
            List<String> messages = commits.getCommitMessages(fromTag, toTag);
            Map<String, String> versionTable = versions.getComponentVersions(version);

            List<String> features  = new ArrayList<>();
            List<String> fixes     = new ArrayList<>();
            List<String> security  = new ArrayList<>();
            List<String> breaking  = new ArrayList<>();

            for (String msg : messages) {
                Matcher fb = BREAKING.matcher(msg);
                if (fb.find()) { breaking.add(fb.group(1).trim()); continue; }

                Matcher ff = FEAT.matcher(msg);
                if (ff.matches()) { features.add(ff.group(2).trim()); continue; }

                Matcher fx = FIX.matcher(msg);
                if (fx.matches()) { fixes.add(fx.group(2).trim()); continue; }

                Matcher fs = SEC.matcher(msg);
                if (fs.matches()) { security.add(fs.group(2).trim()); }
            }

            // Highlights = top-3 features + all breaking changes
            List<String> highlights = new ArrayList<>();
            features.stream().limit(3).forEach(highlights::add);
            highlights.addAll(breaking);

            String summary = buildSummary(version, features.size(), fixes.size(), breaking.size(), security.size());

            String notesId = insertDraft(version, summary, highlights, features, fixes, breaking,
                security, migrationNotes == null ? "" : migrationNotes, versionTable);
            notesGenerated.increment();
            audit.audit("RELEASE_NOTES_GENERATED", "version=" + version + " id=" + notesId);
            return notesId;
        });
    }

    /**
     * Publish to operator portal and notify all active tenants.
     */
    public Promise<Void> publishAndNotify(String notesId) {
        return Promise.ofBlocking(executor, () -> {
            String[] row = getDraftRow(notesId);
            String version = row[0]; String summary = row[1]; boolean hasBreaking = !row[2].equals("[]");
            String rendered = row[3];

            portal.publishToPortal(version, rendered);
            markPublished(notesId);

            notifier.notifyAllTenants(version, summary, hasBreaking);
            markNotified(notesId);

            notesPublished.increment();
            audit.audit("RELEASE_NOTES_PUBLISHED", "version=" + version);
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildSummary(String version, int features, int fixes, int breaking, int security) {
        return "Platform " + version + ": " + features + " feature(s), " + fixes + " fix(es)" +
               (breaking > 0 ? ", " + breaking + " BREAKING CHANGE(S)" : "") +
               (security > 0 ? ", " + security + " security fix(es)" : "");
    }

    private String listToJson(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (String it : items) sb.append("\"").append(it.replace("\"", "\\\"")).append("\",");
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append("]").toString();
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append("}").toString();
    }

    private String insertDraft(String version, String summary, List<String> highlights,
                                List<String> features, List<String> fixes, List<String> breaking,
                                List<String> security, String migrationNotes, Map<String, String> vt) throws SQLException {
        String rendered = "# Release Notes " + version + "\n\n**Summary:** " + summary +
                          "\n\n**Highlights:** " + highlights + "\n\n**Features:** " + features +
                          "\n\n**Bug Fixes:** " + fixes + "\n\n**Breaking Changes:** " + breaking +
                          "\n\n**Security Fixes:** " + security + "\n\n**Migration Notes:** " + migrationNotes +
                          "\n\n**Version Table:** " + vt;
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO platform_release_notes " +
                 "(version,summary,highlights,features,bug_fixes,breaking_changes,security_fixes,migration_notes,version_table) " +
                 "VALUES (?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,?::jsonb) RETURNING notes_id"
             )) {
            ps.setString(1, version);
            ps.setString(2, summary);
            ps.setString(3, listToJson(highlights));
            ps.setString(4, listToJson(features));
            ps.setString(5, listToJson(fixes));
            ps.setString(6, listToJson(breaking));
            ps.setString(7, listToJson(security));
            ps.setString(8, migrationNotes);
            ps.setString(9, mapToJson(vt));
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private String[] getDraftRow(String notesId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT version, summary, breaking_changes::text, " +
                 "summary || ' features=' || COALESCE((SELECT array_to_string(array_agg(v), ',') FROM jsonb_array_elements_text(features) v),'') " +
                 "FROM platform_release_notes WHERE notes_id=?"
             )) {
            ps.setString(1, notesId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Release notes not found: " + notesId);
                return new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)};
            }
        }
    }

    private void markPublished(String notesId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE platform_release_notes SET status='PUBLISHED', published_at=NOW() WHERE notes_id=?"
             )) { ps.setString(1, notesId); ps.executeUpdate(); }
    }

    private void markNotified(String notesId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE platform_release_notes SET status='NOTIFIED', notified_at=NOW() WHERE notes_id=?"
             )) { ps.setString(1, notesId); ps.executeUpdate(); }
    }
}

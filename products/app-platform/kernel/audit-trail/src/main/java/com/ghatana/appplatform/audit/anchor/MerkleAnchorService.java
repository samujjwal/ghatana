package com.ghatana.appplatform.audit.anchor;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Logger;

/**
 * Produces and verifies Merkle tree anchors over a time-window of audit log entries.
 *
 * <p>The Merkle root is computed from the SHA-256 hashes of all audit entries in the window.
 * The root hash is stored in {@code audit_merkle_anchors} for offline verification.
 *
 * <p>Usage:
 * <pre>
 *   String root = merkleAnchorService.anchor(tenantId, windowStart, windowEnd);
 *   boolean valid = merkleAnchorService.verify(tenantId, windowStart, windowEnd, root);
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Merkle tree anchoring for audit log tamper detection (STORY-K07-015/016)
 * @doc.layer product
 * @doc.pattern Service
 */
public class MerkleAnchorService {

    private static final Logger LOG = Logger.getLogger(MerkleAnchorService.class.getName());

    private final DataSource dataSource;

    public MerkleAnchorService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Compute the Merkle root over all audit entries in the time window and persist it.
     *
     * @param tenantId    Tenant scope
     * @param windowStart Window start (inclusive)
     * @param windowEnd   Window end (inclusive)
     * @return computed Merkle root hex string
     */
    public String anchor(String tenantId, Instant windowStart, Instant windowEnd) {
        List<String> leafHashes = fetchEntryHashes(tenantId, windowStart, windowEnd);
        if (leafHashes.isEmpty()) {
            throw new IllegalStateException("No audit entries in window ["
                + windowStart + ", " + windowEnd + "] for tenant=" + tenantId);
        }

        String merkleRoot = computeMerkleRoot(leafHashes);
        persistAnchor(tenantId, windowStart, windowEnd, merkleRoot, leafHashes.size());

        LOG.info("[MerkleAnchorService] Anchored tenant=" + tenantId
            + " leaves=" + leafHashes.size() + " root=" + merkleRoot);
        return merkleRoot;
    }

    /**
     * Verify the Merkle root matches what was computed at anchor time.
     *
     * @param tenantId    Tenant scope
     * @param windowStart Window start
     * @param windowEnd   Window end
     * @param expectedRoot Expected root hash (from the stored anchor)
     * @return true if the current data matches the stored root
     */
    public boolean verify(String tenantId, Instant windowStart, Instant windowEnd, String expectedRoot) {
        List<String> leafHashes = fetchEntryHashes(tenantId, windowStart, windowEnd);
        if (leafHashes.isEmpty()) return false;
        String currentRoot = computeMerkleRoot(leafHashes);
        boolean matches = currentRoot.equalsIgnoreCase(expectedRoot);
        if (!matches) {
            LOG.warning("[MerkleAnchorService] Verification FAILED tenant=" + tenantId
                + " expected=" + expectedRoot + " computed=" + currentRoot);
        }
        return matches;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private List<String> fetchEntryHashes(String tenantId, Instant from, Instant to) {
        String sql = """
            SELECT entry_hash FROM audit_logs
             WHERE tenant_id = ? AND occurred_at >= ? AND occurred_at <= ?
             ORDER BY sequence_number ASC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            ps.setFetchSize(5000);
            List<String> hashes = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) hashes.add(rs.getString("entry_hash"));
            }
            return hashes;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch entry hashes for Merkle computation", e);
        }
    }

    /**
     * Standard binary Merkle tree construction:
     * - Leaf nodes = SHA-256 of each entry hash
     * - Internal nodes = SHA-256(leftChild + rightChild)
     * - Odd leaves are duplicated
     */
    private String computeMerkleRoot(List<String> leafHashes) {
        if (leafHashes.size() == 1) return sha256(leafHashes.get(0));

        List<String> level = new ArrayList<>();
        for (String h : leafHashes) level.add(sha256(h));

        while (level.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = (i + 1 < level.size()) ? level.get(i + 1) : left; // duplicate if odd
                nextLevel.add(sha256(left + right));
            }
            level = nextLevel;
        }
        return level.get(0);
    }

    private void persistAnchor(String tenantId, Instant windowStart, Instant windowEnd,
                                String merkleRoot, int leafCount) {
        String sql = """
            INSERT INTO audit_merkle_anchors
              (tenant_id, window_start, window_end, merkle_root, leaf_count, anchored_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT (tenant_id, window_start, window_end)
            DO UPDATE SET merkle_root = EXCLUDED.merkle_root,
                          leaf_count  = EXCLUDED.leaf_count,
                          anchored_at = NOW()
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setTimestamp(2, Timestamp.from(windowStart));
            ps.setTimestamp(3, Timestamp.from(windowEnd));
            ps.setString(4, merkleRoot);
            ps.setInt(5, leafCount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to persist Merkle anchor", e);
        }
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

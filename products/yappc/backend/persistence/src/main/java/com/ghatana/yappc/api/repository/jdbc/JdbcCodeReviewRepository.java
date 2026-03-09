/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.yappc.api.domain.CodeReview;
import com.ghatana.yappc.api.domain.CodeReview.*;
import com.ghatana.yappc.api.repository.CodeReviewRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JDBC implementation of CodeReviewRepository.
  *
 * @doc.type class
 * @doc.purpose jdbc code review repository
 * @doc.layer product
 * @doc.pattern Repository
 */
public class JdbcCodeReviewRepository implements CodeReviewRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcCodeReviewRepository.class);
    private static final String TABLE = "yappc.code_reviews";

    private final DataSource dataSource;
    private final Executor executor;
    private final ObjectMapper mapper;

    @Inject
    public JdbcCodeReviewRepository(DataSource dataSource, ObjectMapper mapper) {
        this.dataSource = dataSource;
        this.executor = Executors.newCachedThreadPool();
        this.mapper = mapper;
    }

    @Override
    public Promise<CodeReview> save(CodeReview review) {
        return Promise.ofBlocking(executor, () -> {
            if (review.getId() == null) {
                review.setId(UUID.randomUUID());
            }
            String sql = "INSERT INTO " + TABLE +
                " (id, tenant_id, project_id, story_id, author_id, title, description, status, " +
                "pr_number, repo_url, reviewers, comments, checks, created_at, updated_at) " +
                "VALUES (?, ?, ?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "title = EXCLUDED.title, description = EXCLUDED.description, status = EXCLUDED.status, " +
                "reviewers = EXCLUDED.reviewers, comments = EXCLUDED.comments, checks = EXCLUDED.checks, " +
                "updated_at = EXCLUDED.updated_at";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, review.getId());
                ps.setString(2, review.getTenantId());
                ps.setString(3, review.getProjectId());
                ps.setString(4, review.getStoryId());
                ps.setString(5, review.getAuthorId());
                ps.setString(6, review.getTitle());
                ps.setString(7, review.getDescription());
                ps.setString(8, review.getStatus() != null ? review.getStatus().name() : "OPEN");
                ps.setInt(9, review.getPullRequestNumber());
                ps.setString(10, review.getPullRequestUrl());
                ps.setString(11, mapper.writeValueAsString(review.getReviewers()));
                ps.setString(12, mapper.writeValueAsString(review.getComments()));
                ps.setString(13, mapper.writeValueAsString(review.getFileChanges()));
                ps.setTimestamp(14, Timestamp.from(review.getCreatedAt() != null ? review.getCreatedAt() : Instant.now()));
                ps.setTimestamp(15, Timestamp.from(Instant.now()));
                ps.executeUpdate();
                return review;
            }
        });
    }

    @Override
    public Promise<Optional<CodeReview>> findById(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public Promise<List<CodeReview>> findByProject(String tenantId, String projectId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?::uuid ORDER BY created_at DESC",
            tenantId, projectId);
    }

    @Override
    public Promise<List<CodeReview>> findByStory(String tenantId, String storyId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND story_id = ?::uuid ORDER BY created_at DESC",
            tenantId, storyId);
    }

    @Override
    public Promise<List<CodeReview>> findByAuthor(String tenantId, String authorId) {
        return queryList("SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND author_id = ? ORDER BY created_at DESC",
            tenantId, authorId);
    }

    @Override
    public Promise<List<CodeReview>> findByReviewer(String tenantId, String reviewerId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND reviewers @> ?::jsonb ORDER BY created_at DESC";
            List<CodeReview> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, "[{\"userId\":\"" + reviewerId + "\"}]");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<List<CodeReview>> findByStatus(String tenantId, String projectId, ReviewStatus status) {
        return queryList(
            "SELECT * FROM " + TABLE + " WHERE tenant_id = ? AND project_id = ?::uuid AND status = ? ORDER BY created_at DESC",
            tenantId, projectId, status.name());
    }

    @Override
    public Promise<List<CodeReview>> findPendingForUser(String tenantId, String userId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM " + TABLE +
                " WHERE tenant_id = ? AND status IN ('OPEN', 'IN_REVIEW') AND reviewers @> ?::jsonb ORDER BY created_at DESC";
            List<CodeReview> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setString(2, "[{\"userId\":\"" + userId + "\"}]");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    @Override
    public Promise<Boolean> delete(String tenantId, UUID id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM " + TABLE + " WHERE tenant_id = ? AND id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                ps.setObject(2, id);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private Promise<List<CodeReview>> queryList(String sql, String... params) {
        return Promise.ofBlocking(executor, () -> {
            List<CodeReview> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setString(i + 1, params[i]);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
            return results;
        });
    }

    private CodeReview mapRow(ResultSet rs) throws SQLException, IOException {
        CodeReview review = new CodeReview();
        review.setId(rs.getObject("id", UUID.class));
        review.setTenantId(rs.getString("tenant_id"));
        review.setProjectId(rs.getString("project_id"));
        review.setStoryId(rs.getString("story_id"));
        review.setAuthorId(rs.getString("author_id"));
        review.setTitle(rs.getString("title"));
        review.setDescription(rs.getString("description"));
        try { review.setStatus(ReviewStatus.valueOf(rs.getString("status"))); } catch (Exception ignored) {}
        review.setPullRequestNumber(rs.getInt("pr_number"));
        review.setPullRequestUrl(rs.getString("repo_url"));
        String reviewersJson = rs.getString("reviewers");
        if (reviewersJson != null) {
            review.setReviewers(mapper.readValue(reviewersJson, new TypeReference<List<Reviewer>>() {}));
        }
        String commentsJson = rs.getString("comments");
        if (commentsJson != null) {
            review.setComments(mapper.readValue(commentsJson, new TypeReference<List<ReviewComment>>() {}));
        }
        String checksJson = rs.getString("checks");
        if (checksJson != null) {
            review.setFileChanges(mapper.readValue(checksJson, new TypeReference<List<FileChange>>() {}));
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) review.setCreatedAt(created.toInstant());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) review.setUpdatedAt(updated.toInstant());
        return review;
    }
}

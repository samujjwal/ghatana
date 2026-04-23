/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JdbcHumanApprovalService}.
 *
 * <p>Uses a mock {@link DataSource} to verify that:
 * <ul>
 *   <li>Mutations (requestApproval, approve, reject) delegate to the in-memory parent // GH-90000
 *       <em>and</em> trigger JDBC persistence.</li>
 *   <li>Read methods fall back to the in-memory store when JDBC throws.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("JdbcHumanApprovalService Tests")
class JdbcHumanApprovalServiceTest extends EventloopTestBase {

    @Mock
    private AepEventPublisher publisher;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    private JdbcHumanApprovalService service;

    private static final ApprovalRequest.ApprovalContext CTX = new ApprovalRequest.ApprovalContext( // GH-90000
            "INTENT", "SHAPE", "Unit test block", List.of("criteria-1"), List.of());

    @BeforeEach
    void setup() { // GH-90000
        // Publisher fire-and-forget: always succeed
        when(publisher.publish(anyString(), anyString(), any())) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        service = new JdbcHumanApprovalService(publisher, dataSource, new ObjectMapper()); // GH-90000
    }

    @Test
    @DisplayName("requestApproval stores in-memory and attempts JDBC insert")
    void requestApprovalPersistsToJdbc() throws Exception { // GH-90000
        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000

        ApprovalRequest result = runPromise(() -> // GH-90000
                service.requestApproval("tenant-1", "proj-1", "agent-1", // GH-90000
                        ApprovalRequest.ApprovalType.PHASE_ADVANCE, CTX));

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.status()).isEqualTo(ApprovalRequest.ApprovalStatus.PENDING); // GH-90000
        assertThat(result.tenantId()).isEqualTo("tenant-1");
        assertThat(result.projectId()).isEqualTo("proj-1");

        // Verify JDBC insert was attempted
        verify(dataSource).getConnection(); // GH-90000
        verify(preparedStatement).executeUpdate(); // GH-90000
    }

    @Test
    @DisplayName("requestApproval still succeeds when JDBC insert fails (resilient)")
    void requestApprovalResilientOnJdbcFailure() throws Exception { // GH-90000
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("DB unavailable"));

        // Should NOT throw — just log warning
        ApprovalRequest result = runPromise(() -> // GH-90000
                service.requestApproval("tenant-1", "proj-1", null, // GH-90000
                        ApprovalRequest.ApprovalType.DEPLOYMENT, CTX));

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.isPending()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("approve updates in-memory state and attempts JDBC update")
    void approveUpdatesJdbc() throws Exception { // GH-90000
        // First, add a request to the in-memory store
        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000

        // Create the approval
        ApprovalRequest created = runPromise(() -> // GH-90000
                service.requestApproval("tenant-1", "proj-1", "agent", // GH-90000
                        ApprovalRequest.ApprovalType.PHASE_ADVANCE, CTX));

        // Approve it
        ApprovalRequest approved = runPromise(() -> // GH-90000
                service.approve("tenant-1", created.id(), "reviewer-1")); // GH-90000

        assertThat(approved.status()).isEqualTo(ApprovalRequest.ApprovalStatus.APPROVED); // GH-90000
        assertThat(approved.decidedBy()).isEqualTo("reviewer-1");
    }

    @Test
    @DisplayName("reject updates in-memory state and attempts JDBC update")
    void rejectUpdatesJdbc() throws Exception { // GH-90000
        when(dataSource.getConnection()).thenReturn(connection); // GH-90000
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000

        ApprovalRequest created = runPromise(() -> // GH-90000
                service.requestApproval("tenant-1", "proj-1", "agent", // GH-90000
                        ApprovalRequest.ApprovalType.RISK_ACCEPTANCE, CTX));

        ApprovalRequest rejected = runPromise(() -> // GH-90000
                service.reject("tenant-1", created.id(), "security-reviewer")); // GH-90000

        assertThat(rejected.status()).isEqualTo(ApprovalRequest.ApprovalStatus.REJECTED); // GH-90000
        assertThat(rejected.decidedBy()).isEqualTo("security-reviewer");
    }

    @Test
    @DisplayName("pendingFor falls back to in-memory when DB query throws")
    void pendingForFallsBackToInMemoryOnJdbcError() throws Exception { // GH-90000
        // Prime in-memory store via requestApproval
        when(dataSource.getConnection()) // GH-90000
                .thenReturn(connection)     // first call: insert succeeds // GH-90000
                .thenThrow(new java.sql.SQLException("DB unavailable"));  // second call: query fails

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000

        runPromise(() -> // GH-90000
                service.requestApproval("tenant-1", "proj-1", null, // GH-90000
                        ApprovalRequest.ApprovalType.PHASE_ADVANCE, CTX));

        // DB query will fail, falls back to in-memory
        List<ApprovalRequest> pending = service.pendingFor("tenant-1", "proj-1"); // GH-90000

        assertThat(pending).hasSize(1); // GH-90000
        assertThat(pending.get(0).projectId()).isEqualTo("proj-1");
    }

    @Test
    @DisplayName("findById falls back to in-memory on JDBC error")
    void findByIdFallsBackToInMemoryOnJdbcError() throws Exception { // GH-90000
        when(dataSource.getConnection()) // GH-90000
                .thenReturn(connection)     // insert // GH-90000
                .thenThrow(new java.sql.SQLException("DB down"));  // findById query

        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement); // GH-90000
        when(preparedStatement.executeUpdate()).thenReturn(1); // GH-90000

        ApprovalRequest created = runPromise(() -> // GH-90000
                service.requestApproval("tenant-1", "proj-1", null, // GH-90000
                        ApprovalRequest.ApprovalType.PHASE_ADVANCE, CTX));

        Optional<ApprovalRequest> found = service.findById("tenant-1", created.id()); // GH-90000

        assertThat(found).isPresent(); // GH-90000
        assertThat(found.get().id()).isEqualTo(created.id()); // GH-90000
    }
}

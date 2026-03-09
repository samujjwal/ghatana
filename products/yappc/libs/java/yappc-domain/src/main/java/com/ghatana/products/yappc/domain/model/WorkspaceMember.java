package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.PersonaType;
import com.ghatana.products.yappc.domain.enums.WorkspaceRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * WorkspaceMember entity representing user membership in a workspace.
 *
 * <p><b>Purpose</b><br>
 * WorkspaceMember is the many-to-many join entity connecting Users to Workspaces
 * with role and persona information. Controls access permissions and dashboard views.
 *
 * <p><b>Role-Based Access Control</b><br>
 * Role determines permissions: OWNER (full), ADMIN (manage), MEMBER (standard), VIEWER (read-only).
 *
 * <p><b>Persona-Based UI</b><br>
 * Persona determines which dashboard views the user sees: EXECUTIVE, LEADERSHIP,
 * MANAGER, ENGINEER, SECURITY_CHAMPION, COMPLIANCE_OFFICER, AUDITOR.
 *
 * <p><b>Architecture Role</b><br>
 * This is a domain model in the JPA persistence layer, mapped to the "workspace_member" table
 * in PostgreSQL. Used for tenant access control and UI personalization.
 *
 * <p><b>Thread Safety</b><br>
 * Entity class is NOT thread-safe. Instances should not be shared across threads.
 *
 * @see Workspace
 * @see User
 * @doc.type class
 * @doc.purpose Workspace membership with role/persona
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "workspace_member", 
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_workspace_member_workspace_user", columnNames = {"workspace_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_workspace_member_workspace_persona", columnList = "workspace_id, persona")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMember {

    /**
     * Unique identifier for the workspace membership.
     * UUID generated automatically using PostgreSQL gen_random_uuid().
     */
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Foreign key to Workspace entity.
     * Workspace this membership belongs to.
     */
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * Foreign key to User entity.
     * User who is a member of the workspace.
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * User's role in the workspace.
     * Determines access permissions: OWNER > ADMIN > MEMBER > VIEWER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private WorkspaceRole role;

    /**
     * User's persona for dashboard views.
     * Determines which KPIs and metrics are shown.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "persona", nullable = false, length = 50)
    private PersonaType persona;

    /**
     * Timestamp when user joined the workspace.
     * Set automatically on first insert.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

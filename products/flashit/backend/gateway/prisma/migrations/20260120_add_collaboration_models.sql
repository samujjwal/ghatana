-- Migration: Add collaboration, RBAC, and data management models
-- Date: 2026-01-20
-- Description: Adds UserRole enum, system role to users, and 9 new models:
--   comments, reactions, follows, invitations, notifications, templates,
--   reports, data_export_requests, deletion_requests

-- ============================================================================
-- UserRole Enum & User.role column
-- ============================================================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'UserRole') THEN
    CREATE TYPE "UserRole" AS ENUM ('USER', 'OPERATOR', 'ADMIN', 'SUPER_ADMIN');
  END IF;
END $$;

ALTER TABLE users ADD COLUMN IF NOT EXISTS "role" "UserRole" NOT NULL DEFAULT 'USER';
CREATE INDEX IF NOT EXISTS idx_users_role ON users ("role");

-- ============================================================================
-- Comments
-- ============================================================================
CREATE TABLE IF NOT EXISTS comments (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  moment_id   UUID NOT NULL,
  user_id     UUID NOT NULL,
  content     TEXT NOT NULL,
  parent_id   UUID,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ,

  CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id)
);

CREATE INDEX IF NOT EXISTS idx_comments_moment_id ON comments (moment_id);
CREATE INDEX IF NOT EXISTS idx_comments_user_id ON comments (user_id);
CREATE INDEX IF NOT EXISTS idx_comments_parent_id ON comments (parent_id);
CREATE INDEX IF NOT EXISTS idx_comments_deleted_at ON comments (deleted_at);

-- ============================================================================
-- Reactions
-- ============================================================================
CREATE TABLE IF NOT EXISTS reactions (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  moment_id  UUID NOT NULL,
  user_id    UUID NOT NULL,
  emoji      VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_reactions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT uq_reactions_moment_user_emoji UNIQUE (moment_id, user_id, emoji)
);

CREATE INDEX IF NOT EXISTS idx_reactions_moment_id ON reactions (moment_id);
CREATE INDEX IF NOT EXISTS idx_reactions_user_id ON reactions (user_id);

-- ============================================================================
-- Follows
-- ============================================================================
CREATE TABLE IF NOT EXISTS follows (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  follower_id   UUID NOT NULL,
  following_id  UUID NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  unfollowed_at TIMESTAMPTZ,

  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_follows_following FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT uq_follows_pair UNIQUE (follower_id, following_id)
);

CREATE INDEX IF NOT EXISTS idx_follows_follower_id ON follows (follower_id);
CREATE INDEX IF NOT EXISTS idx_follows_following_id ON follows (following_id);

-- ============================================================================
-- Invitations
-- ============================================================================
CREATE TABLE IF NOT EXISTS invitations (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sphere_id    UUID NOT NULL,
  sender_id    UUID NOT NULL,
  recipient_id UUID,
  email        VARCHAR(255) NOT NULL,
  role         VARCHAR(50) NOT NULL,
  token        VARCHAR(255) NOT NULL UNIQUE,
  status       VARCHAR(50) NOT NULL DEFAULT 'pending',
  expires_at   TIMESTAMPTZ NOT NULL,
  accepted_at  TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_invitations_sender FOREIGN KEY (sender_id) REFERENCES users(id),
  CONSTRAINT fk_invitations_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_invitations_sphere_id ON invitations (sphere_id);
CREATE INDEX IF NOT EXISTS idx_invitations_sender_id ON invitations (sender_id);
CREATE INDEX IF NOT EXISTS idx_invitations_recipient_id ON invitations (recipient_id);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON invitations (email);
CREATE INDEX IF NOT EXISTS idx_invitations_token ON invitations (token);
CREATE INDEX IF NOT EXISTS idx_invitations_status ON invitations (status);

-- ============================================================================
-- Notifications
-- ============================================================================
CREATE TABLE IF NOT EXISTS notifications (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL,
  type         VARCHAR(100) NOT NULL,
  title        VARCHAR(500) NOT NULL,
  body         TEXT NOT NULL,
  data         JSONB,
  channel      VARCHAR(50) NOT NULL DEFAULT 'in_app',
  read         BOOLEAN NOT NULL DEFAULT FALSE,
  read_at      TIMESTAMPTZ,
  sent_at      TIMESTAMPTZ,
  dismissed_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications (read);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notifications (type);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON notifications (user_id, read);
CREATE INDEX IF NOT EXISTS idx_notifications_created_desc ON notifications (created_at DESC);

-- ============================================================================
-- Templates
-- ============================================================================
CREATE TABLE IF NOT EXISTS templates (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL,
  name        VARCHAR(255) NOT NULL,
  description TEXT,
  category    VARCHAR(100) NOT NULL,
  content     JSONB NOT NULL,
  is_public   BOOLEAN NOT NULL DEFAULT FALSE,
  usage_count INTEGER NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at  TIMESTAMPTZ,

  CONSTRAINT fk_templates_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_templates_user_id ON templates (user_id);
CREATE INDEX IF NOT EXISTS idx_templates_category ON templates (category);
CREATE INDEX IF NOT EXISTS idx_templates_is_public ON templates (is_public);
CREATE INDEX IF NOT EXISTS idx_templates_deleted_at ON templates (deleted_at);

-- ============================================================================
-- Reports
-- ============================================================================
CREATE TABLE IF NOT EXISTS reports (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL,
  report_type  VARCHAR(50) NOT NULL,
  format       VARCHAR(20) NOT NULL,
  status       VARCHAR(50) NOT NULL DEFAULT 'pending',
  parameters   JSONB,
  file_url     TEXT,
  file_size    BIGINT,
  error_msg    TEXT,
  started_at   TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reports_user_id ON reports (user_id);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports (status);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON reports (created_at);

-- ============================================================================
-- Data Export Requests
-- ============================================================================
CREATE TABLE IF NOT EXISTS data_export_requests (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL,
  format       VARCHAR(20) NOT NULL DEFAULT 'json',
  scope        VARCHAR(50) NOT NULL DEFAULT 'full',
  status       VARCHAR(50) NOT NULL DEFAULT 'pending',
  file_url     TEXT,
  file_size    BIGINT,
  error_msg    TEXT,
  started_at   TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  expires_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_data_exports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_data_exports_user_id ON data_export_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_data_exports_status ON data_export_requests (status);

-- ============================================================================
-- Deletion Requests
-- ============================================================================
CREATE TABLE IF NOT EXISTS deletion_requests (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL,
  scope         VARCHAR(50) NOT NULL DEFAULT 'full',
  reason        TEXT,
  status        VARCHAR(50) NOT NULL DEFAULT 'pending',
  scheduled_for TIMESTAMPTZ NOT NULL,
  confirmed_at  TIMESTAMPTZ,
  executed_at   TIMESTAMPTZ,
  cancelled_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT fk_deletion_requests_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_deletion_requests_user_id ON deletion_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_deletion_requests_status ON deletion_requests (status);
CREATE INDEX IF NOT EXISTS idx_deletion_requests_scheduled ON deletion_requests (scheduled_for);

-- Copyright (c) 2026 Ghatana Platform Contributors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Durable audit events for page artifact save/load/validation actions.
CREATE TABLE IF NOT EXISTS page_artifact_audit_events (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_page_artifact_audit_events_tenant ON page_artifact_audit_events(tenant_id);
CREATE INDEX idx_page_artifact_audit_events_artifact ON page_artifact_audit_events(artifact_id);
CREATE INDEX idx_page_artifact_audit_events_time ON page_artifact_audit_events(occurred_at DESC);

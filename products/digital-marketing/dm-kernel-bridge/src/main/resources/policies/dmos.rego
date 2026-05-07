package dmos.authz

import future.keywords.if
import future.keywords.in

# DMOS authorization policy.
#
# Input shape:
#   input.tenantId    - tenant identifier
#   input.principalId - caller principal (user ID or service account)
#   input.resource    - resource path (e.g. "campaigns/123", "privacy/dsar")
#   input.action      - action requested (e.g. "read", "write", "delete")
#
# The policy allows a request when at least one allow rule is satisfied
# and no explicit deny rule matches.

default allow := false

# ------------------------------------------------------------------
# Allow: service accounts (prefixed with "svc-") can perform any action
# on operational resources.
# ------------------------------------------------------------------
allow if {
    startswith(input.principalId, "svc-")
    not denied
}

# ------------------------------------------------------------------
# Allow: workspace members can read their own resources.
# ------------------------------------------------------------------
allow if {
    input.action == "read"
    not denied
    valid_tenant
}

# ------------------------------------------------------------------
# Allow: workspace editors can write campaigns, workflows, contacts.
# ------------------------------------------------------------------
allow if {
    input.action in {"write", "create", "update"}
    writable_resource
    not denied
    valid_tenant
}

# ------------------------------------------------------------------
# Allow: admin principals can delete and perform DSAR actions.
# ------------------------------------------------------------------
allow if {
    input.action in {"delete", "admin"}
    is_admin_resource
    not denied
    valid_tenant
}

# ------------------------------------------------------------------
# Allow: privacy/dsar actions require explicit write permission.
# ------------------------------------------------------------------
allow if {
    startswith(input.resource, "privacy/dsar")
    input.action == "write"
    not denied
    valid_tenant
}

# ------------------------------------------------------------------
# Helper rules
# ------------------------------------------------------------------

valid_tenant if {
    input.tenantId != ""
    input.principalId != ""
    input.principalId != "anonymous"
}

writable_resource if {
    resource_prefix := split(input.resource, "/")[0]
    resource_prefix in {"campaigns", "workflows", "contacts", "connectors", "content", "workspaces", "budget", "analytics"}
}

is_admin_resource if {
    resource_prefix := split(input.resource, "/")[0]
    resource_prefix in {"privacy", "admin", "contacts", "campaigns"}
}

# ------------------------------------------------------------------
# Deny rules — explicit denials take precedence over allows.
# ------------------------------------------------------------------

denied if {
    input.principalId == ""
}

denied if {
    input.tenantId == ""
}

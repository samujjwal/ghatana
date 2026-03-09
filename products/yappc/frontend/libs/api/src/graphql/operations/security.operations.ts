/**
 * Security Phase GraphQL Operations
 *
 * @description GraphQL queries, mutations, and subscriptions for the
 * security phase including vulnerability management, security scans,
 * compliance frameworks, secrets management, and audit logging.
 *
 * @doc.type api
 * @doc.purpose Security Phase API
 * @doc.layer data-access
 */

import { gql } from '@apollo/client';

// =============================================================================
// FRAGMENTS
// =============================================================================

export const VULNERABILITY_FRAGMENT = gql`
  fragment VulnerabilityFields on Vulnerability {
    id
    title
    description
    severity
    status
    source
    cveId
    cvssScore
    cvssVector
    affectedComponent {
      name
      version
      type
      path
    }
    fixAvailable
    fixVersion
    discoveredAt
    firstSeenAt
    lastSeenAt
    assignee {
      id
      name
      email
      avatar
    }
    dueDate
    slaStatus
    exploitability
    attackVector
    references {
      type
      url
      title
    }
    relatedVulnerabilities {
      id
      cveId
      severity
    }
    tags
    comments {
      id
      author {
        id
        name
        avatar
      }
      content
      timestamp
    }
  }
`;

export const SECURITY_SCAN_FRAGMENT = gql`
  fragment SecurityScanFields on SecurityScan {
    id
    type
    name
    status
    startedAt
    completedAt
    duration
    triggeredBy {
      id
      name
    }
    trigger
    target {
      type
      identifier
      branch
      commit
    }
    configuration {
      scanners
      severity
      excludePatterns
      customRules
    }
    results {
      totalFindings
      bySeverity {
        severity
        count
        new
        fixed
      }
      newFindings
      fixedFindings
      falsePositives
    }
    coverage {
      filesScanned
      linesScanned
      percentage
    }
    artifacts {
      name
      type
      url
      size
    }
    pipelineId
    pullRequestId
  }
`;

export const COMPLIANCE_FRAMEWORK_FRAGMENT = gql`
  fragment ComplianceFrameworkFields on ComplianceFramework {
    id
    name
    description
    version
    type
    status
    lastAssessedAt
    nextAssessmentDue
    overallScore
    controls {
      id
      identifier
      title
      description
      category
      status
      evidence {
        id
        type
        description
        url
        uploadedAt
        uploadedBy {
          id
          name
        }
      }
      findings {
        id
        severity
        description
        remediation
      }
      lastAssessedAt
      owner {
        id
        name
      }
    }
    categories {
      name
      controlCount
      passedCount
      failedCount
      percentage
    }
    requirements {
      id
      title
      description
      mandatory
      status
    }
  }
`;

export const SECRET_FRAGMENT = gql`
  fragment SecretFields on Secret {
    id
    name
    description
    type
    status
    createdAt
    updatedAt
    lastRotatedAt
    expiresAt
    rotationPolicy {
      enabled
      intervalDays
      notifyBeforeDays
    }
    environments {
      name
      synced
      lastSyncedAt
    }
    accessControl {
      users {
        id
        name
        permission
      }
      services {
        id
        name
        permission
      }
    }
    auditInfo {
      lastAccessedAt
      lastAccessedBy {
        id
        name
      }
      accessCount
    }
    metadata {
      key
      value
    }
    encrypted
    version
    previousVersions {
      version
      createdAt
      createdBy {
        id
        name
      }
    }
  }
`;

export const SECURITY_POLICY_FRAGMENT = gql`
  fragment SecurityPolicyFields on SecurityPolicy {
    id
    name
    description
    type
    status
    enabled
    createdAt
    updatedAt
    owner {
      id
      name
    }
    rules {
      id
      name
      description
      condition
      action
      severity
      enabled
      exceptions {
        type
        value
        reason
        expiresAt
      }
    }
    scope {
      repositories
      branches
      environments
      services
    }
    enforcement {
      mode
      blockOnViolation
      notifyOnViolation
      autoRemediate
    }
    violations {
      total
      lastWeek
      trend
    }
    lastTriggeredAt
  }
`;

export const AUDIT_LOG_FRAGMENT = gql`
  fragment AuditLogFields on AuditLog {
    id
    timestamp
    action
    category
    actor {
      id
      type
      name
      email
      ipAddress
      userAgent
    }
    resource {
      type
      id
      name
      path
    }
    outcome
    severity
    details {
      key
      oldValue
      newValue
    }
    metadata {
      correlationId
      sessionId
      requestId
      source
      location {
        country
        region
        city
      }
    }
  }
`;

export const SECURITY_ALERT_FRAGMENT = gql`
  fragment SecurityAlertFields on SecurityAlert {
    id
    title
    description
    type
    severity
    status
    createdAt
    acknowledgedAt
    resolvedAt
    source
    indicators {
      type
      value
      confidence
    }
    affectedResources {
      type
      id
      name
    }
    timeline {
      timestamp
      event
      details
    }
    assignee {
      id
      name
      avatar
    }
    relatedAlerts {
      id
      title
      severity
    }
    recommendations {
      priority
      action
      description
    }
    falsePositive
    falsePositiveReason
  }
`;

// =============================================================================
// QUERIES
// =============================================================================

export const GET_VULNERABILITY = gql`
  ${VULNERABILITY_FRAGMENT}
  
  query GetVulnerability($id: ID!) {
    vulnerability(id: $id) {
      ...VulnerabilityFields
      timeline {
        timestamp
        event
        actor {
          id
          name
        }
        details
      }
      remediationSteps {
        order
        description
        completed
        completedAt
        completedBy {
          id
          name
        }
      }
    }
  }
`;

export const LIST_VULNERABILITIES = gql`
  ${VULNERABILITY_FRAGMENT}
  
  query ListVulnerabilities(
    $projectId: ID!
    $filter: VulnerabilityFilter
    $pagination: PaginationInput
    $sort: SortInput
  ) {
    vulnerabilities(
      projectId: $projectId
      filter: $filter
      pagination: $pagination
      sort: $sort
    ) {
      edges {
        cursor
        node {
          ...VulnerabilityFields
        }
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
        totalCount
      }
      statistics {
        total
        bySeverity {
          severity
          count
        }
        byStatus {
          status
          count
        }
        averageResolutionTime
        slaCompliance
      }
    }
  }
`;

export const GET_VULNERABILITY_TRENDS = gql`
  query GetVulnerabilityTrends(
    $projectId: ID!
    $timeRange: TimeRangeInput!
    $groupBy: TrendGroupBy
  ) {
    vulnerabilityTrends(projectId: $projectId, timeRange: $timeRange, groupBy: $groupBy) {
      dataPoints {
        timestamp
        total
        critical
        high
        medium
        low
        newCount
        fixedCount
      }
      summary {
        totalDiscovered
        totalFixed
        averageTimeToFix
        fixRate
      }
    }
  }
`;

export const GET_SECURITY_SCAN = gql`
  ${SECURITY_SCAN_FRAGMENT}
  
  query GetSecurityScan($id: ID!) {
    securityScan(id: $id) {
      ...SecurityScanFields
      findings {
        id
        rule
        severity
        message
        location {
          file
          line
          column
        }
        snippet
        remediation
        falsePositive
        suppressed
      }
    }
  }
`;

export const LIST_SECURITY_SCANS = gql`
  ${SECURITY_SCAN_FRAGMENT}
  
  query ListSecurityScans(
    $projectId: ID!
    $filter: ScanFilter
    $pagination: PaginationInput
  ) {
    securityScans(projectId: $projectId, filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          ...SecurityScanFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        totalCount
      }
    }
  }
`;

export const GET_SCAN_HISTORY = gql`
  query GetScanHistory($projectId: ID!, $scanType: ScanType!, $limit: Int) {
    scanHistory(projectId: $projectId, scanType: $scanType, limit: $limit) {
      scans {
        id
        startedAt
        status
        results {
          totalFindings
          bySeverity {
            severity
            count
          }
        }
      }
      trends {
        period
        findingsCount
        delta
      }
    }
  }
`;

export const GET_COMPLIANCE_FRAMEWORK = gql`
  ${COMPLIANCE_FRAMEWORK_FRAGMENT}
  
  query GetComplianceFramework($id: ID!) {
    complianceFramework(id: $id) {
      ...ComplianceFrameworkFields
    }
  }
`;

export const LIST_COMPLIANCE_FRAMEWORKS = gql`
  ${COMPLIANCE_FRAMEWORK_FRAGMENT}
  
  query ListComplianceFrameworks($projectId: ID!, $filter: ComplianceFilter) {
    complianceFrameworks(projectId: $projectId, filter: $filter) {
      ...ComplianceFrameworkFields
    }
  }
`;

export const GET_COMPLIANCE_REPORT = gql`
  query GetComplianceReport($frameworkId: ID!, $format: ReportFormat) {
    complianceReport(frameworkId: $frameworkId, format: $format) {
      generatedAt
      framework {
        id
        name
        version
      }
      summary {
        totalControls
        passedControls
        failedControls
        notApplicable
        overallScore
      }
      sections {
        category
        controls {
          identifier
          title
          status
          evidence {
            type
            description
          }
          gaps {
            description
            severity
            remediation
          }
        }
      }
      downloadUrl
    }
  }
`;

export const GET_SECRET = gql`
  ${SECRET_FRAGMENT}
  
  query GetSecret($id: ID!) {
    secret(id: $id) {
      ...SecretFields
    }
  }
`;

export const LIST_SECRETS = gql`
  ${SECRET_FRAGMENT}
  
  query ListSecrets($projectId: ID!, $filter: SecretFilter) {
    secrets(projectId: $projectId, filter: $filter) {
      ...SecretFields
    }
  }
`;

export const GET_SECRET_VALUE = gql`
  query GetSecretValue($id: ID!, $environment: String!) {
    secretValue(id: $id, environment: $environment) {
      value
      decryptedAt
      accessedBy {
        id
        name
      }
    }
  }
`;

export const GET_EXPIRING_SECRETS = gql`
  ${SECRET_FRAGMENT}
  
  query GetExpiringSecrets($projectId: ID!, $withinDays: Int!) {
    expiringSecrets(projectId: $projectId, withinDays: $withinDays) {
      ...SecretFields
    }
  }
`;

export const GET_SECURITY_POLICY = gql`
  ${SECURITY_POLICY_FRAGMENT}
  
  query GetSecurityPolicy($id: ID!) {
    securityPolicy(id: $id) {
      ...SecurityPolicyFields
      recentViolations {
        id
        timestamp
        resource
        rule
        severity
        blocked
      }
    }
  }
`;

export const LIST_SECURITY_POLICIES = gql`
  ${SECURITY_POLICY_FRAGMENT}
  
  query ListSecurityPolicies($projectId: ID!, $filter: PolicyFilter) {
    securityPolicies(projectId: $projectId, filter: $filter) {
      ...SecurityPolicyFields
    }
  }
`;

export const VALIDATE_POLICY = gql`
  query ValidatePolicy($policyId: ID!, $target: PolicyValidationTarget!) {
    validatePolicy(policyId: $policyId, target: $target) {
      valid
      violations {
        rule
        severity
        message
        location
        remediation
      }
      warnings {
        rule
        message
      }
    }
  }
`;

export const GET_AUDIT_LOGS = gql`
  ${AUDIT_LOG_FRAGMENT}
  
  query GetAuditLogs(
    $projectId: ID!
    $filter: AuditLogFilter
    $pagination: PaginationInput
  ) {
    auditLogs(projectId: $projectId, filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          ...AuditLogFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        totalCount
      }
    }
  }
`;

export const SEARCH_AUDIT_LOGS = gql`
  ${AUDIT_LOG_FRAGMENT}
  
  query SearchAuditLogs($projectId: ID!, $query: String!, $filter: AuditLogFilter) {
    searchAuditLogs(projectId: $projectId, query: $query, filter: $filter) {
      results {
        ...AuditLogFields
      }
      facets {
        field
        values {
          value
          count
        }
      }
      totalCount
    }
  }
`;

export const GET_SECURITY_ALERT = gql`
  ${SECURITY_ALERT_FRAGMENT}
  
  query GetSecurityAlert($id: ID!) {
    securityAlert(id: $id) {
      ...SecurityAlertFields
    }
  }
`;

export const LIST_SECURITY_ALERTS = gql`
  ${SECURITY_ALERT_FRAGMENT}
  
  query ListSecurityAlerts(
    $projectId: ID!
    $filter: SecurityAlertFilter
    $pagination: PaginationInput
  ) {
    securityAlerts(projectId: $projectId, filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          ...SecurityAlertFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        totalCount
      }
      statistics {
        total
        bySeverity {
          severity
          count
        }
        byType {
          type
          count
        }
      }
    }
  }
`;

export const GET_SECURITY_DASHBOARD = gql`
  query GetSecurityDashboard($projectId: ID!, $timeRange: TimeRangeInput!) {
    securityDashboard(projectId: $projectId, timeRange: $timeRange) {
      overview {
        securityScore
        scoreTrend
        openVulnerabilities
        criticalVulnerabilities
        pendingScans
        expiredSecrets
        policyViolations
        complianceScore
      }
      vulnerabilityChart {
        labels
        datasets {
          label
          data
        }
      }
      scanActivity {
        total
        passed
        failed
        byType {
          type
          count
        }
      }
      recentAlerts {
        id
        title
        severity
        timestamp
      }
      topRisks {
        type
        description
        severity
        affectedCount
        recommendation
      }
      complianceStatus {
        framework
        score
        status
        dueDate
      }
    }
  }
`;

export const GET_SECURITY_POSTURE = gql`
  query GetSecurityPosture($projectId: ID!) {
    securityPosture(projectId: $projectId) {
      overallScore
      categories {
        name
        score
        weight
        findings {
          severity
          count
        }
        recommendations {
          priority
          title
          impact
          effort
        }
      }
      benchmarks {
        name
        score
        industryAverage
        percentile
      }
      improvementPlan {
        priority
        action
        expectedImpact
        estimatedEffort
        category
      }
    }
  }
`;

// =============================================================================
// MUTATIONS
// =============================================================================

export const UPDATE_VULNERABILITY = gql`
  ${VULNERABILITY_FRAGMENT}
  
  mutation UpdateVulnerability($id: ID!, $input: UpdateVulnerabilityInput!) {
    updateVulnerability(id: $id, input: $input) {
      ...VulnerabilityFields
    }
  }
`;

export const UPDATE_VULNERABILITY_STATUS = gql`
  ${VULNERABILITY_FRAGMENT}
  
  mutation UpdateVulnerabilityStatus(
    $id: ID!
    $status: VulnerabilityStatus!
    $reason: String
  ) {
    updateVulnerabilityStatus(id: $id, status: $status, reason: $reason) {
      ...VulnerabilityFields
    }
  }
`;

export const ASSIGN_VULNERABILITY = gql`
  ${VULNERABILITY_FRAGMENT}
  
  mutation AssignVulnerability($id: ID!, $assigneeId: ID!) {
    assignVulnerability(id: $id, assigneeId: $assigneeId) {
      ...VulnerabilityFields
    }
  }
`;

export const ADD_VULNERABILITY_COMMENT = gql`
  mutation AddVulnerabilityComment($vulnerabilityId: ID!, $content: String!) {
    addVulnerabilityComment(vulnerabilityId: $vulnerabilityId, content: $content) {
      id
      author {
        id
        name
        avatar
      }
      content
      timestamp
    }
  }
`;

export const BULK_UPDATE_VULNERABILITIES = gql`
  mutation BulkUpdateVulnerabilities(
    $vulnerabilityIds: [ID!]!
    $input: BulkVulnerabilityUpdateInput!
  ) {
    bulkUpdateVulnerabilities(vulnerabilityIds: $vulnerabilityIds, input: $input) {
      successCount
      failedCount
      errors {
        vulnerabilityId
        message
      }
    }
  }
`;

export const MARK_VULNERABILITY_FALSE_POSITIVE = gql`
  ${VULNERABILITY_FRAGMENT}
  
  mutation MarkVulnerabilityFalsePositive($id: ID!, $reason: String!) {
    markVulnerabilityFalsePositive(id: $id, reason: $reason) {
      ...VulnerabilityFields
    }
  }
`;

export const CREATE_SECURITY_SCAN = gql`
  ${SECURITY_SCAN_FRAGMENT}
  
  mutation CreateSecurityScan($projectId: ID!, $input: CreateScanInput!) {
    createSecurityScan(projectId: $projectId, input: $input) {
      ...SecurityScanFields
    }
  }
`;

export const START_SECURITY_SCAN = gql`
  ${SECURITY_SCAN_FRAGMENT}
  
  mutation StartSecurityScan($id: ID!) {
    startSecurityScan(id: $id) {
      ...SecurityScanFields
    }
  }
`;

export const CANCEL_SECURITY_SCAN = gql`
  mutation CancelSecurityScan($id: ID!) {
    cancelSecurityScan(id: $id) {
      success
      message
    }
  }
`;

export const SUPPRESS_SCAN_FINDING = gql`
  mutation SuppressScanFinding($scanId: ID!, $findingId: ID!, $reason: String!) {
    suppressScanFinding(scanId: $scanId, findingId: $findingId, reason: $reason) {
      success
    }
  }
`;

export const MARK_FINDING_FALSE_POSITIVE = gql`
  mutation MarkFindingFalsePositive($scanId: ID!, $findingId: ID!, $reason: String!) {
    markFindingFalsePositive(scanId: $scanId, findingId: $findingId, reason: $reason) {
      success
    }
  }
`;

export const CREATE_COMPLIANCE_FRAMEWORK = gql`
  ${COMPLIANCE_FRAMEWORK_FRAGMENT}
  
  mutation CreateComplianceFramework($projectId: ID!, $input: CreateComplianceInput!) {
    createComplianceFramework(projectId: $projectId, input: $input) {
      ...ComplianceFrameworkFields
    }
  }
`;

export const UPDATE_COMPLIANCE_CONTROL = gql`
  mutation UpdateComplianceControl(
    $frameworkId: ID!
    $controlId: ID!
    $input: UpdateControlInput!
  ) {
    updateComplianceControl(frameworkId: $frameworkId, controlId: $controlId, input: $input) {
      id
      status
      evidence {
        id
        type
        description
        url
      }
    }
  }
`;

export const ADD_COMPLIANCE_EVIDENCE = gql`
  mutation AddComplianceEvidence($controlId: ID!, $input: AddEvidenceInput!) {
    addComplianceEvidence(controlId: $controlId, input: $input) {
      id
      type
      description
      url
      uploadedAt
      uploadedBy {
        id
        name
      }
    }
  }
`;

export const DELETE_COMPLIANCE_EVIDENCE = gql`
  mutation DeleteComplianceEvidence($evidenceId: ID!) {
    deleteComplianceEvidence(evidenceId: $evidenceId) {
      success
    }
  }
`;

export const RUN_COMPLIANCE_ASSESSMENT = gql`
  mutation RunComplianceAssessment($frameworkId: ID!) {
    runComplianceAssessment(frameworkId: $frameworkId) {
      assessmentId
      status
      startedAt
    }
  }
`;

export const CREATE_SECRET = gql`
  ${SECRET_FRAGMENT}
  
  mutation CreateSecret($projectId: ID!, $input: CreateSecretInput!) {
    createSecret(projectId: $projectId, input: $input) {
      ...SecretFields
    }
  }
`;

export const UPDATE_SECRET = gql`
  ${SECRET_FRAGMENT}
  
  mutation UpdateSecret($id: ID!, $input: UpdateSecretInput!) {
    updateSecret(id: $id, input: $input) {
      ...SecretFields
    }
  }
`;

export const SET_SECRET_VALUE = gql`
  mutation SetSecretValue($id: ID!, $value: String!, $environment: String!) {
    setSecretValue(id: $id, value: $value, environment: $environment) {
      success
      version
    }
  }
`;

export const ROTATE_SECRET = gql`
  ${SECRET_FRAGMENT}
  
  mutation RotateSecret($id: ID!, $newValue: String) {
    rotateSecret(id: $id, newValue: $newValue) {
      ...SecretFields
    }
  }
`;

export const DELETE_SECRET = gql`
  mutation DeleteSecret($id: ID!) {
    deleteSecret(id: $id) {
      success
    }
  }
`;

export const SYNC_SECRET_TO_ENVIRONMENT = gql`
  mutation SyncSecretToEnvironment($secretId: ID!, $environment: String!) {
    syncSecretToEnvironment(secretId: $secretId, environment: $environment) {
      success
      syncedAt
    }
  }
`;

export const UPDATE_SECRET_ACCESS = gql`
  mutation UpdateSecretAccess($secretId: ID!, $input: SecretAccessInput!) {
    updateSecretAccess(secretId: $secretId, input: $input) {
      accessControl {
        users {
          id
          name
          permission
        }
        services {
          id
          name
          permission
        }
      }
    }
  }
`;

export const CREATE_SECURITY_POLICY = gql`
  ${SECURITY_POLICY_FRAGMENT}
  
  mutation CreateSecurityPolicy($projectId: ID!, $input: CreatePolicyInput!) {
    createSecurityPolicy(projectId: $projectId, input: $input) {
      ...SecurityPolicyFields
    }
  }
`;

export const UPDATE_SECURITY_POLICY = gql`
  ${SECURITY_POLICY_FRAGMENT}
  
  mutation UpdateSecurityPolicy($id: ID!, $input: UpdatePolicyInput!) {
    updateSecurityPolicy(id: $id, input: $input) {
      ...SecurityPolicyFields
    }
  }
`;

export const DELETE_SECURITY_POLICY = gql`
  mutation DeleteSecurityPolicy($id: ID!) {
    deleteSecurityPolicy(id: $id) {
      success
    }
  }
`;

export const TOGGLE_SECURITY_POLICY = gql`
  ${SECURITY_POLICY_FRAGMENT}
  
  mutation ToggleSecurityPolicy($id: ID!, $enabled: Boolean!) {
    toggleSecurityPolicy(id: $id, enabled: $enabled) {
      ...SecurityPolicyFields
    }
  }
`;

export const ADD_POLICY_EXCEPTION = gql`
  mutation AddPolicyException($policyId: ID!, $ruleId: ID!, $input: ExceptionInput!) {
    addPolicyException(policyId: $policyId, ruleId: $ruleId, input: $input) {
      id
      type
      value
      reason
      expiresAt
      createdBy {
        id
        name
      }
    }
  }
`;

export const REMOVE_POLICY_EXCEPTION = gql`
  mutation RemovePolicyException($policyId: ID!, $ruleId: ID!, $exceptionId: ID!) {
    removePolicyException(policyId: $policyId, ruleId: $ruleId, exceptionId: $exceptionId) {
      success
    }
  }
`;

export const ACKNOWLEDGE_SECURITY_ALERT = gql`
  ${SECURITY_ALERT_FRAGMENT}
  
  mutation AcknowledgeSecurityAlert($id: ID!, $note: String) {
    acknowledgeSecurityAlert(id: $id, note: $note) {
      ...SecurityAlertFields
    }
  }
`;

export const RESOLVE_SECURITY_ALERT = gql`
  ${SECURITY_ALERT_FRAGMENT}
  
  mutation ResolveSecurityAlert($id: ID!, $resolution: String!) {
    resolveSecurityAlert(id: $id, resolution: $resolution) {
      ...SecurityAlertFields
    }
  }
`;

export const MARK_ALERT_FALSE_POSITIVE = gql`
  ${SECURITY_ALERT_FRAGMENT}
  
  mutation MarkAlertFalsePositive($id: ID!, $reason: String!) {
    markAlertFalsePositive(id: $id, reason: $reason) {
      ...SecurityAlertFields
    }
  }
`;

export const EXPORT_AUDIT_LOGS = gql`
  mutation ExportAuditLogs(
    $projectId: ID!
    $filter: AuditLogFilter!
    $format: ExportFormat!
  ) {
    exportAuditLogs(projectId: $projectId, filter: $filter, format: $format) {
      jobId
      status
      downloadUrl
      expiresAt
    }
  }
`;

// =============================================================================
// SUBSCRIPTIONS
// =============================================================================

export const SUBSCRIBE_TO_VULNERABILITY_UPDATES = gql`
  ${VULNERABILITY_FRAGMENT}
  
  subscription OnVulnerabilityUpdate($projectId: ID!) {
    vulnerabilityUpdated(projectId: $projectId) {
      type
      vulnerability {
        ...VulnerabilityFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_SCAN_PROGRESS = gql`
  subscription OnScanProgress($scanId: ID!) {
    scanProgress(scanId: $scanId) {
      scanId
      status
      progress
      currentPhase
      findingsCount
      estimatedTimeRemaining
      logs {
        timestamp
        level
        message
      }
    }
  }
`;

export const SUBSCRIBE_TO_SECURITY_ALERTS = gql`
  ${SECURITY_ALERT_FRAGMENT}
  
  subscription OnSecurityAlert($projectId: ID!) {
    securityAlertTriggered(projectId: $projectId) {
      ...SecurityAlertFields
    }
  }
`;

export const SUBSCRIBE_TO_COMPLIANCE_UPDATES = gql`
  subscription OnComplianceUpdate($frameworkId: ID!) {
    complianceUpdated(frameworkId: $frameworkId) {
      type
      controlId
      status
      score
    }
  }
`;

export const SUBSCRIBE_TO_SECRET_EVENTS = gql`
  subscription OnSecretEvent($projectId: ID!) {
    secretEvent(projectId: $projectId) {
      type
      secretId
      secretName
      eventType
      timestamp
      actor {
        id
        name
      }
    }
  }
`;

export const SUBSCRIBE_TO_POLICY_VIOLATIONS = gql`
  subscription OnPolicyViolation($projectId: ID!) {
    policyViolation(projectId: $projectId) {
      policyId
      policyName
      ruleId
      ruleName
      severity
      resource {
        type
        id
        name
      }
      timestamp
      blocked
    }
  }
`;

export const SUBSCRIBE_TO_AUDIT_STREAM = gql`
  ${AUDIT_LOG_FRAGMENT}
  
  subscription OnAuditEvent($projectId: ID!, $filter: AuditStreamFilter) {
    auditEvent(projectId: $projectId, filter: $filter) {
      ...AuditLogFields
    }
  }
`;

// =============================================================================
// TYPE DEFINITIONS
// =============================================================================

export interface UpdateVulnerabilityInput {
  status?: string;
  assigneeId?: string;
  dueDate?: string;
  tags?: string[];
  priority?: string;
}

export interface BulkVulnerabilityUpdateInput {
  status?: string;
  assigneeId?: string;
  tags?: string[];
}

export interface CreateScanInput {
  type: 'sast' | 'dast' | 'sca' | 'container' | 'iac' | 'secrets';
  name: string;
  target: {
    type: 'repository' | 'branch' | 'commit' | 'image' | 'url';
    identifier: string;
    branch?: string;
    commit?: string;
  };
  configuration?: {
    scanners?: string[];
    severityThreshold?: string;
    excludePatterns?: string[];
    customRules?: Array<{ id: string; enabled: boolean }>;
  };
  scheduleAt?: string;
}

export interface CreateComplianceInput {
  type: 'soc2' | 'iso27001' | 'hipaa' | 'pci_dss' | 'gdpr' | 'custom';
  name?: string;
  templateId?: string;
  assessmentFrequency?: 'monthly' | 'quarterly' | 'annually';
}

export interface UpdateControlInput {
  status?: 'not_started' | 'in_progress' | 'implemented' | 'not_applicable';
  ownerId?: string;
  notes?: string;
}

export interface AddEvidenceInput {
  type: 'document' | 'screenshot' | 'log' | 'configuration' | 'report' | 'other';
  description: string;
  url?: string;
  file?: File;
}

export interface CreateSecretInput {
  name: string;
  description?: string;
  type: 'api_key' | 'password' | 'certificate' | 'ssh_key' | 'token' | 'other';
  value: string;
  environments?: string[];
  rotationPolicy?: {
    enabled: boolean;
    intervalDays: number;
    notifyBeforeDays?: number;
  };
  expiresAt?: string;
  metadata?: Array<{ key: string; value: string }>;
}

export interface UpdateSecretInput {
  name?: string;
  description?: string;
  rotationPolicy?: {
    enabled: boolean;
    intervalDays: number;
    notifyBeforeDays?: number;
  };
  expiresAt?: string;
  metadata?: Array<{ key: string; value: string }>;
}

export interface SecretAccessInput {
  users?: Array<{ userId: string; permission: 'read' | 'write' | 'admin' }>;
  services?: Array<{ serviceId: string; permission: 'read' | 'write' }>;
}

export interface CreatePolicyInput {
  name: string;
  description?: string;
  type: 'code_quality' | 'security' | 'compliance' | 'operational' | 'custom';
  rules: Array<{
    name: string;
    description?: string;
    condition: string;
    action: 'block' | 'warn' | 'notify' | 'log';
    severity: 'critical' | 'high' | 'medium' | 'low';
  }>;
  scope?: {
    repositories?: string[];
    branches?: string[];
    environments?: string[];
    services?: string[];
  };
  enforcement?: {
    mode: 'enforce' | 'audit';
    blockOnViolation?: boolean;
    notifyOnViolation?: boolean;
    autoRemediate?: boolean;
  };
}

export interface UpdatePolicyInput {
  name?: string;
  description?: string;
  rules?: Array<{
    id?: string;
    name: string;
    description?: string;
    condition: string;
    action: string;
    severity: string;
    enabled?: boolean;
  }>;
  scope?: {
    repositories?: string[];
    branches?: string[];
    environments?: string[];
    services?: string[];
  };
  enforcement?: {
    mode?: string;
    blockOnViolation?: boolean;
    notifyOnViolation?: boolean;
    autoRemediate?: boolean;
  };
}

export interface ExceptionInput {
  type: 'resource' | 'pattern' | 'user' | 'time';
  value: string;
  reason: string;
  expiresAt?: string;
}

export interface PolicyValidationTarget {
  type: 'repository' | 'branch' | 'pullRequest' | 'deployment';
  identifier: string;
}

export interface VulnerabilityFilter {
  severity?: string[];
  status?: string[];
  source?: string[];
  assigneeId?: string;
  cveId?: string;
  affectedComponent?: string;
  tags?: string[];
  search?: string;
  dateRange?: TimeRangeInput;
}

export interface ScanFilter {
  type?: string[];
  status?: string[];
  triggeredBy?: string;
  dateRange?: TimeRangeInput;
}

export interface ComplianceFilter {
  type?: string[];
  status?: string[];
}

export interface SecretFilter {
  type?: string[];
  status?: string[];
  environment?: string;
  expiringSoon?: boolean;
  search?: string;
}

export interface PolicyFilter {
  type?: string[];
  enabled?: boolean;
  search?: string;
}

export interface AuditLogFilter {
  actions?: string[];
  categories?: string[];
  actorId?: string;
  actorType?: string;
  resourceType?: string;
  resourceId?: string;
  outcome?: string;
  severity?: string[];
  dateRange?: TimeRangeInput;
  ipAddress?: string;
}

export interface SecurityAlertFilter {
  type?: string[];
  severity?: string[];
  status?: string[];
  assigneeId?: string;
  dateRange?: TimeRangeInput;
}

export interface AuditStreamFilter {
  actions?: string[];
  categories?: string[];
  severity?: string[];
}

export interface TimeRangeInput {
  start: string;
  end: string;
}

export type VulnerabilityStatus = 'open' | 'in_progress' | 'resolved' | 'false_positive' | 'accepted_risk' | 'wont_fix';
export type ScanType = 'sast' | 'dast' | 'sca' | 'container' | 'iac' | 'secrets';
export type TrendGroupBy = 'day' | 'week' | 'month';
export type ReportFormat = 'pdf' | 'html' | 'csv' | 'json';
export type ExportFormat = 'csv' | 'json' | 'pdf';

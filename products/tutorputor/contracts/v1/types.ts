export type TenantId = string & { readonly __tenantId: unique symbol };
export type UserId = string & { readonly __userId: unique symbol };
export type ModuleId = string & { readonly __moduleId: unique symbol };
export type EnrollmentId = string & { readonly __enrollmentId: unique symbol };
export type AssessmentId = string & { readonly __assessmentId: unique symbol };
export type AssessmentItemId = string & { readonly __assessmentItemId: unique symbol };
export type AssessmentAttemptId = string & {
  readonly __assessmentAttemptId: unique symbol;
};
export type ModuleRevisionId = string & { readonly __moduleRevisionId: unique symbol };
export type MarketplaceListingId = string & { readonly __marketplaceListingId: unique symbol };

export type Difficulty = "INTRO" | "INTERMEDIATE" | "ADVANCED";
export type ModuleStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type UserRole = "student" | "teacher" | "admin" | "creator";

export interface UserSummary {
  id: UserId;
  email: string;
  displayName: string;
  role: UserRole;
}

export interface ModuleSummary {
  id: ModuleId;
  slug: string;
  title: string;
  domain: "MATH" | "SCIENCE" | "TECH";
  difficulty: Difficulty;
  estimatedTimeMinutes: number;
  tags: string[];
  status: ModuleStatus;
  progressPercent?: number;
  publishedAt?: string;
}

export type ContentBlockType =
  | "text"
  | "rich_text"
  | "interactive_visualization"
  | "video"
  | "image"
  | "simulation"
  | "vr_simulation"
  | "example"
  | "exercise"
  | "assessment_item_ref"
  | "ai_tutor_prompt";

/**
 * Payload for VR/AR simulation content blocks.
 */
export interface VRSimulationPayload {
  bundleUrl: string;
  requirements: VRRequirements;
  metadata: VRMetadata;
}

export interface VRRequirements {
  minHeadset?: "standalone" | "tethered" | "any";
  controllers?: boolean;
  roomScale?: boolean;
  handTracking?: boolean;
}

export interface VRMetadata {
  title: string;
  description?: string;
  duration?: number;
  difficulty?: "beginner" | "intermediate" | "advanced";
  objectives?: string[];
}

/**
 * Payload for USP simulation content blocks.
 * References a simulation manifest by ID or embeds a full manifest.
 */
export interface SimulationBlockPayload {
  /** Reference to an existing simulation manifest */
  manifestId?: string;

  /** Inline manifest for authoring (used in drafts before publishing) */
  inlineManifest?: {
    domain: SimulationDomain;
    title: string;
    description?: string;
    initialEntities: Array<{
      id: string;
      type: string;
      x: number;
      y: number;
      [key: string]: unknown;
    }>;
    steps: Array<{
      id: string;
      orderIndex: number;
      title?: string;
      description?: string;
      actions: Array<{
        action: string;
        [key: string]: unknown;
      }>;
    }>;
    canvas?: {
      width: number;
      height: number;
      backgroundColor?: string;
    };
    playback?: {
      defaultSpeed: number;
      allowScrubbing?: boolean;
      autoPlay?: boolean;
    };
  };

  /** Display configuration */
  display?: {
    showControls?: boolean;
    showTimeline?: boolean;
    showNarration?: boolean;
    aspectRatio?: "16:9" | "4:3" | "1:1" | "auto";
  };

  /** AI tutor integration */
  tutorContext?: {
    enabled: boolean;
    contextPrompt?: string;
  };
}

export type SimulationDomain =
  | "CS_DISCRETE"
  | "PHYSICS"
  | "ECONOMICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ENGINEERING"
  | "MATHEMATICS";

export type SimulationTemplateStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type SimulationTemplateDifficulty =
  | "BEGINNER"
  | "INTERMEDIATE"
  | "ADVANCED"
  | "EXPERT";

export type SimulationTemplateLicense =
  | "FREE"
  | "CC_BY"
  | "CC_BY_SA"
  | "CC_BY_NC"
  | "PROPRIETARY";

export interface SimulationTemplateStats {
  views: number;
  uses: number;
  favorites: number;
  rating: number;
  ratingCount: number;
  completionRate: number;
  avgTimeMinutes: number;
}

export interface SimulationTemplate {
  id: string;
  tenantId: TenantId;
  slug: string;
  title: string;
  description: string;
  domain: SimulationDomain;
  difficulty: SimulationTemplateDifficulty;
  tags: string[];
  thumbnailUrl?: string;
  license: SimulationTemplateLicense;
  isPremium: boolean;
  isVerified: boolean;
  version: string;
  authorId: UserId;
  authorName?: string;
  authorAvatarUrl?: string;
  organization?: string;
  stats: SimulationTemplateStats;
  status: SimulationTemplateStatus;
  publishedAt?: string;
  createdAt: string;
  updatedAt: string;
  moduleId?: ModuleId | null;
  manifestId?: string | null;
  conceptId?: string | null;
}

export interface SimulationTemplateInput {
  slug?: string;
  title: string;
  description: string;
  domain: SimulationDomain;
  difficulty: SimulationTemplateDifficulty;
  tags?: string[];
  thumbnailUrl?: string;
  license?: SimulationTemplateLicense;
  isPremium?: boolean;
  isVerified?: boolean;
  version?: string;
  manifestId?: string;
  moduleId?: ModuleId;
  conceptId?: string;
  status?: SimulationTemplateStatus;
  authorName?: string;
  authorAvatarUrl?: string;
  organization?: string;
}

export interface SimulationTemplateUpdate {
  title?: string;
  description?: string;
  difficulty?: SimulationTemplateDifficulty;
  tags?: string[];
  thumbnailUrl?: string;
  license?: SimulationTemplateLicense;
  isPremium?: boolean;
  isVerified?: boolean;
  version?: string;
  manifestId?: string | null;
  moduleId?: ModuleId | null;
  conceptId?: string | null;
  status?: SimulationTemplateStatus;
  authorName?: string | null;
  authorAvatarUrl?: string | null;
  organization?: string | null;
}

/**
 * Simulation launch token for VR/AR sessions.
 */
export interface SimulationLaunchToken {
  token: string;
  expiresAt: string;
  sessionUrl: string;
  bundleUrl: string;
}

export interface ContentBlock {
  id: string;
  orderIndex: number;
  blockType: ContentBlockType;
  payload: unknown;
}

export interface LearningObjective {
  id: string;
  label: string;
  taxonomyLevel:
  | "remember"
  | "understand"
  | "apply"
  | "analyze"
  | "evaluate"
  | "create";
}

export interface ModuleDetail extends ModuleSummary {
  description: string;
  learningObjectives: LearningObjective[];
  contentBlocks: ContentBlock[];
  prerequisites: ModuleId[];
  version: number;
}

export type EnrollmentStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export interface Enrollment {
  id: EnrollmentId;
  userId: UserId;
  moduleId: ModuleId;
  status: EnrollmentStatus;
  progressPercent: number;
  startedAt?: string;
  completedAt?: string;
  timeSpentSeconds: number;
}

export interface TutorQueryRequest {
  moduleId?: ModuleId;
  question: string;
  locale?: string;
}

export interface TutorCitation {
  type: "module" | "objective" | "content_block";
  id: string;
  label: string;
}

export interface TutorResponsePayload {
  answer: string;
  followUpQuestions?: string[];
  citations?: TutorCitation[];
  safety: {
    blocked: boolean;
    reason?: string;
  };
}

export interface DashboardSummary {
  user: UserSummary;
  currentEnrollments: Enrollment[];
  recommendedModules: ModuleSummary[];
}

export type AssessmentType = "QUIZ" | "PROJECT" | "SIMULATION";
export type AssessmentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

export type AssessmentItemType =
  | "multiple_choice_single"
  | "multiple_choice_multi"
  | "short_answer"
  | "code"
  | "free_response";

export interface AssessmentItemChoice {
  id: string;
  label: string;
  isCorrect?: boolean;
  rationale?: string;
}

export interface AssessmentItem {
  id: AssessmentItemId;
  type: AssessmentItemType;
  prompt: string;
  stimulus?: string;
  choices?: AssessmentItemChoice[];
  modelAnswer?: string;
  rubric?: string;
  points: number;
  taxonomyLevel?: LearningObjective["taxonomyLevel"];
  metadata?: Record<string, unknown>;
}

export interface AssessmentSummary {
  id: AssessmentId;
  moduleId: ModuleId;
  title: string;
  type: AssessmentType;
  status: AssessmentStatus;
  version: number;
  passingScore: number;
  attemptsAllowed?: number | null;
  timeLimitMinutes?: number | null;
}

export interface Assessment extends AssessmentSummary {
  createdBy: UserId;
  updatedBy: UserId;
  createdAt: string;
  updatedAt: string;
  objectives: LearningObjective[];
  items: AssessmentItem[];
}

export type AssessmentAttemptStatus =
  | "IN_PROGRESS"
  | "SUBMITTED"
  | "GRADED"
  | "EXPIRED";

export type AssessmentResponse =
  | { type: "multiple_choice"; selectedChoiceIds: string[] }
  | { type: "short_answer"; text: string }
  | { type: "code"; language: string; source: string }
  | { type: "free_response"; text: string };

export interface AssessmentFeedback {
  itemId: AssessmentItemId;
  scorePercent: number;
  strengths?: string[];
  improvements?: string[];
  needsReview?: boolean;
  comments?: string;
}

export interface AssessmentAttempt {
  id: AssessmentAttemptId;
  assessmentId: AssessmentId;
  userId: UserId;
  tenantId: TenantId;
  status: AssessmentAttemptStatus;
  responses: Record<AssessmentItemId, AssessmentResponse>;
  scorePercent?: number;
  feedback?: AssessmentFeedback[];
  startedAt: string;
  submittedAt?: string;
  gradedAt?: string;
  timeSpentSeconds?: number;
}

export interface AssessmentGenerationInput {
  tenantId: TenantId;
  moduleId: ModuleId;
  objectiveIds: string[];
  count: number;
  difficulty: Difficulty;
  tone?: "FORMATIVE" | "SUMMATIVE";
}

export interface AssessmentGenerationResult {
  items: AssessmentItem[];
  warnings?: string[];
  model?: string;
}

export const ASSESSMENT_NOT_IMPLEMENTED_CODE = "ASSESSMENT_NOT_IMPLEMENTED" as const;

export type LearningEventType =
  | "module_viewed"
  | "module_completed"
  | "assessment_started"
  | "assessment_completed"
  | "ai_tutor_message";

export interface LearningEventInput {
  type: LearningEventType;
  userId: UserId;
  moduleId?: ModuleId;
  payload?: Record<string, unknown>;
  timestamp?: string;
}

export interface AnalyticsSummary {
  tenantId: TenantId;
  totalEvents: number;
  activeLearners: number;
  eventsByType: Record<LearningEventType, number>;
  moduleCompletions: Array<{ moduleId: ModuleId; count: number }>;
}

// =============================================================================
// Advanced Analytics
// =============================================================================

export type RiskLevel = "low" | "medium" | "high" | "critical";

/**
 * Student risk indicator for early warning system.
 */
export interface StudentRiskIndicator {
  userId: UserId;
  riskLevel: RiskLevel;
  riskScore: number; // 0-100
  factors: RiskFactor[];
  lastUpdated: string;
  recommendations: string[];
}

export interface RiskFactor {
  type: "inactivity" | "low_progress" | "failing_assessments" | "missed_deadlines" | "declining_engagement";
  severity: RiskLevel;
  description: string;
  value: number;
  threshold: number;
}

/**
 * Module difficulty heatmap data.
 */
export interface ModuleDifficultyHeatmap {
  moduleId: ModuleId;
  moduleTitle: string;
  averageCompletionTime: number;
  averageAttempts: number;
  failureRate: number;
  dropOffRate: number;
  difficultyScore: number; // 0-100
}

/**
 * Usage analytics by time period.
 */
export interface UsageAnalytics {
  period: "daily" | "weekly" | "monthly";
  data: UsagePeriodData[];
}

export interface UsagePeriodData {
  date: string;
  activeUsers: number;
  newEnrollments: number;
  completions: number;
  totalTimeMinutes: number;
  assessmentAttempts: number;
  aiTutorQueries: number;
}

/**
 * Advanced analytics summary with predictions.
 */
export interface AdvancedAnalyticsSummary extends AnalyticsSummary {
  atRiskStudents: StudentRiskIndicator[];
  difficultyHeatmap: ModuleDifficultyHeatmap[];
  usageTrends: UsageAnalytics;
  predictions: AnalyticsPredictions;
}

export interface AnalyticsPredictions {
  projectedCompletions: number;
  projectedAtRiskCount: number;
  trendDirection: "improving" | "stable" | "declining";
  confidenceScore: number;
}

export interface MarketplaceListing {
  id: MarketplaceListingId;
  tenantId: TenantId;
  moduleId: ModuleId;
  creatorId: UserId;
  status: "DRAFT" | "ACTIVE" | "ARCHIVED";
  visibility: "PUBLIC" | "PRIVATE";
  priceCents: number;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
  performance?: {
    enrollments: number;
    rating?: number;
  };
}

export interface ModuleDraftInput {
  slug: string;
  title: string;
  description: string;
  domain: ModuleSummary["domain"];
  difficulty: Difficulty;
  estimatedTimeMinutes: number;
  tags: string[];
  learningObjectives: LearningObjective[];
  contentBlocks: ContentBlock[];
  prerequisites?: ModuleId[];
}

export interface ModuleDraftPatch {
  title?: string;
  description?: string;
  tags?: string[];
  learningObjectives?: LearningObjective[];
  contentBlocks?: ContentBlock[];
  estimatedTimeMinutes?: number;
  difficulty?: Difficulty;
}

// =============================================================================
// Learning Pathways
// =============================================================================

export type LearningPathId = string & { readonly __learningPathId: unique symbol };
export type LearningPathNodeId = string & { readonly __learningPathNodeId: unique symbol };

export type PathwayStatus = "ACTIVE" | "COMPLETED" | "PAUSED";

export interface LearningPathNode {
  id: LearningPathNodeId;
  moduleId: ModuleId;
  orderIndex: number;
  isOptional: boolean;
  completedAt?: string;
}

export interface LearningPath {
  id: LearningPathId;
  userId: UserId;
  tenantId: TenantId;
  title: string;
  goal: string;
  status: PathwayStatus;
  nodes: LearningPathNode[];
  createdAt: string;
  updatedAt: string;
}

export interface LearningPathRecommendation {
  modules: ModuleSummary[];
  reasoning: string;
  estimatedDurationMinutes: number;
}

export interface LearningPathEnrollment {
  id: string;
  pathId: LearningPathId;
  userId: UserId;
  tenantId: TenantId;
  startedAt: string;
  completedAt?: string;
  currentNodeIndex: number;
  progress: {
    nodeId: LearningPathNodeId;
    status: "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";
    completedAt?: string;
  }[];
}

export interface PathwayConstraints {
  maxModules?: number;
  maxDurationMinutes?: number;
  excludeModuleIds?: ModuleId[];
}

// =============================================================================
// Teacher/Classroom
// =============================================================================

export type ClassroomId = string & { readonly __classroomId: unique symbol };

export interface RosterEntry {
  userId: UserId;
  displayName: string;
  email?: string;
  enrolledAt: string;
  progressPercent: number;
}

export interface Classroom {
  id: ClassroomId;
  tenantId: TenantId;
  teacherId: UserId;
  name: string;
  description?: string;
  roster: RosterEntry[];
  assignedModules: ModuleId[];
  createdAt: string;
  updatedAt: string;
}

export interface TeacherDashboardSummary {
  teacher: UserSummary;
  classrooms: Classroom[];
  recentActivity: LearningEventInput[];
  atRiskStudents: RosterEntry[];
  totalStudents: number;
  averageClassProgress: number;
}

export interface ClassroomProgress {
  classroomId: ClassroomId;
  moduleId: ModuleId;
  moduleTitle: string;
  averageProgress: number;
  completionRate: number;
  strugglingStudents: RosterEntry[];
}

// =============================================================================
// Collaboration (Q&A, Discussions)
// =============================================================================

export type ThreadId = string & { readonly __threadId: unique symbol };
export type PostId = string & { readonly __postId: unique symbol };
export type HelpRequestId = string & { readonly __helpRequestId: unique symbol };

export type ThreadStatus = "OPEN" | "RESOLVED" | "CLOSED";

export interface Post {
  id: PostId;
  threadId: ThreadId;
  authorId: UserId;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt?: string;
  isAnswer?: boolean;
}

export interface Thread {
  id: ThreadId;
  tenantId: TenantId;
  moduleId?: ModuleId;
  title: string;
  status: ThreadStatus;
  authorId: UserId;
  authorName: string;
  posts: Post[];
  createdAt: string;
  resolvedAt?: string;
}

export type HelpRequestStatus = "PENDING" | "ANSWERED" | "ESCALATED";

export interface HelpRequest {
  id: HelpRequestId;
  tenantId: TenantId;
  userId: UserId;
  moduleId: ModuleId;
  question: string;
  status: HelpRequestStatus;
  createdAt: string;
  answeredAt?: string;
}

// =============================================================================
// VR/AR Simulation
// =============================================================================

export interface VRSimulationBlock {
  bundleUrl: string;
  requirements: {
    vrHeadset?: boolean;
    arSupport?: boolean;
    minMemoryMB: number;
  };
  metadata: Record<string, unknown>;
}

export interface SimulationLaunchToken {
  token: string;
  expiresAt: string;
  sessionUrl: string;
}

// =============================================================================
// Gamification (Badges, Achievements, Leaderboards)
// =============================================================================

export type BadgeId = string & { readonly __badgeId: unique symbol };

export type BadgeCategory = "LEARNING" | "COLLABORATION" | "STREAK" | "MASTERY" | "SPECIAL";

export interface Badge {
  id: BadgeId;
  tenantId: TenantId;
  name: string;
  description: string;
  iconUrl?: string;
  category: BadgeCategory;
  criteria: string;
  points: number;
}

export interface Achievement {
  id: string;
  badgeId: BadgeId;
  userId: UserId;
  tenantId: TenantId;
  badge: Badge;
  earnedAt: string;
  metadata?: Record<string, unknown>;
}

export interface UserProgress {
  userId: UserId;
  totalPoints: number;
  badgesEarned: number;
  currentStreak: number;
  longestStreak: number;
  level: number;
  levelProgress: number;
  recentBadges: Achievement[];
}

export interface LeaderboardEntry {
  rank: number;
  userId: UserId;
  displayName: string;
  avatarUrl?: string;
  totalPoints: number;
  badgesEarned: number;
}

// =============================================================================
// Billing/Payments (Marketplace)
// =============================================================================

export type CheckoutSessionId = string & { readonly __checkoutSessionId: unique symbol };
export type PurchaseId = string & { readonly __purchaseId: unique symbol };

export type CheckoutStatus = "PENDING" | "COMPLETED" | "FAILED" | "CANCELLED";

export interface CheckoutSession {
  id: CheckoutSessionId;
  tenantId: TenantId;
  listingId: MarketplaceListingId;
  userId: UserId;
  amountCents: number;
  status: CheckoutStatus;
  createdAt: string;
  completedAt?: string;
  paymentUrl?: string;
}

export interface Purchase {
  id: PurchaseId;
  tenantId: TenantId;
  userId: UserId;
  listingId: MarketplaceListingId;
  moduleId: ModuleId;
  amountCents: number;
  purchasedAt: string;
}

// =============================================================================
// LTI Integration (Canvas, Blackboard, Google Classroom)
// =============================================================================

export interface LTILaunchPayload {
  iss: string;
  sub: string;
  aud: string;
  exp: number;
  iat: number;
  nonce: string;
  context?: {
    id: string;
    label: string;
    title: string;
  };
  resourceLink?: {
    id: string;
    title: string;
  };
  roles?: string[];
}

export interface LTIDeepLinkingContent {
  type: "ltiResourceLink";
  title: string;
  url: string;
  custom?: Record<string, string>;
}

export interface LTIValidationResult {
  valid: boolean;
  payload?: LTILaunchPayload;
  error?: string;
}

// =============================================================================
// Offline Mode & Sync (Block 4 - Day 31)
// =============================================================================

/**
 * Sync status for offline mutations.
 */
export type SyncStatus = "synced" | "pending" | "error" | "offline";

/**
 * Overall offline state for the application.
 */
export interface OfflineState {
  isOnline: boolean;
  lastSyncAt: string | null;
  pendingMutationsCount: number;
  syncStatus: SyncStatus;
}

/**
 * Mutation types that can be queued for offline sync.
 */
export type OfflineMutationType =
  | "progress_update"
  | "assessment_attempt"
  | "ai_tutor_query"
  | "learning_event"
  | "thread_post"
  | "reaction";

/**
 * A pending mutation queued for sync when connectivity resumes.
 */
export interface PendingMutation {
  id: string;
  type: OfflineMutationType;
  payload: unknown;
  createdAt: string;
  retryCount: number;
  maxRetries: number;
  lastError?: string;
  idempotencyKey: string;
}

/**
 * Cached module data for offline access.
 */
export interface CachedModule {
  moduleId: ModuleId;
  slug: string;
  data: ModuleDetail;
  cachedAt: string;
  expiresAt: string;
  version: number;
}

/**
 * Cached progress snapshot for offline viewing.
 */
export interface CachedProgress {
  userId: UserId;
  enrollments: Enrollment[];
  cachedAt: string;
}

/**
 * Sync result after attempting to sync pending mutations.
 */
export interface SyncResult {
  success: boolean;
  syncedCount: number;
  failedCount: number;
  errors: Array<{ mutationId: string; error: string }>;
  completedAt: string;
}

// =============================================================================
// SSO & Identity Federation (Block 4 - Day 36)
// =============================================================================

/**
 * Supported identity provider types.
 */
export type IdentityProviderType = "oidc" | "saml";

/**
 * SSO provider status for monitoring.
 */
export type SsoProviderStatus = "active" | "inactive" | "error" | "pending_verification";

/**
 * Role mapping rule for SSO claims.
 */
export interface RoleMappingRule {
  /** The claim name to check (e.g., "groups", "roles", "department") */
  claim: string;
  /** The operator for matching */
  operator: "equals" | "contains" | "startsWith" | "endsWith" | "regex";
  /** The value to match against */
  value: string;
  /** The TutorPutor role to assign if matched */
  role: UserRole;
  /** Priority for rule evaluation (lower = higher priority) */
  priority?: number;
}

/**
 * Role mapping configuration for an IdP.
 */
export interface RoleMappingConfig {
  /** Default role if no rules match */
  defaultRole: UserRole;
  /** Ordered list of mapping rules */
  rules: RoleMappingRule[];
  /** Whether to sync roles on every login (vs only first login) */
  syncOnEveryLogin?: boolean;
}

/**
 * SAML-specific configuration.
 */
export interface SamlProviderConfig {
  /** IdP Entity ID */
  entityId: string;
  /** SSO URL for SAML authentication */
  ssoUrl: string;
  /** Single Logout URL (optional) */
  sloUrl?: string;
  /** IdP X.509 certificate for signature verification */
  certificate: string;
  /** Name ID format */
  nameIdFormat?: "emailAddress" | "persistent" | "transient" | "unspecified";
  /** Attribute mappings for claims */
  attributeMappings?: Record<string, string>;
}

/**
 * OIDC-specific configuration.
 */
export interface OidcProviderConfig {
  /** OIDC discovery endpoint URL */
  discoveryEndpoint: string;
  /** OAuth2 client ID */
  clientId: string;
  /** Requested scopes beyond openid */
  scopes?: string[];
  /** Custom authorization parameters */
  additionalParams?: Record<string, string>;
}

/**
 * Configuration for an external identity provider.
 */
export interface IdentityProviderConfig {
  id: string;
  type: IdentityProviderType;
  displayName: string;
  discoveryEndpoint: string;
  clientId: string;
  enabled: boolean;
  tenantId?: TenantId;
  iconUrl?: string;
  /** Role mapping configuration */
  roleMapping?: RoleMappingConfig;
  /** Allowed email domains (empty = all domains) */
  allowedDomains?: string[];
  /** OIDC-specific config (when type = 'oidc') */
  oidcConfig?: OidcProviderConfig;
  /** SAML-specific config (when type = 'saml') */
  samlConfig?: SamlProviderConfig;
  /** Provider health status */
  status?: SsoProviderStatus;
  /** Last successful authentication timestamp */
  lastSuccessfulAuthAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Link between external identity and TutorPutor user.
 */
export interface SsoUserLink {
  id?: string;
  userId: UserId;
  providerId: string;
  externalId: string;
  email: string;
  /** Display name from IdP */
  displayName?: string;
  /** Profile picture URL from IdP */
  avatarUrl?: string;
  /** Raw claims from last login (for debugging) */
  lastClaims?: Record<string, unknown>;
  linkedAt: string;
  lastLoginAt?: string;
}

/**
 * SSO login initiation request.
 */
export interface SsoLoginRequest {
  providerId: string;
  redirectUri?: string;
  /** Optional state to pass through the flow */
  state?: string;
}

/**
 * SSO login callback result.
 */
export interface SsoLoginResult {
  success: boolean;
  user?: UserSummary;
  isNewUser?: boolean;
  accessToken?: string;
  refreshToken?: string;
  expiresAt?: string;
  error?: {
    code: SsoErrorCode;
    message: string;
  };
}

/**
 * SSO error codes.
 */
export type SsoErrorCode =
  | "provider_not_found"
  | "provider_disabled"
  | "domain_not_allowed"
  | "token_verification_failed"
  | "user_provisioning_failed"
  | "link_already_exists"
  | "configuration_error"
  | "network_error";

// =============================================================================
// Compliance & Audit (Block 4 - Day 39-40)
// =============================================================================

/**
 * Status of a data export request.
 */
export type ExportStatus = "pending" | "processing" | "completed" | "failed" | "expired";

/**
 * Data export request for GDPR/FERPA/CCPA compliance.
 */
export interface DataExportRequest {
  id: string;
  userId: UserId;
  tenantId: TenantId;
  status: ExportStatus;
  requestedAt: string;
  estimatedCompletionAt: string;
  completedAt?: string;
  downloadUrl?: string;
  expiresAt?: string;
}

/**
 * Data deletion request.
 */
export interface DataDeletionRequest {
  id: string;
  userId: UserId;
  tenantId: TenantId;
  status: "scheduled" | "processing" | "completed" | "cancelled";
  requestedAt: string;
  scheduledDeletionAt: string;
  completedAt?: string;
  retentionDays: number;
}

/**
 * Audit event actions.
 */
export type AuditAction =
  | "data_export_requested"
  | "data_export_completed"
  | "data_deletion_requested"
  | "data_deletion_completed"
  | "sso_config_created"
  | "sso_config_updated"
  | "sso_config_deleted"
  | "role_assigned"
  | "role_revoked"
  | "user_login"
  | "user_logout"
  | "tenant_settings_updated";

/**
 * Audit event for compliance tracking.
 */
export interface AuditEvent {
  id: string;
  actorId: UserId;
  tenantId: TenantId;
  action: AuditAction;
  targetType: "user" | "tenant" | "sso_config" | "module" | "classroom";
  targetId: string;
  timestamp: string;
  ipAddress?: string;
  userAgent?: string;
  metadata: Record<string, unknown>;
}

// =============================================================================
// Social Learning & Collaboration (Block 4 - Day 41-45)
// =============================================================================

export type StudyGroupId = string & { readonly __studyGroupId: unique symbol };

/**
 * Study group for collaborative learning.
 */
export interface StudyGroup {
  id: StudyGroupId;
  tenantId: TenantId;
  classroomId: ClassroomId;
  name: string;
  description?: string;
  memberIds: UserId[];
  createdBy: UserId;
  createdAt: string;
  updatedAt: string;
}

/**
 * Reaction types for posts/threads.
 */
export type ReactionType = "👍" | "✅" | "❓" | "💡" | "🎉";

/**
 * Reaction on a post.
 */
export interface Reaction {
  id: string;
  postId: PostId;
  userId: UserId;
  type: ReactionType;
  createdAt: string;
}

/**
 * Mention in a post.
 */
export interface Mention {
  userId: UserId;
  displayName: string;
  startIndex: number;
  endIndex: number;
}

/**
 * Thread tag for categorization.
 */
export type ThreadTag = "question" | "explanation" | "resource" | "discussion" | "announcement";

/**
 * Presence status for live co-viewing.
 */
export interface ModulePresence {
  moduleSlug: string;
  users: Array<{
    userId: UserId;
    displayName: string;
    avatarUrl?: string;
    joinedAt: string;
  }>;
  totalCount: number;
}

// =============================================================================
// Institution Admin (Block 4 - Day 38)
// =============================================================================

/**
 * Tenant summary for admin dashboard.
 */
export interface TenantSummary {
  tenantId: TenantId;
  name: string;
  totalUsers: number;
  activeUsers: number;
  totalModules: number;
  totalClassrooms: number;
  createdAt: string;
  subscriptionTier: "free" | "basic" | "enterprise";
}

/**
 * Usage metrics for tenant analytics.
 */
export interface UsageMetrics {
  tenantId: TenantId;
  dateRange: { start: string; end: string };
  dailyActiveUsers: number;
  weeklyActiveUsers: number;
  monthlyActiveUsers: number;
  totalLearningEvents: number;
  totalAssessmentAttempts: number;
  averageSessionDurationMinutes: number;
  moduleCompletionRate: number;
  topModules: Array<{ moduleId: ModuleId; title: string; enrollments: number }>;
}

/**
 * Pagination arguments for list queries.
 */
export interface PaginationArgs {
  cursor?: string;
  limit: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
  /** @deprecated Use cursor-based pagination where possible */
  offset?: number;
  /** @deprecated Use cursor-based pagination where possible */
  page?: number;
}

/**
 * Paginated result wrapper.
 */
export interface PaginatedResult<T> {
  items: T[];
  nextCursor?: string;
  totalCount: number;
  hasMore: boolean;
  /** @deprecated Use totalCount */
  total?: number;
}

/**
 * Date range for analytics queries.
 */
export interface DateRange {
  start: string;
  end: string;
}

// =============================================================================
// Payments PSP-Agnostic (Block 4 - Day 47)
// =============================================================================

/**
 * Arguments for creating a checkout session.
 */
export interface CheckoutArgs {
  tenantId: TenantId;
  userId: UserId;
  listingId: MarketplaceListingId;
  successUrl: string;
  cancelUrl: string;
  metadata?: Record<string, string>;
}

/**
 * Payment status after verification.
 */
export interface PaymentStatus {
  sessionId: CheckoutSessionId;
  status: CheckoutStatus;
  amountCents: number;
  currency: string;
  paidAt?: string;
  receiptUrl?: string;
}

/**
 * Webhook result from PSP.
 */
export interface WebhookResult {
  eventType: "payment_completed" | "payment_failed" | "refund_issued";
  sessionId: CheckoutSessionId;
  success: boolean;
  error?: string;
}

// =============================================================================
// Subscription & Payment Management (Block 4 - Days 48-49)
// =============================================================================

export type SubscriptionId = string & { readonly __subscriptionId: unique symbol };
export type PaymentMethodId = string & { readonly __paymentMethodId: unique symbol };
export type InvoiceId = string & { readonly __invoiceId: unique symbol };
export type TransactionId = string & { readonly __transactionId: unique symbol };

/**
 * Subscription plan tiers.
 */
export type SubscriptionTier = "free" | "starter" | "professional" | "institution" | "enterprise";

/**
 * Subscription billing interval.
 */
export type BillingInterval = "monthly" | "quarterly" | "annual";

/**
 * Subscription status.
 */
export type SubscriptionStatus =
  | "active"
  | "trialing"
  | "past_due"
  | "canceled"
  | "paused"
  | "incomplete"
  | "incomplete_expired";

/**
 * Payment method type.
 */
export type PaymentMethodType = "card" | "bank_account" | "paypal" | "invoice";

/**
 * Subscription plan definition.
 */
export interface SubscriptionPlan {
  id: string;
  name: string;
  tier: SubscriptionTier;
  description: string;
  features: string[];
  limits: PlanLimits;
  pricing: PlanPricing[];
  isActive: boolean;
  trialDays: number;
}

/**
 * Plan usage limits.
 */
export interface PlanLimits {
  maxUsers: number;
  maxModules: number;
  maxStorageGB: number;
  maxClassrooms: number;
  maxVrSessions: number;
  analyticsRetentionDays: number;
  supportLevel: "community" | "email" | "priority" | "dedicated";
  customBranding: boolean;
  ssoEnabled: boolean;
  apiAccess: boolean;
}

/**
 * Plan pricing for different intervals.
 */
export interface PlanPricing {
  interval: BillingInterval;
  amountCents: number;
  currency: string;
  stripePriceId?: string;
}

/**
 * Subscription record.
 */
export interface Subscription {
  id: SubscriptionId;
  tenantId: TenantId;
  planId: string;
  tier: SubscriptionTier;
  status: SubscriptionStatus;
  billingInterval: BillingInterval;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  cancelAtPeriodEnd: boolean;
  canceledAt?: string;
  trialStart?: string;
  trialEnd?: string;
  stripeSubscriptionId?: string;
  stripeCustomerId?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Payment method record.
 */
export interface PaymentMethod {
  id: PaymentMethodId;
  tenantId: TenantId;
  type: PaymentMethodType;
  isDefault: boolean;
  card?: CardDetails;
  bankAccount?: BankAccountDetails;
  billingAddress?: BillingAddress;
  stripePaymentMethodId?: string;
  createdAt: string;
  expiresAt?: string;
}

/**
 * Card payment method details.
 */
export interface CardDetails {
  brand: "visa" | "mastercard" | "amex" | "discover" | "other";
  last4: string;
  expMonth: number;
  expYear: number;
  fingerprint?: string;
}

/**
 * Bank account payment method details.
 */
export interface BankAccountDetails {
  bankName: string;
  last4: string;
  accountType: "checking" | "savings";
  routingNumber?: string;
}

/**
 * Billing address.
 */
export interface BillingAddress {
  name: string;
  line1: string;
  line2?: string;
  city: string;
  state?: string;
  postalCode: string;
  country: string;
}

/**
 * Invoice record.
 */
export interface Invoice {
  id: InvoiceId;
  tenantId: TenantId;
  subscriptionId: SubscriptionId;
  number: string;
  status: "draft" | "open" | "paid" | "void" | "uncollectible";
  currency: string;
  subtotalCents: number;
  taxCents: number;
  totalCents: number;
  amountPaidCents: number;
  amountDueCents: number;
  dueDate: string;
  paidAt?: string;
  lineItems: InvoiceLineItem[];
  stripeInvoiceId?: string;
  hostedInvoiceUrl?: string;
  invoicePdfUrl?: string;
  createdAt: string;
}

/**
 * Invoice line item.
 */
export interface InvoiceLineItem {
  description: string;
  quantity: number;
  unitAmountCents: number;
  amountCents: number;
  periodStart?: string;
  periodEnd?: string;
}

/**
 * Payment transaction record.
 */
export interface PaymentTransaction {
  id: TransactionId;
  tenantId: TenantId;
  invoiceId?: InvoiceId;
  paymentMethodId: PaymentMethodId;
  type: "charge" | "refund" | "adjustment";
  status: "pending" | "succeeded" | "failed" | "canceled";
  amountCents: number;
  currency: string;
  failureReason?: string;
  stripePaymentIntentId?: string;
  stripeChargeId?: string;
  receiptUrl?: string;
  createdAt: string;
  processedAt?: string;
}

/**
 * Subscription usage metrics.
 */
export interface SubscriptionUsage {
  subscriptionId: SubscriptionId;
  period: { start: string; end: string };
  users: { current: number; limit: number };
  modules: { current: number; limit: number };
  storageGB: { current: number; limit: number };
  classrooms: { current: number; limit: number };
  vrSessions: { current: number; limit: number };
}

/**
 * Subscription change preview.
 */
export interface SubscriptionChangePreview {
  currentPlan: SubscriptionPlan;
  newPlan: SubscriptionPlan;
  proratedAmountCents: number;
  effectiveDate: string;
  immediateCharge: boolean;
}

/**
 * Payment webhook event.
 */
export interface PaymentWebhookEvent {
  id: string;
  type:
  | "subscription.created"
  | "subscription.updated"
  | "subscription.deleted"
  | "invoice.created"
  | "invoice.paid"
  | "invoice.payment_failed"
  | "payment_method.attached"
  | "payment_method.detached"
  | "customer.subscription.trial_will_end";
  data: Record<string, unknown>;
  stripeEventId: string;
  processedAt?: string;
  createdAt: string;
}

// =============================================================================
// LTI 1.3 Integration (Block 4 - Day 50)
// =============================================================================

export type LtiPlatformId = string & { readonly __ltiPlatformId: unique symbol };
export type LtiDeploymentId = string & { readonly __ltiDeploymentId: unique symbol };
export type LtiContextId = string & { readonly __ltiContextId: unique symbol };
export type LtiResourceLinkId = string & { readonly __ltiResourceLinkId: unique symbol };

/**
 * LTI 1.3 Platform (Tool Consumer) registration.
 */
export interface LtiPlatform {
  id: LtiPlatformId;
  tenantId: TenantId;
  name: string;
  issuer: string;
  clientId: string;
  deploymentId: string;
  authLoginUrl: string;
  authTokenUrl: string;
  jwksUrl: string;
  publicKeyPem?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * LTI 1.3 Launch context.
 */
export interface LtiLaunchContext {
  platformId: LtiPlatformId;
  deploymentId: string;
  contextId: LtiContextId;
  contextType: "CourseOffering" | "CourseSection" | "Group";
  contextLabel: string;
  contextTitle: string;
  resourceLinkId: LtiResourceLinkId;
  resourceLinkTitle: string;
  targetLinkUri: string;
}

/**
 * LTI user claims from launch.
 */
export interface LtiUserClaims {
  sub: string;
  name?: string;
  givenName?: string;
  familyName?: string;
  email?: string;
  picture?: string;
  roles: LtiRole[];
}

/**
 * LTI 1.3 roles.
 */
export type LtiRole =
  | "http://purl.imsglobal.org/vocab/lis/v2/membership#Learner"
  | "http://purl.imsglobal.org/vocab/lis/v2/membership#Instructor"
  | "http://purl.imsglobal.org/vocab/lis/v2/membership#Administrator"
  | "http://purl.imsglobal.org/vocab/lis/v2/membership#ContentDeveloper"
  | "http://purl.imsglobal.org/vocab/lis/v2/membership#Mentor";

/**
 * LTI Deep Linking request.
 */
export interface LtiDeepLinkingRequest {
  platformId: LtiPlatformId;
  deploymentId: string;
  deepLinkingSettingsData: string;
  acceptTypes: string[];
  acceptPresentationDocumentTargets: string[];
  acceptMultiple: boolean;
  autoCreate: boolean;
}

/**
 * LTI Deep Linking content item.
 */
export interface LtiContentItem {
  type: "ltiResourceLink" | "link" | "file" | "html" | "image";
  title: string;
  text?: string;
  url?: string;
  icon?: { url: string; width?: number; height?: number };
  thumbnail?: { url: string; width?: number; height?: number };
  custom?: Record<string, string>;
  lineItem?: LtiLineItem;
}

/**
 * LTI Line item for gradebook integration.
 */
export interface LtiLineItem {
  scoreMaximum: number;
  label: string;
  resourceId?: string;
  tag?: string;
  startDateTime?: string;
  endDateTime?: string;
}

/**
 * LTI Assignment and Grade Services (AGS) score.
 */
export interface LtiScore {
  userId: string;
  scoreGiven: number;
  scoreMaximum: number;
  activityProgress: "Initialized" | "Started" | "InProgress" | "Submitted" | "Completed";
  gradingProgress: "FullyGraded" | "Pending" | "PendingManual" | "Failed" | "NotReady";
  timestamp: string;
  comment?: string;
}

/**
 * LTI Names and Role Provisioning Services (NRPS) member.
 */
export interface LtiMember {
  userId: string;
  roles: LtiRole[];
  name?: string;
  email?: string;
  status: "Active" | "Inactive" | "Deleted";
  ltiContextId: LtiContextId;
}

/**
 * LTI launch session.
 */
export interface LtiSession {
  id: string;
  platformId: LtiPlatformId;
  deploymentId: string;
  userId: UserId;
  ltiUserId: string;
  contextId: LtiContextId;
  resourceLinkId: LtiResourceLinkId;
  roles: LtiRole[];
  targetModuleId?: ModuleId;
  launchData: Record<string, unknown>;
  createdAt: string;
  expiresAt: string;
}

/**
 * LTI grade passback request.
 */
export interface LtiGradePassback {
  sessionId: string;
  lineItemId: string;
  score: LtiScore;
}

/**
 * LTI grade passback result.
 */
export interface LtiGradePassbackResult {
  success: boolean;
  lineItemId: string;
  userId: string;
  scoreGiven: number;
  error?: string;
  passedAt?: string;
}



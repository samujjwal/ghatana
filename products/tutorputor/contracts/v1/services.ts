import type {
  DashboardSummary,
  ModuleSummary,
  ModuleDetail,
  Enrollment,
  TutorResponsePayload,
  TenantId,
  UserId,
  ModuleId,
  EnrollmentId,
  Assessment,
  AssessmentSummary,
  AssessmentId,
  AssessmentItemId,
  AssessmentAttempt,
  AssessmentAttemptId,
  AssessmentGenerationInput,
  AssessmentGenerationResult,
  ModuleDraftInput,
  ModuleDraftPatch,
  AnalyticsSummary,
  AdvancedAnalyticsSummary,
  StudentRiskIndicator,
  ModuleDifficultyHeatmap,
  UsageAnalytics,
  LearningEventInput,
  MarketplaceListing,
  MarketplaceListingId,
  // New imports for extended services
  LearningPath,
  LearningPathRecommendation,
  PathwayConstraints,
  Classroom,
  ClassroomId,
  TeacherDashboardSummary,
  ClassroomProgress,
  Thread,
  ThreadId,
  ThreadStatus,
  Post,
  PostId,
  UserRole,
  SimulationLaunchToken,
  CheckoutSession,
  CheckoutSessionId,
  Purchase,
  LTIValidationResult,
  LTIDeepLinkingContent,
  SimulationTemplate,
  SimulationTemplateInput,
  SimulationTemplateUpdate,
  SimulationTemplateStatus
} from "./types";
import type { ParsedIntent } from "./simulation/types";

export interface ContentService {
  getModuleBySlug(
    tenantId: TenantId,
    slug: string,
    userId?: UserId
  ): Promise<{ module: ModuleDetail; enrollment?: Enrollment }>;

  listModules(args: {
    tenantId: TenantId;
    domain?: string;
    status?: "PUBLISHED";
    cursor?: string;
    limit?: number;
    userId?: UserId;
    query?: string; // Natural language query
  }): Promise<{ items: ModuleSummary[]; nextCursor?: string | null }>;
}

export interface LearningService {
  getDashboard(tenantId: TenantId, userId: UserId): Promise<DashboardSummary>;

  enrollInModule(
    tenantId: TenantId,
    userId: UserId,
    moduleId: ModuleId
  ): Promise<Enrollment>;

  updateProgress(args: {
    tenantId: TenantId;
    enrollmentId: EnrollmentId;
    progressPercent: number;
    timeSpentSecondsDelta: number;
  }): Promise<Enrollment>;
}

export interface AIProxyService {
  handleTutorQuery(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId?: ModuleId;
    question: string;
    locale?: string;
  }): Promise<TutorResponsePayload>;

  parseSimulationIntent(args: {
    userInput: string;
    context?: string;
  }): Promise<ParsedIntent>;

  explainSimulation(args: {
    manifest: any; // SimulationManifest
    query: string;
  }): Promise<string>;

  generateLearningUnitDraft(args: {
    topic: string;
    targetAudience: string;
    learningObjectives?: string[];
  }): Promise<any>; // Returns partial ModuleDraftInput

  parseContentQuery(query: string): Promise<{
    domain?: string;
    difficulty?: string;
    tags?: string[];
    textSearch?: string;
  }>;
}

export interface AssessmentService {
  listAssessments(args: {
    tenantId: TenantId;
    moduleId?: ModuleId;
    status?: AssessmentSummary["status"];
    cursor?: AssessmentId | null;
    limit?: number;
  }): Promise<{ items: AssessmentSummary[]; nextCursor?: AssessmentId | null }>;

  getAssessment(args: {
    tenantId: TenantId;
    assessmentId: AssessmentId;
    userId: UserId;
    includeDraft?: boolean;
  }): Promise<Assessment>;

  generateAssessmentItems(
    args: AssessmentGenerationInput & { userId: UserId }
  ): Promise<AssessmentGenerationResult>;

  startAttempt(args: {
    tenantId: TenantId;
    assessmentId: AssessmentId;
    userId: UserId;
  }): Promise<AssessmentAttempt>;

  submitAttempt(args: {
    tenantId: TenantId;
    attemptId: AssessmentAttemptId;
    userId: UserId;
    responses: AssessmentAttempt["responses"];
    locale?: string;
  }): Promise<AssessmentAttempt>;
}

export interface CMSService {
  listModules(args: {
    tenantId: TenantId;
    status?: ModuleSummary["status"];
    cursor?: ModuleId | null;
    limit?: number;
  }): Promise<{ items: ModuleSummary[]; nextCursor?: ModuleId | null }>;

  createModuleDraft(args: {
    tenantId: TenantId;
    authorId: UserId;
    input: ModuleDraftInput;
  }): Promise<ModuleDetail>;

  updateModuleDraft(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    userId: UserId;
    patch: ModuleDraftPatch;
  }): Promise<ModuleDetail>;

  publishModule(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    userId: UserId;
  }): Promise<ModuleDetail>;

  generateDraftFromIntent(args: {
    tenantId: TenantId;
    authorId: UserId;
    intent: string;
  }): Promise<ModuleDetail>;
}

export interface AnalyticsService {
  recordEvent(args: {
    tenantId: TenantId;
    event: LearningEventInput;
  }): Promise<void>;

  getSummary(args: {
    tenantId: TenantId;
    moduleId?: ModuleId;
  }): Promise<AnalyticsSummary>;

  /**
   * Get advanced analytics with predictive insights.
   */
  getAdvancedAnalytics(args: {
    tenantId: TenantId;
    classroomId?: ClassroomId;
    period?: "daily" | "weekly" | "monthly";
  }): Promise<AdvancedAnalyticsSummary>;

  /**
   * Get at-risk students for early warning.
   */
  getAtRiskStudents(args: {
    tenantId: TenantId;
    classroomId?: ClassroomId;
    minRiskLevel?: "low" | "medium" | "high" | "critical";
  }): Promise<StudentRiskIndicator[]>;

  /**
   * Get module difficulty heatmap.
   */
  getDifficultyHeatmap(args: {
    tenantId: TenantId;
    moduleIds?: ModuleId[];
  }): Promise<ModuleDifficultyHeatmap[]>;

  /**
   * Get usage trends over time.
   */
  getUsageTrends(args: {
    tenantId: TenantId;
    period: "daily" | "weekly" | "monthly";
    days?: number;
  }): Promise<UsageAnalytics>;
}

export interface MarketplaceService {
  listListings(args: {
    tenantId: TenantId;
    status?: MarketplaceListing["status"];
    visibility?: MarketplaceListing["visibility"];
    cursor?: MarketplaceListingId | null;
    limit?: number;
  }): Promise<{ items: MarketplaceListing[]; nextCursor?: MarketplaceListingId | null }>;

  createListing(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    creatorId: UserId;
    visibility: MarketplaceListing["visibility"];
    priceCents: number;
  }): Promise<MarketplaceListing>;

  updateListing(args: {
    tenantId: TenantId;
    listingId: MarketplaceListingId;
    userId: UserId;
    status?: MarketplaceListing["status"];
    visibility?: MarketplaceListing["visibility"];
    priceCents?: number;
  }): Promise<MarketplaceListing>;

  /**
   * Admin-only: Update listing without creator check.
   */
  adminUpdateListing(args: {
    tenantId: TenantId;
    listingId: MarketplaceListingId;
    status?: MarketplaceListing["status"];
    visibility?: MarketplaceListing["visibility"];
  }): Promise<MarketplaceListing>;

  listSimulationTemplates(args: {
    tenantId: TenantId;
    domains?: string[];
    difficulties?: SimulationTemplate["difficulty"][];
    tags?: string[];
    isPremium?: boolean;
    isVerified?: boolean;
    minRating?: number;
    status?: SimulationTemplateStatus;
    search?: string;
    sortBy?: "popularity" | "rating" | "newest" | "mostUsed" | "alphabetical";
    sortOrder?: "asc" | "desc";
    page?: number;
    pageSize?: number;
  }): Promise<{
    templates: SimulationTemplate[];
    total: number;
    page: number;
    pageSize: number;
    hasMore: boolean;
  }>;

  createTemplate(args: {
    tenantId: TenantId;
    createdBy: UserId;
    input: SimulationTemplateInput;
  }): Promise<SimulationTemplate>;

  updateTemplate(args: {
    tenantId: TenantId;
    templateId: string;
    updatedBy: UserId;
    patch: SimulationTemplateUpdate;
  }): Promise<SimulationTemplate>;

  changeTemplateStatus(args: {
    tenantId: TenantId;
    templateId: string;
    updatedBy: UserId;
    status: SimulationTemplateStatus;
  }): Promise<SimulationTemplate>;

  deleteTemplate(args: {
    tenantId: TenantId;
    templateId: string;
    deletedBy: UserId;
  }): Promise<{ deleted: boolean; warnings?: string[] }>;

  /**
   * Admin-only: Get marketplace statistics.
   */
  getMarketplaceStats(args: {
    tenantId: TenantId;
  }): Promise<{
    totalListings: number;
    activeListings: number;
    draftListings: number;
    archivedListings: number;
    totalRevenueCents: number;
    topListings: Array<{ listingId: string; purchaseCount: number }>;
  }>;

  /**
   * Admin-only: Get simulation template statistics.
   */
  getTemplateStats(args: {
    tenantId: TenantId;
  }): Promise<{
    totalTemplates: number;
    verifiedTemplates: number;
    premiumTemplates: number;
    byDomain: Record<string, number>;
  }>;

  /**
   * Admin-only: Update simulation template flags.
   */
  adminUpdateTemplate(args: {
    tenantId: TenantId;
    templateId: string;
    isVerified?: boolean;
    isPremium?: boolean;
  }): Promise<SimulationTemplate>;
}

// =============================================================================
// Pathways Service - AI-Based Learning Path Generation
// =============================================================================

export interface PathwaysService {
  /**
   * Generate a personalized learning pathway based on goal and constraints.
   * Uses AI to recommend module sequences considering prerequisites and difficulty.
   */
  generatePathway(args: {
    tenantId: TenantId;
    userId: UserId;
    goal: string;
    constraints?: PathwayConstraints;
  }): Promise<LearningPathRecommendation>;

  /**
   * Get the active learning path for a user.
   */
  getPathwayForUser(args: {
    tenantId: TenantId;
    userId: UserId;
  }): Promise<LearningPath | null>;

  /**
   * Create a learning path from a recommendation.
   */
  createPathway(args: {
    tenantId: TenantId;
    userId: UserId;
    title: string;
    goal: string;
    moduleIds: ModuleId[];
  }): Promise<LearningPath>;

  /**
   * Mark a module as completed and advance the pathway.
   */
  advancePathway(args: {
    tenantId: TenantId;
    userId: UserId;
    completedModuleId: ModuleId;
  }): Promise<LearningPath>;
}

// =============================================================================
// Teacher Service - Classroom Management
// =============================================================================

export interface TeacherService {
  /**
   * Get teacher dashboard with classrooms, activity, and at-risk students.
   */
  getTeacherDashboard(args: {
    tenantId: TenantId;
    teacherId: UserId;
  }): Promise<TeacherDashboardSummary>;

  /**
   * Create a new classroom.
   */
  createClassroom(args: {
    tenantId: TenantId;
    teacherId: UserId;
    name: string;
    description?: string;
  }): Promise<Classroom>;

  /**
   * Get classroom details by ID.
   */
  getClassroom(args: {
    tenantId: TenantId;
    classroomId: ClassroomId;
  }): Promise<Classroom>;

  /**
   * Add a student to a classroom.
   */
  addStudentToClassroom(args: {
    tenantId: TenantId;
    classroomId: ClassroomId;
    studentId: UserId;
    displayName: string;
    email?: string;
  }): Promise<Classroom>;

  /**
   * Remove a student from a classroom.
   */
  removeStudentFromClassroom(args: {
    tenantId: TenantId;
    classroomId: ClassroomId;
    studentId: UserId;
  }): Promise<Classroom>;

  /**
   * Assign a module to a classroom.
   */
  assignModule(args: {
    tenantId: TenantId;
    classroomId: ClassroomId;
    moduleId: ModuleId;
    dueAt?: string;
  }): Promise<Classroom>;

  /**
   * Get progress for all assigned modules in a classroom.
   */
  getClassroomProgress(args: {
    tenantId: TenantId;
    classroomId: ClassroomId;
  }): Promise<ClassroomProgress[]>;
}

// =============================================================================
// Collaboration Service - Q&A, Discussions
// =============================================================================

export interface CollaborationService {
  /**
   * Create a new discussion thread.
   */
  postQuestion(args: {
    tenantId: TenantId;
    userId: UserId;
    authorName: string;
    moduleId?: ModuleId;
    title: string;
    content: string;
  }): Promise<Thread>;

  /**
   * Reply to an existing thread.
   */
  reply(args: {
    tenantId: TenantId;
    userId: UserId;
    authorName: string;
    threadId: ThreadId;
    content: string;
  }): Promise<Post>;

  /**
   * List threads with optional filters.
   */
  listThreads(args: {
    tenantId: TenantId;
    moduleId?: ModuleId;
    status?: ThreadStatus;
    cursor?: ThreadId | null;
    limit?: number;
  }): Promise<{ items: Thread[]; nextCursor?: ThreadId | null }>;

  /**
   * Get a single thread by ID.
   */
  getThread(args: {
    tenantId: TenantId;
    threadId: ThreadId;
  }): Promise<Thread>;

  /**
   * Mark a post as the accepted answer.
   */
  markAsAnswer(args: {
    tenantId: TenantId;
    userId: UserId;
    threadId: ThreadId;
    postId: PostId;
  }): Promise<Thread>;

  /**
   * Close a thread.
   */
  closeThread(args: {
    tenantId: TenantId;
    userId: UserId;
    threadId: ThreadId;
  }): Promise<Thread>;
}

// =============================================================================
// Simulation Service - VR/AR Launch
// =============================================================================

export interface SimulationService {
  /**
   * Generate a launch token for a VR/AR simulation.
   */
  launchSimulation(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId: ModuleId;
    blockId: string;
  }): Promise<SimulationLaunchToken>;

  /**
   * Validate a simulation token.
   */
  validateToken(args: {
    token: string;
  }): Promise<{ valid: boolean; expiresAt?: string }>;
}

// =============================================================================
// Billing Service - Marketplace Payments
// =============================================================================

export interface BillingService {
  /**
   * Create a checkout session for purchasing a marketplace listing.
   */
  createCheckoutSession(args: {
    tenantId: TenantId;
    userId: UserId;
    listingId: MarketplaceListingId;
    successUrl?: string;
    cancelUrl?: string;
  }): Promise<CheckoutSession>;

  /**
   * Verify payment status for a checkout session.
   */
  verifyPayment(args: {
    tenantId: TenantId;
    sessionId: CheckoutSessionId;
  }): Promise<CheckoutSession>;

  /**
   * List user's purchase history.
   */
  listPurchases(args: {
    tenantId: TenantId;
    userId: UserId;
    cursor?: string | null;
    limit?: number;
  }): Promise<{ items: Purchase[]; nextCursor?: string | null }>;

  /**
   * Check if a user has purchased a specific module.
   */
  hasPurchased(args: {
    tenantId: TenantId;
    userId: UserId;
    moduleId: ModuleId;
  }): Promise<boolean>;
}

// =============================================================================
// LTI Service - LMS Integration (Canvas, Blackboard, Google Classroom)
// =============================================================================

export interface LTIService {
  /**
   * Validate an LTI 1.3 launch request.
   */
  validateLaunch(args: {
    token: string;
    nonce: string;
  }): Promise<LTIValidationResult>;

  /**
   * Generate deep linking content for module selection.
   */
  getDeepLinkingContent(args: {
    tenantId: TenantId;
    moduleIds: ModuleId[];
    baseUrl: string;
  }): Promise<LTIDeepLinkingContent[]>;

  /**
   * Register an LTI platform (e.g., Canvas, Blackboard).
   */
  registerPlatform(args: {
    tenantId: TenantId;
    platformName: string;
    issuer: string;
    clientId: string;
    jwksUrl: string;
    authUrl: string;
    tokenUrl: string;
  }): Promise<{ platformId: string }>;
}

// =============================================================================
// SSO Service - Enterprise Identity Federation (Block 4 - Days 36-40)
// =============================================================================

import type {
  IdentityProviderConfig,
  SsoUserLink,
  SsoLoginRequest,
  SsoLoginResult,
  RoleMappingConfig,
  PaginatedResult,
  PaginationArgs,
  UserSummary,
} from "./types";

/**
 * Service for managing SSO providers and identity federation.
 * 
 * Reuses libs/java/auth OidcTokenVerifier for OIDC token validation.
 */
export interface SsoService {
  // ---------------------------------------------------------------------------
  // Provider Management (Admin)
  // ---------------------------------------------------------------------------

  /**
   * List all identity providers for a tenant.
   */
  listProviders(args: {
    tenantId: TenantId;
  }): Promise<IdentityProviderConfig[]>;

  /**
   * Get enabled providers for login page.
   * Returns only enabled providers with minimal info.
   */
  getLoginProviders(args: {
    tenantSlug: string;
  }): Promise<Array<{
    id: string;
    displayName: string;
    type: "oidc" | "saml";
    iconUrl?: string;
  }>>;

  /**
   * Get a specific provider by ID.
   */
  getProvider(args: {
    tenantId: TenantId;
    providerId: string;
  }): Promise<IdentityProviderConfig>;

  /**
   * Create a new identity provider.
   */
  createProvider(args: {
    tenantId: TenantId;
    config: Omit<IdentityProviderConfig, "id" | "status" | "createdAt" | "updatedAt">;
  }): Promise<IdentityProviderConfig>;

  /**
   * Update an identity provider.
   */
  updateProvider(args: {
    tenantId: TenantId;
    providerId: string;
    patch: Partial<Omit<IdentityProviderConfig, "id" | "tenantId" | "createdAt">>;
  }): Promise<IdentityProviderConfig>;

  /**
   * Delete an identity provider.
   * User links are preserved (soft delete).
   */
  deleteProvider(args: {
    tenantId: TenantId;
    providerId: string;
  }): Promise<void>;

  /**
   * Test provider configuration.
   * Attempts to fetch discovery document/metadata.
   */
  testProvider(args: {
    tenantId: TenantId;
    providerId: string;
  }): Promise<{
    success: boolean;
    discoveryEndpoint?: string;
    error?: string;
  }>;

  // ---------------------------------------------------------------------------
  // Authentication Flow
  // ---------------------------------------------------------------------------

  /**
   * Initiate SSO login flow.
   * Returns URL to redirect user to IdP.
   */
  initiateLogin(args: SsoLoginRequest & {
    tenantId: TenantId;
  }): Promise<{
    redirectUrl: string;
    state: string;
  }>;

  /**
   * Handle IdP callback.
   * Verifies token, provisions user if needed, creates session.
   */
  handleCallback(args: {
    tenantId: TenantId;
    providerId: string;
    code: string;
    state: string;
  }): Promise<SsoLoginResult>;

  /**
   * Handle SAML assertion callback.
   */
  handleSamlCallback(args: {
    tenantId: TenantId;
    providerId: string;
    samlResponse: string;
    relayState?: string;
  }): Promise<SsoLoginResult>;

  // ---------------------------------------------------------------------------
  // User Link Management
  // ---------------------------------------------------------------------------

  /**
   * List SSO-linked users for a tenant.
   */
  listLinkedUsers(args: {
    tenantId: TenantId;
    providerId?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<SsoUserLink & { user: UserSummary }>>;

  /**
   * Get SSO links for a specific user.
   */
  getUserLinks(args: {
    tenantId: TenantId;
    userId: UserId;
  }): Promise<SsoUserLink[]>;

  /**
   * Unlink an SSO identity from a user.
   */
  unlinkUser(args: {
    tenantId: TenantId;
    linkId: string;
  }): Promise<void>;

  /**
   * Force re-sync user from IdP claims.
   */
  syncUserFromIdp(args: {
    tenantId: TenantId;
    linkId: string;
  }): Promise<SsoUserLink>;

  // ---------------------------------------------------------------------------
  // Role Mapping
  // ---------------------------------------------------------------------------

  /**
   * Get role mapping configuration for a provider.
   */
  getRoleMapping(args: {
    tenantId: TenantId;
    providerId: string;
  }): Promise<RoleMappingConfig>;

  /**
   * Update role mapping configuration.
   */
  updateRoleMapping(args: {
    tenantId: TenantId;
    providerId: string;
    config: RoleMappingConfig;
  }): Promise<RoleMappingConfig>;

  /**
   * Preview role mapping for a set of claims.
   * Useful for testing mapping rules.
   */
  previewRoleMapping(args: {
    tenantId: TenantId;
    providerId: string;
    claims: Record<string, unknown>;
  }): Promise<{
    matchedRole: string;
    matchedRule?: string;
    allClaims: Record<string, unknown>;
  }>;
}

// =============================================================================
// Compliance Service - Data Privacy & Audit (Block 4 - Days 39-40)
// =============================================================================

import type {
  DataExportRequest,
  DataDeletionRequest,
  AuditEvent,
  ExportStatus,
  DateRange,
} from "./types";

/**
 * Service for GDPR/FERPA/CCPA compliance operations.
 */
export interface ComplianceService {
  /**
   * Request data export for a user (GDPR Article 20).
   */
  requestUserExport(args: {
    tenantId: TenantId;
    userId: UserId;
    requestedBy: UserId;
  }): Promise<DataExportRequest>;

  /**
   * Get status of an export request.
   */
  getExportStatus(args: {
    tenantId: TenantId;
    requestId: string;
  }): Promise<DataExportRequest>;

  /**
   * Download completed export.
   */
  downloadExport(args: {
    tenantId: TenantId;
    requestId: string;
  }): Promise<{
    downloadUrl: string;
    expiresAt: string;
  }>;

  /**
   * Request user data deletion (GDPR Article 17).
   */
  requestUserDeletion(args: {
    tenantId: TenantId;
    userId: UserId;
    requestedBy: UserId;
    reason?: string;
  }): Promise<DataDeletionRequest>;

  /**
   * Cancel a pending deletion request.
   */
  cancelDeletionRequest(args: {
    tenantId: TenantId;
    requestId: string;
  }): Promise<DataDeletionRequest>;

  /**
   * Get deletion request status.
   */
  getDeletionStatus(args: {
    tenantId: TenantId;
    requestId: string;
  }): Promise<DataDeletionRequest>;

  /**
   * Query audit events for a tenant.
   */
  queryAuditEvents(args: {
    tenantId: TenantId;
    actorId?: UserId;
    action?: AuditEvent["action"];
    targetType?: AuditEvent["targetType"];
    dateRange?: DateRange;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<AuditEvent>>;
}

// =============================================================================
// Institution Admin Service - Tenant Management (Block 4 - Day 38)
// =============================================================================

import type { TenantSummary, UsageMetrics } from "./types";

/**
 * Service for institutional administrators.
 */
export interface InstitutionAdminService {
  /**
   * Get summary for a tenant.
   */
  getTenantSummary(args: {
    tenantId: TenantId;
  }): Promise<TenantSummary>;

  /**
   * List users in a tenant.
   */
  listTenantUsers(args: {
    tenantId: TenantId;
    role?: string;
    searchQuery?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<UserSummary>>;

  /**
   * Get usage metrics for a tenant.
   */
  getTenantUsage(args: {
    tenantId: TenantId;
    dateRange: DateRange;
  }): Promise<UsageMetrics>;

  /**
   * Bulk import users from CSV/Excel.
   */
  bulkImportUsers(args: {
    tenantId: TenantId;
    importedBy: UserId;
    users: Array<{
      email: string;
      displayName: string;
      role: string;
      classroomIds?: string[];
    }>;
    sendInvites?: boolean;
  }): Promise<{
    imported: number;
    failed: number;
    errors: Array<{ email: string; reason: string }>;
  }>;

  /**
   * Assign a learning path to all students in a classroom.
   */
  assignPathToClassroom(args: {
    tenantId: TenantId;
    classroomId: ClassroomId;
    pathwayId: string;
    assignedBy: UserId;
  }): Promise<{
    assignedCount: number;
  }>;

  /**
   * Update a user's role.
   */
  updateUserRole(args: {
    tenantId: TenantId;
    userId: UserId;
    newRole: UserRole;
    updatedBy: UserId;
  }): Promise<UserSummary>;
}

// =============================================================================
// Social Learning Service - Study Groups, Forums, Peer Tutoring (Block 4 - Days 41-45)
// =============================================================================

import type {
  StudyGroup,
  StudyGroupVisibility,
  StudyGroupMember,
  StudyGroupRole,
  StudyGroupJoinRequest,
  StudyGroupInvite,
  StudySession,
  StudySessionType,
  SessionRsvp,
  Forum,
  ForumScope,
  ForumTopic,
  ForumPost,
  PostReaction,
  ReactionType,
  TutorProfile,
  TutoringRequest,
  TutoringSession,
  TutoringReview,
  ChatRoom,
  ChatMessage,
  SharedNote,
  SocialActivity,
  SocialNotification,
} from "./social";

/**
 * Service for study group management.
 */
export interface StudyGroupService {
  // ---------------------------------------------------------------------------
  // Group CRUD
  // ---------------------------------------------------------------------------

  /**
   * Create a new study group.
   */
  createGroup(args: {
    tenantId: TenantId;
    createdBy: UserId;
    name: string;
    description: string;
    visibility: StudyGroupVisibility;
    subjects: string[];
    moduleIds?: ModuleId[];
    maxMembers?: number;
    requireApproval?: boolean;
  }): Promise<StudyGroup>;

  /**
   * Get a study group by ID.
   */
  getGroup(args: {
    tenantId: TenantId;
    groupId: string;
    userId?: UserId;
  }): Promise<StudyGroup & { membership?: StudyGroupMember }>;

  /**
   * List study groups.
   */
  listGroups(args: {
    tenantId: TenantId;
    userId?: UserId;
    visibility?: StudyGroupVisibility;
    subject?: string;
    moduleId?: ModuleId;
    searchQuery?: string;
    memberOf?: boolean; // Only groups user is member of
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<StudyGroup>>;

  /**
   * Update a study group.
   */
  updateGroup(args: {
    tenantId: TenantId;
    groupId: string;
    userId: UserId;
    patch: Partial<Pick<StudyGroup, 'name' | 'description' | 'visibility' | 'maxMembers' | 'requireApproval' | 'subjects' | 'coverImageUrl'>>;
  }): Promise<StudyGroup>;

  /**
   * Archive a study group.
   */
  archiveGroup(args: {
    tenantId: TenantId;
    groupId: string;
    userId: UserId;
  }): Promise<StudyGroup>;

  // ---------------------------------------------------------------------------
  // Membership
  // ---------------------------------------------------------------------------

  /**
   * Join a study group (for public groups).
   */
  joinGroup(args: {
    tenantId: TenantId;
    groupId: string;
    userId: UserId;
  }): Promise<StudyGroupMember>;

  /**
   * Request to join a private group.
   */
  requestJoin(args: {
    tenantId: TenantId;
    groupId: string;
    userId: UserId;
    message?: string;
  }): Promise<StudyGroupJoinRequest>;

  /**
   * Handle join request (approve/reject).
   */
  handleJoinRequest(args: {
    tenantId: TenantId;
    requestId: string;
    reviewerId: UserId;
    approved: boolean;
    rejectionReason?: string;
  }): Promise<StudyGroupJoinRequest>;

  /**
   * List pending join requests for a group.
   */
  listJoinRequests(args: {
    tenantId: TenantId;
    groupId: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<StudyGroupJoinRequest>>;

  /**
   * Invite a user to a group.
   */
  inviteMember(args: {
    tenantId: TenantId;
    groupId: string;
    invitedBy: UserId;
    invitedEmail: string;
  }): Promise<StudyGroupInvite>;

  /**
   * Accept or decline an invite.
   */
  respondToInvite(args: {
    tenantId: TenantId;
    inviteId: string;
    userId: UserId;
    accepted: boolean;
  }): Promise<StudyGroupInvite>;

  /**
   * List members of a group.
   */
  listMembers(args: {
    tenantId: TenantId;
    groupId: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<StudyGroupMember>>;

  /**
   * Update member role.
   */
  updateMemberRole(args: {
    tenantId: TenantId;
    groupId: string;
    memberId: string;
    updatedBy: UserId;
    newRole: StudyGroupRole;
  }): Promise<StudyGroupMember>;

  /**
   * Remove a member from the group.
   */
  removeMember(args: {
    tenantId: TenantId;
    groupId: string;
    memberId: string;
    removedBy: UserId;
  }): Promise<void>;

  /**
   * Leave a study group.
   */
  leaveGroup(args: {
    tenantId: TenantId;
    groupId: string;
    userId: UserId;
  }): Promise<void>;

  // ---------------------------------------------------------------------------
  // Study Sessions
  // ---------------------------------------------------------------------------

  /**
   * Schedule a study session.
   */
  scheduleSession(args: {
    tenantId: TenantId;
    groupId: string;
    createdBy: UserId;
    title: string;
    description?: string;
    scheduledAt: Date;
    duration: number;
    type: StudySessionType;
    moduleId?: ModuleId;
    lessonIds?: string[];
    maxParticipants?: number;
  }): Promise<StudySession>;

  /**
   * List upcoming sessions for a group.
   */
  listSessions(args: {
    tenantId: TenantId;
    groupId: string;
    includeCompleted?: boolean;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<StudySession>>;

  /**
   * RSVP to a session.
   */
  rsvpSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    status: SessionRsvp['status'];
    note?: string;
  }): Promise<SessionRsvp>;

  /**
   * Start a session.
   */
  startSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
  }): Promise<StudySession>;

  /**
   * End a session and add notes.
   */
  endSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    notes?: string;
    recordingUrl?: string;
  }): Promise<StudySession>;

  /**
   * Cancel a session.
   */
  cancelSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    reason?: string;
  }): Promise<StudySession>;
}

/**
 * Service for discussion forum management.
 */
export interface ForumService {
  // ---------------------------------------------------------------------------
  // Forum Management
  // ---------------------------------------------------------------------------

  /**
   * Create a forum.
   */
  createForum(args: {
    tenantId: TenantId;
    name: string;
    description: string;
    scope: ForumScope;
    scopeId?: string;
    categories?: Array<{ name: string; description?: string; color: string }>;
    settings?: {
      allowAnonymousPosts?: boolean;
      requireModeration?: boolean;
      allowAttachments?: boolean;
      allowPolls?: boolean;
    };
  }): Promise<Forum>;

  /**
   * Get a forum.
   */
  getForum(args: {
    tenantId: TenantId;
    forumId: string;
  }): Promise<Forum>;

  /**
   * List forums.
   */
  listForums(args: {
    tenantId: TenantId;
    scope?: ForumScope;
    scopeId?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<Forum>>;

  // ---------------------------------------------------------------------------
  // Topics
  // ---------------------------------------------------------------------------

  /**
   * Create a topic.
   */
  createTopic(args: {
    tenantId: TenantId;
    forumId: string;
    authorId: UserId;
    title: string;
    content: string;
    contentFormat?: 'markdown' | 'html' | 'plain';
    categoryId?: string;
    attachments?: Array<{ name: string; type: string; url: string }>;
  }): Promise<ForumTopic>;

  /**
   * Get a topic with posts.
   */
  getTopic(args: {
    tenantId: TenantId;
    topicId: string;
    userId?: UserId; // For view tracking
  }): Promise<ForumTopic>;

  /**
   * List topics in a forum.
   */
  listTopics(args: {
    tenantId: TenantId;
    forumId: string;
    categoryId?: string;
    isPinned?: boolean;
    isAnswered?: boolean;
    searchQuery?: string;
    sortBy?: 'newest' | 'active' | 'popular';
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<ForumTopic>>;

  /**
   * Update a topic.
   */
  updateTopic(args: {
    tenantId: TenantId;
    topicId: string;
    userId: UserId;
    patch: Partial<Pick<ForumTopic, 'title' | 'content' | 'categoryId'>>;
  }): Promise<ForumTopic>;

  /**
   * Pin/unpin a topic.
   */
  togglePinTopic(args: {
    tenantId: TenantId;
    topicId: string;
    userId: UserId;
    pinned: boolean;
  }): Promise<ForumTopic>;

  /**
   * Lock/unlock a topic.
   */
  toggleLockTopic(args: {
    tenantId: TenantId;
    topicId: string;
    userId: UserId;
    locked: boolean;
  }): Promise<ForumTopic>;

  /**
   * Delete a topic.
   */
  deleteTopic(args: {
    tenantId: TenantId;
    topicId: string;
    userId: UserId;
  }): Promise<void>;

  // ---------------------------------------------------------------------------
  // Posts
  // ---------------------------------------------------------------------------

  /**
   * Create a post (reply).
   */
  createPost(args: {
    tenantId: TenantId;
    topicId: string;
    authorId: UserId;
    content: string;
    contentFormat?: 'markdown' | 'html' | 'plain';
    parentId?: string;
    isAnonymous?: boolean;
    attachments?: Array<{ name: string; type: string; url: string }>;
  }): Promise<ForumPost>;

  /**
   * List posts in a topic.
   */
  listPosts(args: {
    tenantId: TenantId;
    topicId: string;
    parentId?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<ForumPost>>;

  /**
   * Update a post.
   */
  updatePost(args: {
    tenantId: TenantId;
    postId: string;
    userId: UserId;
    content: string;
    editReason?: string;
  }): Promise<ForumPost>;

  /**
   * Delete a post.
   */
  deletePost(args: {
    tenantId: TenantId;
    postId: string;
    userId: UserId;
  }): Promise<void>;

  /**
   * Mark post as accepted answer.
   */
  markAsAnswer(args: {
    tenantId: TenantId;
    topicId: string;
    postId: string;
    userId: UserId;
  }): Promise<ForumPost>;

  /**
   * React to a post.
   */
  reactToPost(args: {
    tenantId: TenantId;
    postId: string;
    userId: UserId;
    reaction: ReactionType;
  }): Promise<ForumPost>;

  /**
   * Remove reaction from a post.
   */
  removeReaction(args: {
    tenantId: TenantId;
    postId: string;
    userId: UserId;
    reaction: ReactionType;
  }): Promise<ForumPost>;

  // ---------------------------------------------------------------------------
  // Moderation
  // ---------------------------------------------------------------------------

  /**
   * Report content for moderation.
   */
  reportContent(args: {
    tenantId: TenantId;
    contentType: 'topic' | 'post';
    contentId: string;
    reporterId: UserId;
    reason: string;
    details?: string;
  }): Promise<{ reportId: string }>;

  /**
   * Moderate content (hide/delete).
   */
  moderateContent(args: {
    tenantId: TenantId;
    contentType: 'topic' | 'post';
    contentId: string;
    moderatorId: UserId;
    action: 'approve' | 'hide' | 'delete';
    note?: string;
  }): Promise<void>;
}

/**
 * Service for peer tutoring.
 */
export interface PeerTutoringService {
  // ---------------------------------------------------------------------------
  // Tutor Profiles
  // ---------------------------------------------------------------------------

  /**
   * Create or update tutor profile.
   */
  upsertTutorProfile(args: {
    tenantId: TenantId;
    userId: UserId;
    displayName: string;
    bio: string;
    subjects: string[];
    moduleIds?: ModuleId[];
    sessionTypes: TutoringSession['type'][];
    timezone: string;
    maxSessionsPerWeek?: number;
  }): Promise<TutorProfile>;

  /**
   * Get tutor profile.
   */
  getTutorProfile(args: {
    tenantId: TenantId;
    userId: UserId;
  }): Promise<TutorProfile | null>;

  /**
   * Search for tutors.
   */
  searchTutors(args: {
    tenantId: TenantId;
    subject?: string;
    moduleId?: ModuleId;
    sessionType?: TutoringSession['type'];
    minRating?: number;
    searchQuery?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutorProfile>>;

  /**
   * Toggle tutor availability.
   */
  toggleAvailability(args: {
    tenantId: TenantId;
    userId: UserId;
    isAvailable: boolean;
  }): Promise<TutorProfile>;

  // ---------------------------------------------------------------------------
  // Tutoring Requests
  // ---------------------------------------------------------------------------

  /**
   * Create a tutoring request.
   */
  createRequest(args: {
    tenantId: TenantId;
    studentId: UserId;
    subject: string;
    moduleId?: ModuleId;
    lessonId?: string;
    title: string;
    description: string;
    preferredTypes: TutoringSession['type'][];
    preferredTime?: Date;
    estimatedDuration?: number;
    urgency?: 'low' | 'medium' | 'high';
  }): Promise<TutoringRequest>;

  /**
   * List open tutoring requests.
   */
  listOpenRequests(args: {
    tenantId: TenantId;
    subject?: string;
    moduleId?: ModuleId;
    tutorId?: UserId; // Requests tutor can help with
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutoringRequest>>;

  /**
   * Get user's tutoring requests (as student or tutor).
   */
  getMyRequests(args: {
    tenantId: TenantId;
    userId: UserId;
    role: 'student' | 'tutor';
    status?: TutoringRequest['status'];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutoringRequest>>;

  /**
   * Accept a tutoring request.
   */
  acceptRequest(args: {
    tenantId: TenantId;
    requestId: string;
    tutorId: UserId;
  }): Promise<TutoringRequest>;

  /**
   * Cancel a tutoring request.
   */
  cancelRequest(args: {
    tenantId: TenantId;
    requestId: string;
    userId: UserId;
    reason?: string;
  }): Promise<TutoringRequest>;

  // ---------------------------------------------------------------------------
  // Sessions
  // ---------------------------------------------------------------------------

  /**
   * Schedule a tutoring session.
   */
  scheduleSession(args: {
    tenantId: TenantId;
    requestId: string;
    tutorId: UserId;
    scheduledAt: Date;
    duration: number;
    type: TutoringSession['type'];
    meetingUrl?: string;
  }): Promise<TutoringSession>;

  /**
   * Get session details.
   */
  getSession(args: {
    tenantId: TenantId;
    sessionId: string;
  }): Promise<TutoringSession>;

  /**
   * List sessions.
   */
  listSessions(args: {
    tenantId: TenantId;
    userId: UserId;
    role?: 'student' | 'tutor';
    status?: TutoringSession['status'];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutoringSession>>;

  /**
   * Start a session.
   */
  startSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
  }): Promise<TutoringSession>;

  /**
   * End a session.
   */
  endSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    notes?: string;
  }): Promise<TutoringSession>;

  /**
   * Report a no-show.
   */
  reportNoShow(args: {
    tenantId: TenantId;
    sessionId: string;
    reporterId: UserId;
  }): Promise<TutoringSession>;

  // ---------------------------------------------------------------------------
  // Reviews
  // ---------------------------------------------------------------------------

  /**
   * Submit a review.
   */
  submitReview(args: {
    tenantId: TenantId;
    sessionId: string;
    reviewerId: UserId;
    rating: number;
    helpfulness: number;
    communication: number;
    knowledge: number;
    comment?: string;
  }): Promise<TutoringReview>;

  /**
   * Respond to a review (as tutor).
   */
  respondToReview(args: {
    tenantId: TenantId;
    reviewId: string;
    tutorId: UserId;
    response: string;
  }): Promise<TutoringReview>;

  /**
   * List reviews for a tutor.
   */
  listTutorReviews(args: {
    tenantId: TenantId;
    tutorId: UserId;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<TutoringReview>>;
}

/**
 * Service for real-time chat.
 */
export interface ChatService {
  /**
   * Get or create a chat room.
   */
  getOrCreateRoom(args: {
    tenantId: TenantId;
    type: ChatRoom['type'];
    participants: UserId[];
    studyGroupId?: string;
    tutoringSessionId?: string;
  }): Promise<ChatRoom>;

  /**
   * Get chat room by ID.
   */
  getRoom(args: {
    tenantId: TenantId;
    roomId: string;
    userId: UserId;
  }): Promise<ChatRoom>;

  /**
   * List user's chat rooms.
   */
  listRooms(args: {
    tenantId: TenantId;
    userId: UserId;
    type?: ChatRoom['type'];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<ChatRoom>>;

  /**
   * Send a message.
   */
  sendMessage(args: {
    tenantId: TenantId;
    roomId: string;
    senderId: UserId;
    type: ChatMessage['type'];
    content: string;
    metadata?: Record<string, unknown>;
    replyToId?: string;
    attachments?: Array<{ name: string; type: string; url: string }>;
  }): Promise<ChatMessage>;

  /**
   * Get messages in a room.
   */
  getMessages(args: {
    tenantId: TenantId;
    roomId: string;
    userId: UserId;
    before?: string; // messageId
    limit?: number;
  }): Promise<{ messages: ChatMessage[]; hasMore: boolean }>;

  /**
   * React to a message.
   */
  reactToMessage(args: {
    tenantId: TenantId;
    messageId: string;
    userId: UserId;
    emoji: string;
  }): Promise<ChatMessage>;

  /**
   * Delete a message.
   */
  deleteMessage(args: {
    tenantId: TenantId;
    messageId: string;
    userId: UserId;
  }): Promise<void>;

  /**
   * Mark messages as read.
   */
  markAsRead(args: {
    tenantId: TenantId;
    roomId: string;
    userId: UserId;
    lastReadMessageId: string;
  }): Promise<void>;
}

/**
 * Service for collaborative features.
 */
export interface CollaborationService {
  /**
   * Create a shared note.
   */
  createSharedNote(args: {
    tenantId: TenantId;
    createdBy: UserId;
    title: string;
    content: string;
    moduleId?: ModuleId;
    lessonId?: string;
    studyGroupId?: string;
    allowEditing?: boolean;
    allowComments?: boolean;
  }): Promise<SharedNote>;

  /**
   * Get a shared note.
   */
  getSharedNote(args: {
    tenantId: TenantId;
    noteId: string;
    userId: UserId;
  }): Promise<SharedNote>;

  /**
   * Update a shared note.
   */
  updateSharedNote(args: {
    tenantId: TenantId;
    noteId: string;
    userId: UserId;
    content: string;
  }): Promise<SharedNote>;

  /**
   * Share a note with users.
   */
  shareNote(args: {
    tenantId: TenantId;
    noteId: string;
    sharedBy: UserId;
    shareWith: Array<{ userId: UserId; permission: 'view' | 'comment' | 'edit' }>;
  }): Promise<SharedNote>;

  /**
   * List shared notes.
   */
  listSharedNotes(args: {
    tenantId: TenantId;
    userId: UserId;
    studyGroupId?: string;
    moduleId?: ModuleId;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<SharedNote>>;
}

/**
 * Service for social activity feed.
 */
export interface SocialActivityService {
  /**
   * Get activity feed.
   */
  getFeed(args: {
    tenantId: TenantId;
    userId: UserId;
    studyGroupId?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<SocialActivity>>;

  /**
   * Get notifications.
   */
  getNotifications(args: {
    tenantId: TenantId;
    userId: UserId;
    unreadOnly?: boolean;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<SocialNotification>>;

  /**
   * Mark notifications as read.
   */
  markNotificationsRead(args: {
    tenantId: TenantId;
    userId: UserId;
    notificationIds?: string[];
  }): Promise<{ markedCount: number }>;

  /**
   * Get unread notification count.
   */
  getUnreadCount(args: {
    tenantId: TenantId;
    userId: UserId;
  }): Promise<{ count: number }>;

  /**
   * Update notification preferences.
   */
  updateNotificationPreferences(args: {
    tenantId: TenantId;
    userId: UserId;
    preferences: {
      emailEnabled?: boolean;
      pushEnabled?: boolean;
      types?: Partial<Record<SocialNotification['type'], boolean>>;
    };
  }): Promise<void>;
}

// ============================================
// VR Labs Services
// ============================================

import type {
  VRLab,
  VRLabId,
  VRScene,
  VRSceneId,
  VRSession,
  VRSessionId,
  VRSessionStatus,
  VRAsset,
  VRAssetId,
  VRLabAnalytics,
  VRMultiplayerSession,
  VRInteractable,
  VRLabObjective,
  VRLabCategory,
  VRLabDifficulty,
  VRDeviceType,
  VRDeviceInfo,
  VRInteractionLog,
  VRPerformanceMetrics,
  CreateVRLabRequest,
  UpdateVRLabRequest,
  CreateVRSceneRequest,
  StartVRSessionRequest,
  UpdateVRSessionRequest,
  VRLabListParams,
} from './vr-labs';

/**
 * Service for managing VR Labs and scenes.
 */
export interface VRLabService {
  /**
   * Create a new VR lab.
   */
  createLab(args: {
    tenantId: TenantId;
    userId: UserId;
    data: CreateVRLabRequest;
  }): Promise<VRLab>;

  /**
   * Get a VR lab by ID.
   */
  getLabById(args: {
    tenantId: TenantId;
    labId: VRLabId;
  }): Promise<VRLab | null>;

  /**
   * List VR labs with filtering.
   */
  listLabs(args: {
    tenantId: TenantId;
    params: VRLabListParams;
  }): Promise<PaginatedResult<VRLab>>;

  /**
   * Update a VR lab.
   */
  updateLab(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
    data: UpdateVRLabRequest;
  }): Promise<VRLab>;

  /**
   * Delete a VR lab.
   */
  deleteLab(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
  }): Promise<void>;

  /**
   * Publish a VR lab.
   */
  publishLab(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
  }): Promise<VRLab>;

  /**
   * Add a scene to a VR lab.
   */
  addScene(args: {
    tenantId: TenantId;
    userId: UserId;
    data: CreateVRSceneRequest;
  }): Promise<VRScene>;

  /**
   * Update a scene.
   */
  updateScene(args: {
    tenantId: TenantId;
    sceneId: VRSceneId;
    userId: UserId;
    data: Partial<VRScene>;
  }): Promise<VRScene>;

  /**
   * Delete a scene.
   */
  deleteScene(args: {
    tenantId: TenantId;
    sceneId: VRSceneId;
    userId: UserId;
  }): Promise<void>;

  /**
   * Add an interactable to a scene.
   */
  addInteractable(args: {
    tenantId: TenantId;
    sceneId: VRSceneId;
    userId: UserId;
    data: Omit<VRInteractable, 'id' | 'sceneId'>;
  }): Promise<VRInteractable>;

  /**
   * Update an interactable.
   */
  updateInteractable(args: {
    tenantId: TenantId;
    interactableId: string;
    userId: UserId;
    data: Partial<VRInteractable>;
  }): Promise<VRInteractable>;

  /**
   * Delete an interactable.
   */
  deleteInteractable(args: {
    tenantId: TenantId;
    interactableId: string;
    userId: UserId;
  }): Promise<void>;

  /**
   * Add an objective to a lab.
   */
  addObjective(args: {
    tenantId: TenantId;
    labId: VRLabId;
    userId: UserId;
    data: Omit<VRLabObjective, 'id' | 'labId'>;
  }): Promise<VRLabObjective>;

  /**
   * Update an objective.
   */
  updateObjective(args: {
    tenantId: TenantId;
    objectiveId: string;
    userId: UserId;
    data: Partial<VRLabObjective>;
  }): Promise<VRLabObjective>;

  /**
   * Delete an objective.
   */
  deleteObjective(args: {
    tenantId: TenantId;
    objectiveId: string;
    userId: UserId;
  }): Promise<void>;
}

/**
 * Service for managing VR sessions.
 */
export interface VRSessionService {
  /**
   * Start a new VR session.
   */
  startSession(args: {
    tenantId: TenantId;
    userId: UserId;
    data: StartVRSessionRequest;
  }): Promise<VRSession>;

  /**
   * Get a session by ID.
   */
  getSession(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
  }): Promise<VRSession | null>;

  /**
   * Update session state.
   */
  updateSession(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    userId: UserId;
    data: UpdateVRSessionRequest;
  }): Promise<VRSession>;

  /**
   * End a VR session.
   */
  endSession(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    userId: UserId;
  }): Promise<VRSession>;

  /**
   * List user's VR sessions.
   */
  listUserSessions(args: {
    tenantId: TenantId;
    userId: UserId;
    labId?: VRLabId;
    status?: VRSessionStatus;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<VRSession>>;

  /**
   * Log an interaction during a session.
   */
  logInteraction(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    log: VRInteractionLog;
  }): Promise<void>;

  /**
   * Complete an objective in a session.
   */
  completeObjective(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    objectiveId: string;
    points: number;
  }): Promise<VRSession>;

  /**
   * Update performance metrics.
   */
  updatePerformanceMetrics(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
    metrics: Partial<VRPerformanceMetrics>;
  }): Promise<void>;

  /**
   * Get session summary after completion.
   */
  getSessionSummary(args: {
    tenantId: TenantId;
    sessionId: VRSessionId;
  }): Promise<{
    session: VRSession;
    totalPoints: number;
    maxPoints: number;
    objectivesCompleted: number;
    objectivesTotal: number;
    duration: number;
    rank?: string;
  }>;
}

/**
 * Service for managing VR assets.
 */
export interface VRAssetService {
  /**
   * Upload a VR asset.
   */
  uploadAsset(args: {
    tenantId: TenantId;
    userId: UserId;
    file: {
      name: string;
      type: string;
      size: number;
      data: Buffer | Uint8Array;
    };
    metadata: {
      tags?: string[];
      isPublic?: boolean;
    };
  }): Promise<VRAsset>;

  /**
   * Get an asset by ID.
   */
  getAsset(args: {
    tenantId: TenantId;
    assetId: VRAssetId;
  }): Promise<VRAsset | null>;

  /**
   * List assets.
   */
  listAssets(args: {
    tenantId: TenantId;
    type?: string;
    search?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<VRAsset>>;

  /**
   * Delete an asset.
   */
  deleteAsset(args: {
    tenantId: TenantId;
    assetId: VRAssetId;
    userId: UserId;
  }): Promise<void>;

  /**
   * Get a signed URL for downloading an asset.
   */
  getDownloadUrl(args: {
    tenantId: TenantId;
    assetId: VRAssetId;
  }): Promise<{ url: string; expiresAt: string }>;

  /**
   * Get a signed URL for uploading an asset.
   */
  getUploadUrl(args: {
    tenantId: TenantId;
    userId: UserId;
    fileName: string;
    contentType: string;
  }): Promise<{ url: string; assetId: VRAssetId; expiresAt: string }>;
}

/**
 * Service for VR multiplayer sessions.
 */
export interface VRMultiplayerService {
  /**
   * Create a multiplayer session.
   */
  createSession(args: {
    tenantId: TenantId;
    hostUserId: UserId;
    labId: VRLabId;
    settings: {
      maxParticipants: number;
      voiceChatEnabled: boolean;
      spatialAudioEnabled: boolean;
    };
  }): Promise<VRMultiplayerSession>;

  /**
   * Join a multiplayer session.
   */
  joinSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    displayName: string;
    avatarUrl?: string;
  }): Promise<VRMultiplayerSession>;

  /**
   * Leave a multiplayer session.
   */
  leaveSession(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
  }): Promise<void>;

  /**
   * Update participant state.
   */
  updateParticipant(args: {
    tenantId: TenantId;
    sessionId: string;
    userId: UserId;
    state: {
      position?: { x: number; y: number; z: number };
      rotation?: { x: number; y: number; z: number; w: number };
      isMuted?: boolean;
      isReady?: boolean;
    };
  }): Promise<void>;

  /**
   * Start a multiplayer session (host only).
   */
  startSession(args: {
    tenantId: TenantId;
    sessionId: string;
    hostUserId: UserId;
  }): Promise<VRMultiplayerSession>;

  /**
   * End a multiplayer session.
   */
  endSession(args: {
    tenantId: TenantId;
    sessionId: string;
    hostUserId: UserId;
  }): Promise<void>;

  /**
   * Get session details.
   */
  getSession(args: {
    tenantId: TenantId;
    sessionId: string;
  }): Promise<VRMultiplayerSession | null>;

  /**
   * List available multiplayer sessions.
   */
  listSessions(args: {
    tenantId: TenantId;
    labId?: VRLabId;
    status?: 'lobby' | 'active';
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<VRMultiplayerSession>>;
}

/**
 * Service for VR analytics.
 */
export interface VRAnalyticsService {
  /**
   * Get analytics for a specific lab.
   */
  getLabAnalytics(args: {
    tenantId: TenantId;
    labId: VRLabId;
    period: 'day' | 'week' | 'month' | 'all';
  }): Promise<VRLabAnalytics>;

  /**
   * Get user's VR learning progress.
   */
  getUserProgress(args: {
    tenantId: TenantId;
    userId: UserId;
  }): Promise<{
    totalLabsCompleted: number;
    totalTimeSpent: number;
    averageScore: number;
    labProgress: Array<{
      labId: VRLabId;
      labTitle: string;
      completionRate: number;
      lastSessionAt: string;
      bestScore: number;
    }>;
  }>;

  /**
   * Get aggregated VR analytics for admin dashboard.
   */
  getAggregatedAnalytics(args: {
    tenantId: TenantId;
    period: 'day' | 'week' | 'month';
  }): Promise<{
    totalSessions: number;
    uniqueUsers: number;
    averageSessionDuration: number;
    popularLabs: Array<{ labId: VRLabId; title: string; sessions: number }>;
    deviceUsage: Record<VRDeviceType, number>;
    completionTrend: Array<{ date: string; completions: number }>;
  }>;

  /**
   * Track a VR event.
   */
  trackEvent(args: {
    tenantId: TenantId;
    userId: UserId;
    event: {
      type: string;
      labId?: VRLabId;
      sessionId?: VRSessionId;
      metadata?: Record<string, unknown>;
    };
  }): Promise<void>;
}

// =============================================================================
// Subscription & Payment Services (Block 4 - Days 48-49)
// =============================================================================

import type {
  SubscriptionId,
  PaymentMethodId,
  InvoiceId,
  TransactionId,
  SubscriptionPlan,
  Subscription,
  SubscriptionStatus,
  SubscriptionTier,
  BillingInterval,
  PaymentMethod,
  PaymentMethodType,
  CardDetails,
  BillingAddress,
  Invoice,
  PaymentTransaction,
  SubscriptionUsage,
  SubscriptionChangePreview,
  PaymentWebhookEvent,
} from "./types";

/**
 * @doc.type interface
 * @doc.purpose Manage subscription plans and billing
 * @doc.layer product
 * @doc.pattern Service
 */
export interface SubscriptionService {
  // Plan management
  /**
   * List available subscription plans.
   */
  listPlans(args: {
    tenantId: TenantId;
    includeInactive?: boolean;
  }): Promise<SubscriptionPlan[]>;

  /**
   * Get a specific plan by ID.
   */
  getPlan(args: {
    planId: string;
  }): Promise<SubscriptionPlan | null>;

  // Subscription lifecycle
  /**
   * Get tenant's current subscription.
   */
  getCurrentSubscription(args: {
    tenantId: TenantId;
  }): Promise<Subscription | null>;

  /**
   * Create a new subscription.
   */
  createSubscription(args: {
    tenantId: TenantId;
    planId: string;
    billingInterval: BillingInterval;
    paymentMethodId?: PaymentMethodId;
    trialDays?: number;
  }): Promise<Subscription>;

  /**
   * Preview subscription change.
   */
  previewChange(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    newPlanId: string;
    newBillingInterval?: BillingInterval;
  }): Promise<SubscriptionChangePreview>;

  /**
   * Change subscription plan.
   */
  changePlan(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    newPlanId: string;
    newBillingInterval?: BillingInterval;
    prorationBehavior?: 'create_prorations' | 'none' | 'always_invoice';
  }): Promise<Subscription>;

  /**
   * Cancel subscription.
   */
  cancelSubscription(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    cancelImmediately?: boolean;
    reason?: string;
  }): Promise<Subscription>;

  /**
   * Resume a canceled subscription.
   */
  resumeSubscription(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
  }): Promise<Subscription>;

  /**
   * Pause subscription.
   */
  pauseSubscription(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    resumeAt?: string;
  }): Promise<Subscription>;

  // Usage tracking
  /**
   * Get current usage against plan limits.
   */
  getUsage(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
  }): Promise<SubscriptionUsage>;

  /**
   * Check if tenant can perform action based on limits.
   */
  checkLimit(args: {
    tenantId: TenantId;
    resource: 'users' | 'modules' | 'classrooms' | 'storage' | 'vrSessions';
    increment?: number;
  }): Promise<{ allowed: boolean; current: number; limit: number; message?: string }>;
}

/**
 * @doc.type interface
 * @doc.purpose Manage payment methods and transactions
 * @doc.layer product
 * @doc.pattern Service
 */
export interface PaymentMethodService {
  /**
   * List tenant's payment methods.
   */
  listPaymentMethods(args: {
    tenantId: TenantId;
  }): Promise<PaymentMethod[]>;

  /**
   * Get a specific payment method.
   */
  getPaymentMethod(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
  }): Promise<PaymentMethod | null>;

  /**
   * Create setup intent for adding new payment method.
   */
  createSetupIntent(args: {
    tenantId: TenantId;
    paymentMethodType: PaymentMethodType;
  }): Promise<{ clientSecret: string; setupIntentId: string }>;

  /**
   * Attach payment method after setup.
   */
  attachPaymentMethod(args: {
    tenantId: TenantId;
    setupIntentId: string;
    billingAddress?: BillingAddress;
    setAsDefault?: boolean;
  }): Promise<PaymentMethod>;

  /**
   * Set default payment method.
   */
  setDefaultPaymentMethod(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
  }): Promise<void>;

  /**
   * Remove payment method.
   */
  removePaymentMethod(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
  }): Promise<void>;

  /**
   * Update billing address.
   */
  updateBillingAddress(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
    billingAddress: BillingAddress;
  }): Promise<PaymentMethod>;
}

/**
 * @doc.type interface
 * @doc.purpose Manage invoices and billing history
 * @doc.layer product
 * @doc.pattern Service
 */
export interface InvoiceService {
  /**
   * List tenant's invoices.
   */
  listInvoices(args: {
    tenantId: TenantId;
    status?: Invoice['status'];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<Invoice>>;

  /**
   * Get a specific invoice.
   */
  getInvoice(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
  }): Promise<Invoice | null>;

  /**
   * Get upcoming invoice preview.
   */
  getUpcomingInvoice(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
  }): Promise<Invoice | null>;

  /**
   * Pay an open invoice.
   */
  payInvoice(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
    paymentMethodId?: PaymentMethodId;
  }): Promise<Invoice>;

  /**
   * Download invoice PDF.
   */
  getInvoicePdf(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
  }): Promise<{ url: string; expiresAt: string }>;

  /**
   * Send invoice by email.
   */
  sendInvoiceEmail(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
    email: string;
  }): Promise<void>;
}

/**
 * @doc.type interface
 * @doc.purpose Process payment transactions
 * @doc.layer product
 * @doc.pattern Service
 */
export interface PaymentTransactionService {
  /**
   * List payment transactions.
   */
  listTransactions(args: {
    tenantId: TenantId;
    type?: PaymentTransaction['type'];
    status?: PaymentTransaction['status'];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<PaymentTransaction>>;

  /**
   * Get a specific transaction.
   */
  getTransaction(args: {
    tenantId: TenantId;
    transactionId: TransactionId;
  }): Promise<PaymentTransaction | null>;

  /**
   * Process a refund.
   */
  createRefund(args: {
    tenantId: TenantId;
    transactionId: TransactionId;
    amountCents?: number;
    reason?: string;
  }): Promise<PaymentTransaction>;

  /**
   * Get transaction receipt.
   */
  getReceipt(args: {
    tenantId: TenantId;
    transactionId: TransactionId;
  }): Promise<{ url: string }>;
}

/**
 * @doc.type interface
 * @doc.purpose Handle payment webhook events
 * @doc.layer product
 * @doc.pattern Service
 */
export interface PaymentWebhookService {
  /**
   * Verify and process incoming webhook.
   */
  processWebhook(args: {
    payload: string;
    signature: string;
    provider: 'stripe';
  }): Promise<{ processed: boolean; eventType: string; error?: string }>;

  /**
   * List webhook events for debugging.
   */
  listWebhookEvents(args: {
    tenantId?: TenantId;
    eventType?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<PaymentWebhookEvent>>;

  /**
   * Retry a failed webhook.
   */
  retryWebhook(args: {
    eventId: string;
  }): Promise<{ success: boolean; error?: string }>;
}

// =============================================================================
// LTI 1.3 Integration Services (Block 4 - Day 50)
// =============================================================================

import type {
  LtiPlatformId,
  LtiContextId,
  LtiResourceLinkId,
  LtiPlatform,
  LtiLaunchContext,
  LtiUserClaims,
  LtiDeepLinkingRequest,
  LtiContentItem,
  LtiScore,
  LtiMember,
  LtiSession,
  LtiGradePassback,
  LtiGradePassbackResult,
  LtiLineItem,
} from "./types";

/**
 * @doc.type interface
 * @doc.purpose Manage LTI platform registrations
 * @doc.layer product
 * @doc.pattern Service
 */
export interface LtiPlatformService {
  /**
   * List registered LTI platforms.
   */
  listPlatforms(args: {
    tenantId: TenantId;
    isActive?: boolean;
  }): Promise<LtiPlatform[]>;

  /**
   * Get a specific platform.
   */
  getPlatform(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
  }): Promise<LtiPlatform | null>;

  /**
   * Register a new LTI platform.
   */
  registerPlatform(args: {
    tenantId: TenantId;
    name: string;
    issuer: string;
    clientId: string;
    deploymentId: string;
    authLoginUrl: string;
    authTokenUrl: string;
    jwksUrl: string;
    publicKeyPem?: string;
  }): Promise<LtiPlatform>;

  /**
   * Update platform configuration.
   */
  updatePlatform(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    updates: Partial<Omit<LtiPlatform, 'id' | 'tenantId' | 'createdAt' | 'updatedAt'>>;
  }): Promise<LtiPlatform>;

  /**
   * Deactivate a platform.
   */
  deactivatePlatform(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
  }): Promise<void>;

  /**
   * Get tool configuration for platform setup.
   */
  getToolConfiguration(args: {
    tenantId: TenantId;
  }): Promise<{
    issuer: string;
    clientId: string;
    publicJwks: object;
    authLoginUrl: string;
    launchUrl: string;
    deepLinkingUrl: string;
    jwksUrl: string;
  }>;
}

/**
 * @doc.type interface
 * @doc.purpose Handle LTI launch and authentication
 * @doc.layer product
 * @doc.pattern Service
 */
export interface LtiLaunchService {
  /**
   * Initiate OIDC login flow.
   */
  initiateLogin(args: {
    tenantId: TenantId;
    issuer: string;
    loginHint: string;
    ltiMessageHint?: string;
    targetLinkUri: string;
    clientId: string;
  }): Promise<{ redirectUrl: string; state: string; nonce: string }>;

  /**
   * Validate and process launch.
   */
  validateLaunch(args: {
    tenantId: TenantId;
    idToken: string;
    state: string;
  }): Promise<{
    valid: boolean;
    session?: LtiSession;
    userClaims?: LtiUserClaims;
    launchContext?: LtiLaunchContext;
    error?: string;
  }>;

  /**
   * Get or create user from LTI claims.
   */
  resolveUser(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    userClaims: LtiUserClaims;
  }): Promise<{ userId: UserId; isNewUser: boolean }>;

  /**
   * Get active LTI session.
   */
  getSession(args: {
    sessionId: string;
  }): Promise<LtiSession | null>;

  /**
   * Extend session.
   */
  extendSession(args: {
    sessionId: string;
    additionalMinutes: number;
  }): Promise<LtiSession>;
}

/**
 * @doc.type interface
 * @doc.purpose Handle LTI Deep Linking content selection
 * @doc.layer product
 * @doc.pattern Service
 */
export interface LtiDeepLinkingService {
  /**
   * Parse deep linking request.
   */
  parseDeepLinkingRequest(args: {
    tenantId: TenantId;
    idToken: string;
  }): Promise<LtiDeepLinkingRequest>;

  /**
   * List available content for deep linking.
   */
  listAvailableContent(args: {
    tenantId: TenantId;
    contentTypes: string[];
    search?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<{
    moduleId: ModuleId;
    title: string;
    description: string;
    thumbnailUrl?: string;
    domain: string;
    difficulty: string;
  }>>;

  /**
   * Build content item from module.
   */
  buildContentItem(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    includeLineItem?: boolean;
    customParameters?: Record<string, string>;
  }): Promise<LtiContentItem>;

  /**
   * Create deep linking response JWT.
   */
  createDeepLinkingResponse(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    deepLinkingSettingsData: string;
    contentItems: LtiContentItem[];
  }): Promise<{ jwt: string; redirectUrl: string }>;
}

/**
 * @doc.type interface
 * @doc.purpose Handle LTI Assignment and Grade Services
 * @doc.layer product
 * @doc.pattern Service
 */
export interface LtiGradeService {
  /**
   * List line items for a context.
   */
  listLineItems(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
  }): Promise<LtiLineItem[]>;

  /**
   * Create a line item.
   */
  createLineItem(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    lineItem: Omit<LtiLineItem, 'id'>;
  }): Promise<LtiLineItem & { id: string }>;

  /**
   * Submit a score.
   */
  submitScore(args: {
    tenantId: TenantId;
    sessionId: string;
    lineItemId: string;
    score: LtiScore;
  }): Promise<LtiGradePassbackResult>;

  /**
   * Submit multiple scores.
   */
  submitScores(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    lineItemId: string;
    scores: LtiScore[];
  }): Promise<LtiGradePassbackResult[]>;

  /**
   * Get scores for a line item.
   */
  getScores(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    lineItemId: string;
    userId?: string;
  }): Promise<LtiScore[]>;

  /**
   * Sync grades from TutorPutor to LMS.
   */
  syncGrades(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    contextId: LtiContextId;
  }): Promise<{ synced: number; failed: number; errors: string[] }>;
}

/**
 * @doc.type interface
 * @doc.purpose Handle LTI Names and Role Provisioning Services
 * @doc.layer product
 * @doc.pattern Service
 */
export interface LtiRosterService {
  /**
   * Fetch course members from LMS.
   */
  fetchMembers(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    role?: string;
  }): Promise<LtiMember[]>;

  /**
   * Sync members to TutorPutor users.
   */
  syncMembers(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    classroomId?: ClassroomId;
    createMissing?: boolean;
  }): Promise<{
    synced: number;
    created: number;
    updated: number;
    errors: string[];
  }>;

  /**
   * Get member by LTI user ID.
   */
  getMember(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    ltiUserId: string;
  }): Promise<LtiMember | null>;
}


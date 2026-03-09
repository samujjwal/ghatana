/**
 * Centralized Type Definitions
 * 
 * Provides comprehensive type safety for the entire Tutorputor platform.
 * Replaces all 'any' types with properly defined interfaces.
 */

// ============================================================================
// Core Entity Types
// ============================================================================

export interface BaseEntity {
  id: string;
  tenantId: string;
  createdAt: Date;
  updatedAt: Date;
  version: number;
}

export interface User extends BaseEntity {
  email: string;
  firstName?: string;
  lastName?: string;
  isActive: boolean;
  lastLoginAt?: Date;
  roles: UserRole[];
  permissions: Permission[];
}

export interface UserRole {
  id: string;
  userId: string;
  roleId: string;
  assignedAt: Date;
  assignedBy: string;
  role: Role;
}

export interface Role {
  id: string;
  name: string;
  description: string;
  permissions: Permission[];
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export interface Permission {
  id: string;
  resource: string;
  action: string;
  conditions?: Record<string, unknown>;
  description?: string;
}

// ============================================================================
// Module and Content Types
// ============================================================================

export interface Module extends BaseEntity {
  title: string;
  slug: string;
  description: string;
  domain: ModuleDomain;
  difficulty: ModuleDifficulty;
  status: ModuleStatus;
  estimatedTimeMinutes: number;
  prerequisites?: string[];
  learningObjectives?: string[];
  tags?: string[];
  instructorId?: string;
  publishedAt?: Date;
  content?: ModuleContent[];
}

export enum ModuleDomain {
  MATHEMATICS = 'MATHEMATICS',
  SCIENCE = 'SCIENCE',
  LANGUAGE_ARTS = 'LANGUAGE_ARTS',
  SOCIAL_STUDIES = 'SOCIAL_STUDIES',
  COMPUTER_SCIENCE = 'COMPUTER_SCIENCE',
  ENGINEERING = 'ENGINEERING',
  ARTS = 'ARTS',
  PHYSICAL_EDUCATION = 'PHYSICAL_EDUCATION'
}

export enum ModuleDifficulty {
  BEGINNER = 'BEGINNER',
  INTERMEDIATE = 'INTERMEDIATE',
  ADVANCED = 'ADVANCED',
  EXPERT = 'EXPERT'
}

export enum ModuleStatus {
  DRAFT = 'DRAFT',
  REVIEW = 'REVIEW',
  PUBLISHED = 'PUBLISHED',
  ARCHIVED = 'ARCHIVED',
  DEPRECATED = 'DEPRECATED'
}

export interface ModuleContent {
  id: string;
  moduleId: string;
  type: ContentType;
  title: string;
  order: number;
  content: ContentData;
  metadata?: Record<string, unknown>;
  createdAt: Date;
  updatedAt: Date;
}

export enum ContentType {
  TEXT = 'TEXT',
  VIDEO = 'VIDEO',
  AUDIO = 'AUDIO',
  SIMULATION = 'SIMULATION',
  ASSESSMENT = 'ASSESSMENT',
  INTERACTIVE = 'INTERACTIVE',
  DOCUMENT = 'DOCUMENT',
  EXTERNAL_LINK = 'EXTERNAL_LINK'
}

export type ContentData = 
  | TextContent
  | VideoContent
  | AudioContent
  | SimulationContent
  | AssessmentContent
  | InteractiveContent
  | DocumentContent
  | ExternalLinkContent;

export interface TextContent {
  type: ContentType.TEXT;
  body: string;
  format: 'markdown' | 'html' | 'plain';
  wordCount?: number;
}

export interface VideoContent {
  type: ContentType.VIDEO;
  url: string;
  duration: number;
  format: string;
  resolution?: string;
  subtitles?: VideoSubtitle[];
  thumbnail?: string;
}

export interface VideoSubtitle {
  language: string;
  url: string;
  format: 'vtt' | 'srt';
}

export interface AudioContent {
  type: ContentType.AUDIO;
  url: string;
  duration: number;
  format: string;
  bitrate?: number;
  transcript?: string;
}

export interface SimulationContent {
  type: ContentType.SIMULATION;
  simulationId: string;
  parameters?: Record<string, unknown>;
  configuration?: SimulationConfig;
}

export interface SimulationConfig {
  mode: 'guided' | 'exploratory' | 'assessment';
  timeLimit?: number;
  maxAttempts?: number;
  scoringEnabled?: boolean;
}

export interface AssessmentContent {
  type: ContentType.ASSESSMENT;
  assessmentId: string;
  configuration?: AssessmentConfig;
}

export interface AssessmentConfig {
  timeLimit?: number;
  maxAttempts?: number;
  showResults?: boolean;
  allowReview?: boolean;
  randomizeQuestions?: boolean;
}

export interface InteractiveContent {
  type: ContentType.INTERACTIVE;
  component: string;
  props: Record<string, unknown>;
  configuration?: Record<string, unknown>;
}

export interface DocumentContent {
  type: ContentType.DOCUMENT;
  url: string;
  format: 'pdf' | 'docx' | 'pptx' | 'xlsx';
  size: number;
  pages?: number;
}

export interface ExternalLinkContent {
  type: ContentType.EXTERNAL_LINK;
  url: string;
  title?: string;
  description?: string;
  openInNewTab?: boolean;
}

// ============================================================================
// Assessment Types
// ============================================================================

export interface Assessment extends BaseEntity {
  title: string;
  description: string;
  type: AssessmentType;
  status: AssessmentStatus;
  moduleId?: string;
  configuration: AssessmentConfig;
  questions: Question[];
  rubric?: Rubric;
  passingScore?: number;
  maxAttempts?: number;
  timeLimit?: number;
}

export enum AssessmentType {
  FORMATIVE = 'FORMATIVE',
  SUMMATIVE = 'SUMMATIVE',
  DIAGNOSTIC = 'DIAGNOSTIC',
  PRACTICE = 'PRACTICE',
  CERTIFICATION = 'CERTIFICATION'
}

export enum AssessmentStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  ARCHIVED = 'ARCHIVED'
}

export interface Question {
  id: string;
  assessmentId: string;
  type: QuestionType;
  text: string;
  order: number;
  points: number;
  options?: QuestionOption[];
  correctAnswer?: string | string[];
  explanation?: string;
  metadata?: Record<string, unknown>;
}

export enum QuestionType {
  MULTIPLE_CHOICE = 'MULTIPLE_CHOICE',
  TRUE_FALSE = 'TRUE_FALSE',
  SHORT_ANSWER = 'SHORT_ANSWER',
  ESSAY = 'ESSAY',
  FILL_IN_BLANK = 'FILL_IN_BLANK',
  MATCHING = 'MATCHING',
  DRAG_AND_DROP = 'DRAG_AND_DROP',
  SIMULATION = 'SIMULATION'
}

export interface QuestionOption {
  id: string;
  text: string;
  isCorrect: boolean;
  explanation?: string;
  order?: number;
}

export interface Rubric {
  id: string;
  assessmentId: string;
  criteria: RubricCriterion[];
  maxScore: number;
  description?: string;
}

export interface RubricCriterion {
  id: string;
  name: string;
  description: string;
  weight: number;
  levels: RubricLevel[];
}

export interface RubricLevel {
  score: number;
  description: string;
  example?: string;
}

// ============================================================================
// Enrollment and Progress Types
// ============================================================================

export interface Enrollment extends BaseEntity {
  userId: string;
  moduleId: string;
  status: EnrollmentStatus;
  enrolledAt: Date;
  completedAt?: Date;
  progress: number;
  lastAccessAt?: Date;
  timeSpentMinutes: number;
  attempts: number;
  certificate?: Certificate;
}

export enum EnrollmentStatus {
  ENROLLED = 'ENROLLED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  DROPPED = 'DROPPED',
  SUSPENDED = 'SUSPENDED'
}

export interface Progress {
  id: string;
  enrollmentId: string;
  contentId: string;
  status: ProgressStatus;
  startedAt?: Date;
  completedAt?: Date;
  timeSpentMinutes: number;
  score?: number;
  attempts: number;
  data?: Record<string, unknown>;
}

export enum ProgressStatus {
  NOT_STARTED = 'NOT_STARTED',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  SKIPPED = 'SKIPPED'
}

export interface Certificate {
  id: string;
  enrollmentId: string;
  issuedAt: Date;
  expiresAt?: Date;
  certificateUrl: string;
  verificationCode: string;
  metadata?: Record<string, unknown>;
}

// ============================================================================
// Simulation Types
// ============================================================================

export interface Simulation extends Omit<BaseEntity, 'version'> {
  title: string;
  description: string;
  type: SimulationType;
  domain: string;
  configuration: SimulationConfig;
  parameters: SimulationParameter[];
  assets: SimulationAsset[];
  version: string;
}

export enum SimulationType {
  PHYSICS = 'PHYSICS',
  CHEMISTRY = 'CHEMISTRY',
  BIOLOGY = 'BIOLOGY',
  MATHEMATICS = 'MATHEMATICS',
  ENGINEERING = 'ENGINEERING',
  BUSINESS = 'BUSINESS',
  MEDICAL = 'MEDICAL'
}

export interface SimulationParameter {
  id: string;
  name: string;
  type: ParameterType;
  defaultValue: unknown;
  range?: ParameterRange;
  description?: string;
  required?: boolean;
}

export enum ParameterType {
  NUMBER = 'NUMBER',
  STRING = 'STRING',
  BOOLEAN = 'BOOLEAN',
  ARRAY = 'ARRAY',
  OBJECT = 'OBJECT'
}

export interface ParameterRange {
  min?: number;
  max?: number;
  step?: number;
  options?: string[];
}

export interface SimulationAsset {
  id: string;
  type: AssetType;
  url: string;
  size: number;
  format: string;
  metadata?: Record<string, unknown>;
}

export enum AssetType {
  MODEL = 'MODEL',
  TEXTURE = 'TEXTURE',
  AUDIO = 'AUDIO',
  VIDEO = 'VIDEO',
  SCRIPT = 'SCRIPT',
  CONFIGURATION = 'CONFIGURATION'
}

export interface SimulationRun {
  id: string;
  simulationId: string;
  userId: string;
  parameters: Record<string, unknown>;
  status: RunStatus;
  startedAt: Date;
  completedAt?: Date;
  results?: SimulationResults;
  logs?: SimulationLog[];
}

export enum RunStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

export interface SimulationResults {
  score?: number;
  data: Record<string, unknown>;
  metrics: Record<string, number>;
  summary?: string;
}

export interface SimulationLog {
  timestamp: Date;
  level: LogLevel;
  message: string;
  data?: Record<string, unknown>;
}

export enum LogLevel {
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR'
}

// ============================================================================
// API and Response Types
// ============================================================================

export interface ApiResponse<T = unknown> {
  data: T;
  success: boolean;
  message?: string;
  errors?: ApiError[];
  metadata?: ResponseMetadata;
}

export interface ResponseMetadata {
  requestId: string;
  timestamp: Date;
  duration: number;
  version: string;
  pagination?: PaginationInfo;
}

export interface PaginationInfo {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrev: boolean;
}

export interface ApiError {
  code: string;
  message: string;
  field?: string;
  details?: Record<string, unknown>;
}

export interface ListResponse<T> extends ApiResponse<T[]> {
  pagination: PaginationInfo;
}

export interface CreateRequest<T> {
  data: T;
  metadata?: Record<string, unknown>;
}

export interface UpdateRequest<T> {
  data: Partial<T>;
  metadata?: Record<string, unknown>;
}

export interface DeleteRequest {
  id: string;
  metadata?: Record<string, unknown>;
}

export interface SearchRequest {
  query: string;
  filters?: Record<string, unknown>;
  sort?: SortOption[];
  pagination?: PaginationRequest;
}

export interface SortOption {
  field: string;
  direction: 'asc' | 'desc';
}

export interface PaginationRequest {
  page?: number;
  limit?: number;
}

// ============================================================================
// Configuration and Settings Types
// ============================================================================

export interface SystemConfig {
  features: FeatureFlags;
  limits: SystemLimits;
  security: SecurityConfig;
  integrations: IntegrationConfig;
}

export interface FeatureFlags {
  contentWorkerEnabled: boolean;
  aiProxyEnabled: boolean;
  simulationEnabled: boolean;
  analyticsEnabled: boolean;
  notificationsEnabled: boolean;
  multiTenantEnabled: boolean;
}

export interface SystemLimits {
  maxUsersPerTenant: number;
  maxModulesPerTenant: number;
  maxFileSize: number;
  maxSimultaneousSimulations: number;
  sessionTimeout: number;
  rateLimitRequests: number;
  rateLimitWindow: number;
}

export interface SecurityConfig {
  jwtSecret: string;
  sessionSecret: string;
  passwordPolicy: PasswordPolicy;
  corsOrigins: string[];
  allowedHosts: string[];
  encryptionEnabled: boolean;
}

export interface PasswordPolicy {
  minLength: number;
  requireUppercase: boolean;
  requireLowercase: boolean;
  requireNumbers: boolean;
  requireSpecialChars: boolean;
  preventReuse: number;
}

export interface IntegrationConfig {
  aiService: AIServiceConfig;
  storageService: StorageServiceConfig;
  notificationService: NotificationServiceConfig;
  analyticsService: AnalyticsServiceConfig;
}

export interface AIServiceConfig {
  url: string;
  apiKey?: string;
  provider: 'openai' | 'ollama' | 'custom';
  model?: string;
  timeout: number;
  retries: number;
}

export interface StorageServiceConfig {
  provider: 's3' | 'gcs' | 'azure' | 'local';
  endpoint?: string;
  bucket: string;
  accessKey?: string;
  secretKey?: string;
  region?: string;
}

export interface NotificationServiceConfig {
  provider: 'email' | 'sms' | 'push' | 'webhook';
  settings: Record<string, unknown>;
}

export interface AnalyticsServiceConfig {
  provider: 'google-analytics' | 'mixpanel' | 'custom';
  trackingId?: string;
  endpoint?: string;
}

// ============================================================================
// Utility Types
// ============================================================================

export type DeepPartial<T> = {
  [P in keyof T]?: T[P] extends object ? DeepPartial<T[P]> : T[P];
};

export type RequiredFields<T, K extends keyof T> = T & Required<Pick<T, K>>;

export type OptionalFields<T, K extends keyof T> = Omit<T, K> & Partial<Pick<T, K>>;

export type Id = string;

export type Timestamp = Date;

export type JsonObject = Record<string, unknown>;

export type JsonArray = unknown[];

export type JsonValue = string | number | boolean | null | JsonObject | JsonArray;

// ============================================================================
// Event Types
// ============================================================================

export interface SystemEvent {
  id: string;
  type: string;
  source: string;
  data: JsonObject;
  timestamp: Date;
  userId?: string;
  tenantId?: string;
  correlationId?: string;
}

export interface UserEvent extends SystemEvent {
  type: 'user.created' | 'user.updated' | 'user.deleted' | 'user.login' | 'user.logout';
  userId: string;
}

export interface ModuleEvent extends SystemEvent {
  type: 'module.created' | 'module.updated' | 'module.deleted' | 'module.published';
  moduleId: string;
}

export interface AssessmentEvent extends SystemEvent {
  type: 'assessment.started' | 'assessment.completed' | 'assessment.submitted';
  assessmentId: string;
  userId: string;
}

export interface SimulationEvent extends SystemEvent {
  type: 'simulation.started' | 'simulation.completed' | 'simulation.failed';
  simulationId: string;
  userId: string;
}

// ============================================================================
// Validation Types
// ============================================================================

export interface ValidationResult {
  isValid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

export interface ValidationError {
  field: string;
  code: string;
  message: string;
  value?: unknown;
}

export interface ValidationWarning {
  field: string;
  code: string;
  message: string;
  value?: unknown;
}

export interface ValidationRule<T = unknown> {
  name: string;
  validator: (value: T) => ValidationResult;
  required?: boolean;
}

// ============================================================================
// Export all types for easy importing
// ============================================================================

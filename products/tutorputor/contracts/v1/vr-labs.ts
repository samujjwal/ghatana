/**
 * @doc.type contracts
 * @doc.purpose VR Labs types for immersive learning experiences
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */

// ============================================
// VR Lab Types
// ============================================

export type VRLabId = string;
export type VRSceneId = string;
export type VRAssetId = string;
export type VRSessionId = string;

/**
 * VR Lab categories for different subjects
 */
export type VRLabCategory =
  | 'chemistry'
  | 'physics'
  | 'biology'
  | 'astronomy'
  | 'engineering'
  | 'anatomy'
  | 'geology'
  | 'history'
  | 'art'
  | 'mathematics';

/**
 * VR Lab difficulty levels
 */
export type VRLabDifficulty = 'beginner' | 'intermediate' | 'advanced' | 'expert';

/**
 * VR interaction types
 */
export type VRInteractionType =
  | 'grab'
  | 'point'
  | 'teleport'
  | 'rotate'
  | 'scale'
  | 'trigger'
  | 'voice'
  | 'gesture';

/**
 * VR session status
 */
export type VRSessionStatus =
  | 'initializing'
  | 'loading'
  | 'active'
  | 'paused'
  | 'completed'
  | 'failed';

/**
 * VR device types
 */
export type VRDeviceType =
  | 'quest_2'
  | 'quest_3'
  | 'quest_pro'
  | 'vive'
  | 'index'
  | 'pico'
  | 'desktop'
  | 'mobile';

// ============================================
// VR Lab Definition
// ============================================

/**
 * VR Lab represents a complete virtual reality learning environment
 */
export interface VRLab {
  id: VRLabId;
  slug: string;
  title: string;
  description: string;
  category: VRLabCategory;
  difficulty: VRLabDifficulty;
  
  // Lab content
  scenes: VRScene[];
  objectives: VRLabObjective[];
  
  // Metadata
  thumbnailUrl: string;
  previewVideoUrl?: string;
  estimatedDuration: number; // minutes
  
  // Requirements
  requiredDevices: VRDeviceType[];
  minRequirements: VRRequirements;
  
  // Stats
  completionRate: number;
  averageRating: number;
  totalSessions: number;
  
  // Status
  isPublished: boolean;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  
  // Tags
  tags: string[];
  prerequisites?: VRLabId[];
}

/**
 * VR Scene within a lab
 */
export interface VRScene {
  id: VRSceneId;
  labId: VRLabId;
  name: string;
  description: string;
  order: number;
  
  // Scene content
  environmentUrl: string; // glTF/GLB URL
  skyboxUrl?: string;
  lightingPreset: VRLightingPreset;
  
  // Interactive elements
  interactables: VRInteractable[];
  spawnPoints: VR3DPosition[];
  
  // Audio
  ambientSoundUrl?: string;
  narrationUrl?: string;
  
  // Duration
  estimatedDuration: number;
}

/**
 * VR Lab objective for guided learning
 */
export interface VRLabObjective {
  id: string;
  labId: VRLabId;
  title: string;
  description: string;
  order: number;
  
  // Completion criteria
  type: 'interaction' | 'observation' | 'quiz' | 'experiment';
  criteria: VRObjectiveCriteria;
  
  // Hints
  hints: string[];
  
  // Points
  points: number;
  isOptional: boolean;
}

export interface VRObjectiveCriteria {
  targetInteractableId?: string;
  requiredAction?: VRInteractionType;
  expectedResult?: string;
  timeLimit?: number;
}

// ============================================
// VR Interactive Elements
// ============================================

/**
 * Interactive object in VR scene
 */
export interface VRInteractable {
  id: string;
  sceneId: VRSceneId;
  name: string;
  type: VRInteractableType;
  
  // Transform
  position: VR3DPosition;
  rotation: VR3DRotation;
  scale: VR3DScale;
  
  // Model
  modelUrl: string;
  materialOverrides?: VRMaterial[];
  
  // Interactions
  allowedInteractions: VRInteractionType[];
  interactionRange: number;
  
  // Behavior
  behavior: VRInteractableBehavior;
  
  // Metadata
  tooltip?: string;
  audioFeedbackUrl?: string;
}

export type VRInteractableType =
  | 'equipment'
  | 'chemical'
  | 'specimen'
  | 'tool'
  | 'display'
  | 'button'
  | 'lever'
  | 'dial'
  | 'container'
  | 'vehicle'
  | 'npc';

export interface VRInteractableBehavior {
  onGrab?: VRAction;
  onRelease?: VRAction;
  onPointerEnter?: VRAction;
  onPointerExit?: VRAction;
  onActivate?: VRAction;
  physics?: VRPhysicsProperties;
}

export interface VRAction {
  type: 'play_animation' | 'play_sound' | 'spawn_object' | 'trigger_event' | 'show_info' | 'custom';
  params: Record<string, unknown>;
}

export interface VRPhysicsProperties {
  mass: number;
  isKinematic: boolean;
  useGravity: boolean;
  friction: number;
  bounciness: number;
}

// ============================================
// VR 3D Types
// ============================================

export interface VR3DPosition {
  x: number;
  y: number;
  z: number;
}

export interface VR3DRotation {
  x: number;
  y: number;
  z: number;
  w: number; // Quaternion
}

export interface VR3DScale {
  x: number;
  y: number;
  z: number;
}

export interface VRMaterial {
  name: string;
  type: 'pbr' | 'unlit' | 'custom';
  properties: {
    color?: string;
    metallic?: number;
    roughness?: number;
    emissive?: string;
    textureUrl?: string;
  };
}

export type VRLightingPreset =
  | 'daylight'
  | 'sunset'
  | 'night'
  | 'laboratory'
  | 'studio'
  | 'custom';

// ============================================
// VR Session & Progress
// ============================================

/**
 * VR Session represents an active user session in a lab
 */
export interface VRSession {
  id: VRSessionId;
  userId: string;
  labId: VRLabId;
  
  // Session state
  status: VRSessionStatus;
  currentSceneId: VRSceneId;
  
  // Device info
  deviceType: VRDeviceType;
  deviceInfo: VRDeviceInfo;
  
  // Progress
  progress: VRSessionProgress;
  
  // Timing
  startedAt: string;
  lastActiveAt: string;
  endedAt?: string;
  totalDuration: number; // seconds
  
  // Performance
  performanceMetrics: VRPerformanceMetrics;
}

export interface VRDeviceInfo {
  name: string;
  manufacturer: string;
  resolution: { width: number; height: number };
  refreshRate: number;
  trackingType: 'inside_out' | 'outside_in' | 'hybrid';
  controllers: VRControllerInfo[];
}

export interface VRControllerInfo {
  hand: 'left' | 'right';
  type: string;
  hasHaptics: boolean;
}

export interface VRSessionProgress {
  completedObjectives: string[];
  currentObjectiveId?: string;
  totalPoints: number;
  maxPoints: number;
  scenesVisited: VRSceneId[];
  interactionsLog: VRInteractionLog[];
}

export interface VRInteractionLog {
  timestamp: string;
  sceneId: VRSceneId;
  interactableId: string;
  interactionType: VRInteractionType;
  result?: string;
}

export interface VRPerformanceMetrics {
  averageFps: number;
  minFps: number;
  loadTime: number;
  memoryUsage: number;
  latency: number;
}

// ============================================
// VR Requirements
// ============================================

export interface VRRequirements {
  minGpuMemory: number; // MB
  minRam: number; // MB
  minRefreshRate: number;
  requiresControllers: boolean;
  requiresHandTracking: boolean;
  requiresPassthrough: boolean;
}

// ============================================
// VR Asset Management
// ============================================

export interface VRAsset {
  id: VRAssetId;
  name: string;
  type: VRAssetType;
  url: string;
  size: number; // bytes
  format: string;
  
  // Metadata
  thumbnailUrl?: string;
  tags: string[];
  
  // Status
  isPublic: boolean;
  createdAt: string;
  createdBy: string;
}

export type VRAssetType =
  | 'model'
  | 'texture'
  | 'audio'
  | 'video'
  | 'skybox'
  | 'animation'
  | 'script';

// ============================================
// VR Multiplayer
// ============================================

export interface VRMultiplayerSession {
  id: string;
  labId: VRLabId;
  hostUserId: string;
  
  // Participants
  participants: VRParticipant[];
  maxParticipants: number;
  
  // Settings
  voiceChatEnabled: boolean;
  spatialAudioEnabled: boolean;
  
  // Status
  status: 'lobby' | 'active' | 'ended';
  createdAt: string;
}

export interface VRParticipant {
  userId: string;
  displayName: string;
  avatarUrl?: string;
  
  // Transform
  position: VR3DPosition;
  rotation: VR3DRotation;
  
  // Status
  isReady: boolean;
  isMuted: boolean;
  isHost: boolean;
  
  joinedAt: string;
}

// ============================================
// VR Analytics
// ============================================

export interface VRLabAnalytics {
  labId: VRLabId;
  period: 'day' | 'week' | 'month' | 'all';
  
  // Usage
  totalSessions: number;
  uniqueUsers: number;
  averageSessionDuration: number;
  
  // Completion
  completionRate: number;
  averageScore: number;
  objectiveCompletionRates: Record<string, number>;
  
  // Engagement
  mostInteractedObjects: Array<{ id: string; name: string; count: number }>;
  sceneTimeDistribution: Record<VRSceneId, number>;
  
  // Technical
  averageFps: number;
  crashRate: number;
  deviceDistribution: Record<VRDeviceType, number>;
}

// ============================================
// Request/Response Types
// ============================================

export interface CreateVRLabRequest {
  title: string;
  description: string;
  category: VRLabCategory;
  difficulty: VRLabDifficulty;
  estimatedDuration: number;
  thumbnailUrl: string;
  tags?: string[];
}

export interface UpdateVRLabRequest {
  title?: string;
  description?: string;
  category?: VRLabCategory;
  difficulty?: VRLabDifficulty;
  estimatedDuration?: number;
  thumbnailUrl?: string;
  isPublished?: boolean;
  tags?: string[];
}

export interface CreateVRSceneRequest {
  labId: VRLabId;
  name: string;
  description: string;
  order: number;
  environmentUrl: string;
  skyboxUrl?: string;
  lightingPreset: VRLightingPreset;
}

export interface StartVRSessionRequest {
  labId: VRLabId;
  deviceType: VRDeviceType;
  deviceInfo: VRDeviceInfo;
}

export interface UpdateVRSessionRequest {
  status?: VRSessionStatus;
  currentSceneId?: VRSceneId;
  completedObjectiveId?: string;
  interactionLog?: VRInteractionLog;
  performanceMetrics?: Partial<VRPerformanceMetrics>;
}

export interface VRLabListParams {
  category?: VRLabCategory;
  difficulty?: VRLabDifficulty;
  isPublished?: boolean;
  search?: string;
  page?: number;
  limit?: number;
}

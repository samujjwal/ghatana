/**
 * @doc.type contracts
 * @doc.purpose VR Labs types for immersive learning experiences
 * @doc.layer contracts
 * @doc.pattern ValueObject
 */
export type VRLabId = string;
export type VRSceneId = string;
export type VRAssetId = string;
export type VRSessionId = string;
/**
 * VR Lab categories for different subjects
 */
export type VRLabCategory = 'chemistry' | 'physics' | 'biology' | 'astronomy' | 'engineering' | 'anatomy' | 'geology' | 'history' | 'art' | 'mathematics';
/**
 * VR Lab difficulty levels
 */
export type VRLabDifficulty = 'beginner' | 'intermediate' | 'advanced' | 'expert';
/**
 * VR interaction types
 */
export type VRInteractionType = 'grab' | 'point' | 'teleport' | 'rotate' | 'scale' | 'trigger' | 'voice' | 'gesture';
/**
 * VR session status
 */
export type VRSessionStatus = 'initializing' | 'loading' | 'active' | 'paused' | 'completed' | 'failed';
/**
 * VR device types
 */
export type VRDeviceType = 'quest_2' | 'quest_3' | 'quest_pro' | 'vive' | 'index' | 'pico' | 'desktop' | 'mobile';
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
    scenes: VRScene[];
    objectives: VRLabObjective[];
    thumbnailUrl: string;
    previewVideoUrl?: string;
    estimatedDuration: number;
    requiredDevices: VRDeviceType[];
    minRequirements: VRRequirements;
    completionRate: number;
    averageRating: number;
    totalSessions: number;
    isPublished: boolean;
    createdAt: string;
    updatedAt: string;
    createdBy: string;
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
    environmentUrl: string;
    skyboxUrl?: string;
    lightingPreset: VRLightingPreset;
    interactables: VRInteractable[];
    spawnPoints: VR3DPosition[];
    ambientSoundUrl?: string;
    narrationUrl?: string;
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
    type: 'interaction' | 'observation' | 'quiz' | 'experiment';
    criteria: VRObjectiveCriteria;
    hints: string[];
    points: number;
    isOptional: boolean;
}
export interface VRObjectiveCriteria {
    targetInteractableId?: string;
    requiredAction?: VRInteractionType;
    expectedResult?: string;
    timeLimit?: number;
}
/**
 * Interactive object in VR scene
 */
export interface VRInteractable {
    id: string;
    sceneId: VRSceneId;
    name: string;
    type: VRInteractableType;
    position: VR3DPosition;
    rotation: VR3DRotation;
    scale: VR3DScale;
    modelUrl: string;
    materialOverrides?: VRMaterial[];
    allowedInteractions: VRInteractionType[];
    interactionRange: number;
    behavior: VRInteractableBehavior;
    tooltip?: string;
    audioFeedbackUrl?: string;
}
export type VRInteractableType = 'equipment' | 'chemical' | 'specimen' | 'tool' | 'display' | 'button' | 'lever' | 'dial' | 'container' | 'vehicle' | 'npc';
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
export interface VR3DPosition {
    x: number;
    y: number;
    z: number;
}
export interface VR3DRotation {
    x: number;
    y: number;
    z: number;
    w: number;
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
export type VRLightingPreset = 'daylight' | 'sunset' | 'night' | 'laboratory' | 'studio' | 'custom';
/**
 * VR Session represents an active user session in a lab
 */
export interface VRSession {
    id: VRSessionId;
    userId: string;
    labId: VRLabId;
    status: VRSessionStatus;
    currentSceneId: VRSceneId;
    deviceType: VRDeviceType;
    deviceInfo: VRDeviceInfo;
    progress: VRSessionProgress;
    startedAt: string;
    lastActiveAt: string;
    endedAt?: string;
    totalDuration: number;
    performanceMetrics: VRPerformanceMetrics;
}
export interface VRDeviceInfo {
    name: string;
    manufacturer: string;
    resolution: {
        width: number;
        height: number;
    };
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
export interface VRRequirements {
    minGpuMemory: number;
    minRam: number;
    minRefreshRate: number;
    requiresControllers: boolean;
    requiresHandTracking: boolean;
    requiresPassthrough: boolean;
}
export interface VRAsset {
    id: VRAssetId;
    name: string;
    type: VRAssetType;
    url: string;
    size: number;
    format: string;
    thumbnailUrl?: string;
    tags: string[];
    isPublic: boolean;
    createdAt: string;
    createdBy: string;
}
export type VRAssetType = 'model' | 'texture' | 'audio' | 'video' | 'skybox' | 'animation' | 'script';
export interface VRMultiplayerSession {
    id: string;
    labId: VRLabId;
    hostUserId: string;
    participants: VRParticipant[];
    maxParticipants: number;
    voiceChatEnabled: boolean;
    spatialAudioEnabled: boolean;
    status: 'lobby' | 'active' | 'ended';
    createdAt: string;
}
export interface VRParticipant {
    userId: string;
    displayName: string;
    avatarUrl?: string;
    position: VR3DPosition;
    rotation: VR3DRotation;
    isReady: boolean;
    isMuted: boolean;
    isHost: boolean;
    joinedAt: string;
}
export interface VRLabAnalytics {
    labId: VRLabId;
    period: 'day' | 'week' | 'month' | 'all';
    totalSessions: number;
    uniqueUsers: number;
    averageSessionDuration: number;
    completionRate: number;
    averageScore: number;
    objectiveCompletionRates: Record<string, number>;
    mostInteractedObjects: Array<{
        id: string;
        name: string;
        count: number;
    }>;
    sceneTimeDistribution: Record<VRSceneId, number>;
    averageFps: number;
    crashRate: number;
    deviceDistribution: Record<VRDeviceType, number>;
}
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
//# sourceMappingURL=vr-labs.d.ts.map
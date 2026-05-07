export { createAIProxyService, TutorPutorAIProxyService } from "./service";
export type { AIProxyServiceConfig } from "./service";
export {
  buildSocraticTutorPrompt,
  classifyTutorQuestion,
  validateTutorGroundingContext,
} from "./socratic-tutor-policy";
export type {
  AllowedHelpMode,
  TutorAttemptContext,
  TutorGroundingContext,
  TutorGroundingValidation,
} from "./socratic-tutor-policy";

export { AIEditingAssistant, createAIEditingAssistant } from "./ai-editing-assistant";
export type {
  AIEditingAssistantConfig,
  AnimationConfig,
  AnimationLayer,
  Keyframe,
  EasingFunction,
  SimulationManifest,
  SimulationEntity,
  PhysicsParameters,
  SimulationGoal,
  ContentExample,
  Vec2,
} from "./ai-editing-assistant";

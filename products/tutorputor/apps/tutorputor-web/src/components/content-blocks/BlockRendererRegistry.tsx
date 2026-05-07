/**
 * Block Renderer Registry
 * 
 * Provides a registry-based approach for rendering different content block types.
 * New block types can be registered without modifying the core rendering logic.
 */

import type { ContentBlock } from "../../pages/cms/types";

export interface BlockRenderer {
  blockType: string;
  render: (block: ContentBlock, index: number) => React.ReactNode;
}

class BlockRendererRegistry {
  private renderers: Map<string, BlockRenderer> = new Map();

  register(renderer: BlockRenderer): void {
    this.renderers.set(renderer.blockType, renderer);
  }

  getRenderer(blockType: string): BlockRenderer | undefined {
    return this.renderers.get(blockType);
  }

  render(block: ContentBlock, index: number): React.ReactNode {
    const renderer = this.getRenderer(block.blockType);
    if (renderer) {
      return renderer.render(block, index);
    }

    // Fallback renderer for unregistered block types
    return (
      <div className="text-gray-500 dark:text-gray-400 italic border-l-4 border-yellow-500 pl-4">
        <p className="font-medium">Unknown Block Type</p>
        <p className="text-sm">{block.blockType} content (renderer not registered)</p>
      </div>
    );
  }

  getRegisteredTypes(): string[] {
    return Array.from(this.renderers.keys());
  }
}

// Global registry instance
export const blockRendererRegistry = new BlockRendererRegistry();

// Helper to safely access payload properties
function getPayloadProperty<T>(block: ContentBlock, key: string, defaultValue: T): T {
  if (block.payload && typeof block.payload === 'object' && key in block.payload) {
    return (block.payload as Record<string, unknown>)[key] as T;
  }
  return defaultValue;
}

// Register built-in renderers
blockRendererRegistry.register({
  blockType: "text",
  render: (block) => (
    <div className="prose max-w-none">
      <p className="text-gray-700 dark:text-gray-200 whitespace-pre-wrap">
        {getPayloadProperty(block, "markdown", "")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "exercise",
  render: (block) => (
    <div className="border-l-4 border-green-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Exercise</p>
      <p className="text-gray-700 dark:text-gray-200">{getPayloadProperty(block, "prompt", "")}</p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "video",
  render: (block) => (
    <div className="border-l-4 border-blue-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Video</p>
      {getPayloadProperty<string>(block, "url", "") ? (
        <video
          src={getPayloadProperty(block, "url", "")}
          controls
          className="w-full max-w-lg rounded"
        />
      ) : (
        <p className="text-gray-500 dark:text-gray-400">No video URL provided</p>
      )}
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "quiz",
  render: (block) => (
    <div className="border-l-4 border-purple-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Quiz</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "question", "Quiz question not provided")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "interactive",
  render: (block) => (
    <div className="border-l-4 border-orange-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Interactive Element</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "description", "Interactive content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "simulation",
  render: (block) => (
    <div className="border-l-4 border-cyan-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Simulation</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "title", "Simulation content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "animation",
  render: (block) => (
    <div className="border-l-4 border-pink-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Animation</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "description", "Animation content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "assessment",
  render: (block) => (
    <div className="border-l-4 border-indigo-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Assessment</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "title", "Assessment content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "ai_tutor_prompt",
  render: (block) => (
    <div className="border-l-4 border-amber-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">AI Tutor Prompt</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "prompt", "AI tutor content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "chart",
  render: (block) => (
    <div className="border-l-4 border-teal-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Chart</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "title", "Chart content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "evidence",
  render: (block) => (
    <div className="border-l-4 border-lime-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Evidence Block</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "description", "Evidence content")}
      </p>
    </div>
  ),
});

blockRendererRegistry.register({
  blockType: "task",
  render: (block) => (
    <div className="border-l-4 border-rose-500 pl-4">
      <p className="font-medium text-gray-900 dark:text-white mb-2">Task Block</p>
      <p className="text-gray-700 dark:text-gray-200">
        {getPayloadProperty(block, "description", "Task content")}
      </p>
    </div>
  ),
});

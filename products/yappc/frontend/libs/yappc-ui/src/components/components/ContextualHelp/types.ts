/**
 * Types for the Contextual Help System
 * @module components/ContextualHelp/types
 */

/**
 * Help content item
 *
 * @property id - Unique identifier for the help topic
 * @property title - Display title of the help topic
 * @property content - Main help content/description
 * @property category - Category this help belongs to
 * @property keywords - Search keywords for this content
 * @property relatedTopics - IDs of related help topics
 * @property videoUrl - Optional URL to video tutorial
 * @property externalLink - Optional external link for more info
 * @property priority - Priority number (higher = higher priority)
 *
 * @example
 * ```typescript
 * const topic: HelpContent = {
 *   id: 'canvas-basics',
 *   title: 'Canvas Basics',
 *   content: 'Learn how to create and manage elements...',
 *   category: 'canvas',
 *   keywords: ['canvas', 'nodes'],
 *   priority: 100
 * };
 * ```
 */
export interface HelpContent {
  id: string;
  title: string;
  content: string;
  category:
    | 'getting-started'
    | 'canvas'
    | 'collaboration'
    | 'shortcuts'
    | 'troubleshooting';
  keywords: string[];
  relatedTopics?: string[];
  videoUrl?: string;
  externalLink?: string;
  priority: number;
}

/**
 * Props for the ContextualHelp component
 *
 * @property context - Current page/component context for matching help topics
 * @property trigger - How the help is triggered (hover, click, or focus)
 * @property position - Position of the tooltip (top, bottom, left, right, auto)
 * @property className - Additional CSS classes
 * @property children - Content to wrap with contextual help
 *
 * @example
 * ```typescript
 * <ContextualHelp context="canvas" trigger="hover">
 *   <button>Draw Shape</button>
 * </ContextualHelp>
 * ```
 */
export interface ContextualHelpProps {
  context?: string;
  trigger?: 'hover' | 'click' | 'focus';
  position?: 'top' | 'bottom' | 'left' | 'right' | 'auto';
  className?: string;
  children?: React.ReactNode;
}

/**
 * Props for the HelpPanel component
 *
 * @property isOpen - Whether the help panel is open
 * @property onClose - Callback when panel should close
 * @property context - Context for help topic filtering
 * @property searchQuery - Initial search query
 * @property className - Additional CSS classes
 *
 * @example
 * ```typescript
 * <HelpPanel
 *   isOpen={showHelp}
 *   onClose={() => setShowHelp(false)}
 *   context="collaboration"
 * />
 * ```
 */
export interface HelpPanelProps {
  isOpen: boolean;
  onClose: () => void;
  context?: string;
  searchQuery?: string;
  className?: string;
}

/**
 * Props for the HelpTrigger component
 *
 * @property className - Additional CSS classes
 * @property context - Context for help topic filtering
 *
 * @example
 * ```typescript
 * <HelpTrigger context="canvas" />
 * ```
 */
export interface HelpTriggerProps {
  className?: string;
  context?: string;
}

/**
 * Help category definition
 *
 * @property id - Category identifier
 * @property name - Display name
 * @property icon - Emoji or icon for the category
 *
 * @internal Used internally by HelpPanel for category navigation
 */
export interface HelpCategory {
  id: string;
  name: string;
  icon: string;
}

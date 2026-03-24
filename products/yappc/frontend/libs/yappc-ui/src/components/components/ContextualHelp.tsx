/**
 * ContextualHelp - Re-export for backward compatibility
 *
 * The Contextual Help system has been modularized into separate files.
 * This file re-exports everything for backward compatibility.
 *
 * New imports should use the ContextualHelp directory directly.
 *
 * @deprecated Import from './components/ContextualHelp' instead
 */

export {
  ContextualHelp,
  HelpPanel,
  HelpTrigger,
  helpContentManager,
  HelpContentManager,
  HelpContentUtils,
  DEFAULT_HELP_CONTENT,
} from './ContextualHelp/index';

export type {
  ContextualHelpProps,
  HelpPanelProps,
  HelpTriggerProps,
  HelpContent,
  HelpCategory,
} from './ContextualHelp/types';

export { default } from './ContextualHelp/ContextualHelp';

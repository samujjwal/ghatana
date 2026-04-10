/**
 * @ghatana/wizard — platform wizard/stepper library.
 *
 * Provides accessible multi-step wizard navigation components and hooks
 * for all Ghatana products.
 *
 * @doc.type module
 * @doc.purpose Shared wizard/stepper for Ghatana platform
 * @doc.layer platform
 * @doc.pattern Library
 */

export { Wizard } from './Wizard';
export type { WizardProps } from './Wizard';
export { useWizard } from './hooks/useWizard';
export type { WizardStep, WizardState, WizardActions, UseWizardReturn } from './hooks/useWizard';

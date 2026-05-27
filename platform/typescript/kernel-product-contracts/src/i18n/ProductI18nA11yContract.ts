/**
 * K-009: Product i18n/a11y contract.
 * Route/action/card labels and accessibility metadata are validated.
 */

export type I18nKey = string;

export type I18nEntry = {
  key: I18nKey;
  defaultValue: string;
  parameters?: string[];
  context?: string;
};

export type A11yLabel = {
  id: string;
  label: string;
  description?: string;
  role?: 'button' | 'link' | 'input' | 'heading' | 'region' | 'navigation' | 'main' | 'complementary';
  liveRegion?: 'polite' | 'assertive';
};

export type A11yRequirement = {
  keyboardNavigable: boolean;
  screenReaderLabel: boolean;
  focusIndicator: boolean;
  colorContrast?: 'AA' | 'AAA';
  touchTargetSize?: number;
};

export type ProductI18nA11yContract = {
  version: string;
  locales: string[];
  entries: I18nEntry[];
  a11yLabels: A11yLabel[];
  a11yRequirements: A11yRequirement;
};

export function createI18nEntry(
  key: I18nKey,
  defaultValue: string,
  options?: Partial<Omit<I18nEntry, 'key' | 'defaultValue'>>
): I18nEntry {
  const result: I18nEntry = { key, defaultValue };
  
  if (options?.parameters) result.parameters = options.parameters;
  if (options?.context) result.context = options.context;
  
  return result;
}

export function createA11yLabel(
  id: string,
  label: string,
  options?: Partial<Omit<A11yLabel, 'id' | 'label'>>
): A11yLabel {
  const result: A11yLabel = { id, label };
  
  if (options?.description) result.description = options.description;
  if (options?.role) result.role = options.role;
  if (options?.liveRegion) result.liveRegion = options.liveRegion;
  
  return result;
}

export function createA11yRequirement(
  options?: Partial<A11yRequirement>
): A11yRequirement {
  const result: A11yRequirement = {
    keyboardNavigable: options?.keyboardNavigable ?? true,
    screenReaderLabel: options?.screenReaderLabel ?? true,
    focusIndicator: options?.focusIndicator ?? true,
  };
  
  if (options?.colorContrast) result.colorContrast = options.colorContrast;
  if (options?.touchTargetSize !== undefined) result.touchTargetSize = options.touchTargetSize;
  
  return result;
}

export function validateI18nEntry(entry: I18nEntry): boolean {
  if (!entry.key || !entry.defaultValue) return false;
  if (entry.key.includes(' ')) return false;
  return true;
}

export function validateA11yLabel(label: A11yLabel): boolean {
  if (!label.id || !label.label) return false;
  if (label.role && !['button', 'link', 'input', 'heading', 'region', 'navigation', 'main', 'complementary'].includes(label.role)) {
    return false;
  }
  return true;
}

export function hasI18nKey(contract: ProductI18nA11yContract, key: I18nKey): boolean {
  return contract.entries.some(e => e.key === key);
}

export function getI18nEntry(contract: ProductI18nA11yContract, key: I18nKey): I18nEntry | undefined {
  return contract.entries.find(e => e.key === key);
}

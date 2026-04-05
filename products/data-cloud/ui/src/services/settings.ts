/**
 * Settings Service Interface
 * 
 * @doc.type interface
 * @doc.purpose Settings management and validation
 * @doc.layer ui
 * @doc.pattern Service
 */
export interface SettingsService {
  /** Get all settings for tenant */
  getSettings(tenantId: string): Promise<Settings>;
  
  /** Get specific setting by key */
  getSetting(tenantId: string, key: string): Promise<SettingValue | null>;
  
  /** Update setting */
  updateSetting(tenantId: string, key: string, value: SettingValue): Promise<Setting>;
  
  /** Update multiple settings */
  updateSettings(tenantId: string, settings: Record<string, SettingValue>): Promise<Settings>;
  
  /** Reset setting to default */
  resetSetting(tenantId: string, key: string): Promise<Setting>;
  
  /** Validate setting value */
  validateSetting(key: string, value: SettingValue): Promise<ValidationResult>;
  
  /** Get setting change history */
  getSettingHistory(tenantId: string, key?: string): Promise<SettingChange[]>;
  
  /** Export settings */
  exportSettings(tenantId: string): Promise<string>;
  
  /** Import settings */
  importSettings(tenantId: string, settingsJson: string): Promise<ImportResult>;
  
  /** Get setting categories */
  getSettingCategories(): Promise<SettingCategory[]>;
}

/** Settings container */
export interface Settings {
  tenantId: string;
  version: number;
  lastModified: string;
  modifiedBy: string;
  values: Record<string, Setting>;
}

/** Setting definition */
export interface Setting {
  key: string;
  value: SettingValue;
  type: SettingType;
  category: SettingCategory;
  label: string;
  description: string;
  defaultValue: SettingValue;
  constraints?: SettingConstraints;
  editable: boolean;
  visible: boolean;
  sensitive: boolean;
  lastModified: string;
}

/** Setting value type */
export type SettingValue = string | number | boolean | string[] | Record<string, unknown> | null;

/** Setting type */
export type SettingType = 'string' | 'number' | 'boolean' | 'select' | 'multiselect' | 'json' | 'password';

/** Setting category */
export type SettingCategory = 
  | 'general' 
  | 'security' 
  | 'notifications' 
  | 'integrations' 
  | 'performance' 
  | 'display' 
  | 'privacy';

/** Setting constraints */
export interface SettingConstraints {
  min?: number;
  max?: number;
  pattern?: string;
  options?: string[];
  required?: boolean;
  maxLength?: number;
}

/** Setting change record */
export interface SettingChange {
  id: string;
  key: string;
  oldValue: SettingValue;
  newValue: SettingValue;
  changedBy: string;
  changedAt: string;
  reason?: string;
}

/** Validation result */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  normalized?: SettingValue;
}

/** Validation error */
export interface ValidationError {
  code: string;
  message: string;
  field: string;
}

/** Import result */
export interface ImportResult {
  success: boolean;
  imported: number;
  failed: number;
  errors: ImportError[];
}

/** Import error */
export interface ImportError {
  key: string;
  message: string;
}

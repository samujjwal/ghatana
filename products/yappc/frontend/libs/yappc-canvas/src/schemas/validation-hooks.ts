// Phase 8: Component Schema Registry - React hooks integration
// Integrates schema validation with React components and useCanvasApi

import { useCallback, useMemo, useState, useEffect } from 'react';

import { 
  componentSchemaRegistry, 
  validateComponent, 
  createComponent 
} from './component-registry';
import { 
  validateImportData, 
  validateExportData,
  getValidationStats,
  createCanvasAPIValidator,
  PRODUCTION_VALIDATION_CONFIG,
  DEVELOPMENT_VALIDATION_CONFIG,
  type CanvasAPIValidationConfig,
  type ImportValidationResult
} from './validation-helpers';

import type { 
  CanvasComponent,
  ValidationResult 
} from './component-registry';

// Hook for component validation
/**
 *
 */
export interface UseComponentValidationOptions {
  strictMode?: boolean;
  autoValidate?: boolean;
  onValidationError?: (errors: string[]) => void;
}

export const useComponentValidation = (options: UseComponentValidationOptions = {}) => {
  const { strictMode = false, autoValidate = true, onValidationError } = options;
  
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [isValidating, setIsValidating] = useState(false);

  const validateSingle = useCallback((type: string, data: unknown): ValidationResult<CanvasComponent> => {
    setIsValidating(true);
    
    try {
      const result = validateComponent(type, data);
      
      if (!result.success) {
        setValidationErrors(result.errors);
        if (onValidationError) {
          onValidationError(result.errors);
        }
        if (strictMode) {
          throw new Error(`Component validation failed: ${result.errors.join(', ')}`);
        }
      } else {
        setValidationErrors([]);
      }
      
      return result;
    } finally {
      setIsValidating(false);
    }
  }, [strictMode, onValidationError]);

  const validateBatch = useCallback((
    components: Array<{ type: string; data: unknown }>
  ): Array<ValidationResult<CanvasComponent>> => {
    setIsValidating(true);
    
    try {
      const results = components.map(({ type, data }) => validateComponent(type, data));
      const allErrors = results.flatMap(r => r.errors);
      
      setValidationErrors(allErrors);
      if (allErrors.length > 0 && onValidationError) {
        onValidationError(allErrors);
      }
      
      return results;
    } finally {
      setIsValidating(false);
    }
  }, [onValidationError]);

  const createValidated = useCallback((type: string, overrides: Partial<CanvasComponent> = {}): CanvasComponent => {
    try {
      return createComponent(type, overrides);
    } catch (error) {
      const errorMessage = (error as Error).message;
      setValidationErrors([errorMessage]);
      if (onValidationError) {
        onValidationError([errorMessage]);
      }
      if (strictMode) {
        throw error;
      }
      throw error; // createComponent should always throw on failure
    }
  }, [strictMode, onValidationError]);

  return {
    validate: validateSingle,
    validateBatch,
    createValidated,
    validationErrors,
    isValidating,
    clearErrors: () => setValidationErrors([]),
  };
};

// Hook for canvas-wide validation
/**
 *
 */
export interface UseCanvasValidationOptions {
  autoValidateOnChange?: boolean;
  validationConfig?: CanvasAPIValidationConfig;
  onValidationComplete?: (stats: ReturnType<typeof getValidationStats>) => void;
}

export const useCanvasValidation = (
  canvas: { nodes: CanvasComponent[]; edges: unknown[] },
  options: UseCanvasValidationOptions = {}
) => {
  const { 
    autoValidateOnChange = true, 
    validationConfig = PRODUCTION_VALIDATION_CONFIG,
    onValidationComplete
  } = options;
  
  const [validationStats, setValidationStats] = useState<ReturnType<typeof getValidationStats> | null>(null);
  const [isValidating, setIsValidating] = useState(false);

  const validator = useMemo(() => createCanvasAPIValidator(validationConfig), [validationConfig]);

  const validateCanvas = useCallback(async () => {
    setIsValidating(true);
    
    try {
      const stats = getValidationStats(canvas);
      setValidationStats(stats);
      
      if (onValidationComplete) {
        onValidationComplete(stats);
      }
      
      return stats;
    } finally {
      setIsValidating(false);
    }
  }, [canvas, onValidationComplete]);

  const validateImport = useCallback((importData: unknown): ImportValidationResult => {
    return validator.validateImport(importData);
  }, [validator]);

  const validateExport = useCallback(() => {
    return validateExportData(canvas.nodes, canvas.edges, {
      validateBeforeExport: validationConfig.validateOnImport,
    });
  }, [canvas, validationConfig]);

  // Auto-validate when canvas changes
  useEffect(() => {
    if (autoValidateOnChange) {
      validateCanvas();
    }
  }, [canvas.nodes, canvas.edges, autoValidateOnChange, validateCanvas]);

  return {
    validationStats,
    isValidating,
    validateCanvas,
    validateImport,
    validateExport,
    validator,
    isHealthy: validationStats?.overall.healthy ?? true,
  };
};

// Hook for schema registry management
export const useSchemaRegistry = () => {
  const [registeredSchemas, setRegisteredSchemas] = useState<string[]>([]);

  useEffect(() => {
    const updateSchemas = () => {
      setRegisteredSchemas(componentSchemaRegistry.listSchemas());
    };
    
    updateSchemas();
    
    // Set up a simple polling mechanism since we don't have events
    const interval = setInterval(updateSchemas, 5000);
    return () => clearInterval(interval);
  }, []);

  const registerSchema = useCallback((type: string, schema: unknown) => {
    componentSchemaRegistry.registerSchema(type, schema);
    setRegisteredSchemas(componentSchemaRegistry.listSchemas());
  }, []);

  const getSchema = useCallback((type: string) => {
    return componentSchemaRegistry.getSchema(type);
  }, []);

  const getDefaultData = useCallback((type: string) => {
    return componentSchemaRegistry.getDefaultData(type);
  }, []);

  const setDefaultData = useCallback((type: string, factory: () => Partial<CanvasComponent>) => {
    componentSchemaRegistry.setDefaultData(type, factory);
  }, []);

  return {
    registeredSchemas,
    registerSchema,
    getSchema,
    getDefaultData,
    setDefaultData,
    registry: componentSchemaRegistry,
  };
};

// Hook for component creation with validation
/**
 *
 */
export interface UseComponentCreatorOptions {
  defaultPosition?: { x: number; y: number };
  defaultSize?: { width: number; height: number };
  validateOnCreate?: boolean;
  onCreateError?: (error: string) => void;
}

export const useComponentCreator = (options: UseComponentCreatorOptions = {}) => {
  const {
    defaultPosition = { x: 0, y: 0 },
    defaultSize = { width: 200, height: 100 },
    validateOnCreate = true,
    onCreateError
  } = options;

  const { validate } = useComponentValidation({
    strictMode: false,
    onValidationError: onCreateError ? (errors) => onCreateError(errors.join(', ')) : undefined,
  });

  const create = useCallback((type: string, customData: Partial<CanvasComponent> = {}): CanvasComponent | null => {
    try {
      const component = createComponent(type, {
        position: defaultPosition,
        size: defaultSize,
        ...customData,
      });

      if (validateOnCreate) {
        const validation = validate(type, component);
        if (!validation.success) {
          if (onCreateError) {
            onCreateError(`Component creation failed: ${validation.errors.join(', ')}`);
          }
          return null;
        }
        return validation.data!;
      }

      return component;
    } catch (error) {
      if (onCreateError) {
        onCreateError((error as Error).message);
      }
      return null;
    }
  }, [defaultPosition, defaultSize, validateOnCreate, validate, onCreateError]);

  const createBatch = useCallback((
    specs: Array<{ type: string; data?: Partial<CanvasComponent> }>
  ): CanvasComponent[] => {
    const results: CanvasComponent[] = [];
    
    for (const spec of specs) {
      const component = create(spec.type, spec.data);
      if (component) {
        results.push(component);
      }
    }
    
    return results;
  }, [create]);

  const getAvailableTypes = useCallback(() => {
    return componentSchemaRegistry.listSchemas().filter(type => type !== 'edge');
  }, []);

  const getTypeDefault = useCallback((type: string) => {
    return componentSchemaRegistry.getDefaultData(type);
  }, []);

  return {
    create,
    createBatch,
    getAvailableTypes,
    getTypeDefault,
  };
};

// Hook for real-time validation during editing
/**
 *
 */
export interface UseRealTimeValidationOptions {
  debounceMs?: number;
  enableRealTime?: boolean;
}

export const useRealTimeValidation = (
  component: CanvasComponent,
  options: UseRealTimeValidationOptions = {}
) => {
  const { debounceMs = 300, enableRealTime = true } = options;
  
  const [validationState, setValidationState] = useState<{
    isValid: boolean;
    errors: string[];
    lastValidated: Date | null;
  }>({
    isValid: true,
    errors: [],
    lastValidated: null,
  });

  const [debouncedComponent, setDebouncedComponent] = useState(component);

  // Debounce component changes
  useEffect(() => {
    if (!enableRealTime) return;

    const timer = setTimeout(() => {
      setDebouncedComponent(component);
    }, debounceMs);

    return () => clearTimeout(timer);
  }, [component, debounceMs, enableRealTime]);

  // Validate debounced component
  useEffect(() => {
    if (!enableRealTime) return;

    const validation = validateComponent(debouncedComponent.type, debouncedComponent);
    setValidationState({
      isValid: validation.success,
      errors: validation.errors,
      lastValidated: new Date(),
    });
  }, [debouncedComponent, enableRealTime]);

  const forceValidate = useCallback(() => {
    const validation = validateComponent(component.type, component);
    setValidationState({
      isValid: validation.success,
      errors: validation.errors,
      lastValidated: new Date(),
    });
    return validation;
  }, [component]);

  return {
    ...validationState,
    forceValidate,
    isRealTimeEnabled: enableRealTime,
  };
};

// Development vs Production configuration
export const useValidationConfig = (environment: 'development' | 'production' = 'production') => {
  const config = useMemo(() => {
    return environment === 'development' 
      ? DEVELOPMENT_VALIDATION_CONFIG 
      : PRODUCTION_VALIDATION_CONFIG;
  }, [environment]);

  return config;
};
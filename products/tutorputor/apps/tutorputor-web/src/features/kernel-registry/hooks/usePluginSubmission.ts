/**
 * Plugin Submission Form Hook
 *
 * Hook for managing plugin submission state and API calls.
 *
 * @doc.type hook
 * @doc.purpose Manage plugin submission workflow
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback } from "react";
import { useMutation } from "@tanstack/react-query";

// =============================================================================
// Types
// =============================================================================

export type PluginType = "kernel" | "promptPack" | "visualizer";

export type PluginLanguage = "typescript" | "javascript" | "wasm";

export interface PluginMetadata {
  id: string;
  name: string;
  version: string;
  description: string;
  author: string;
  license?: string;
  repository?: string;
  tags?: string[];
}

export interface ResourceLimits {
  maxMemoryMB: number;
  maxExecutionTimeMs: number;
  maxBundleSizeBytes: number;
}

export interface PluginSubmissionData {
  metadata: PluginMetadata;
  type: PluginType;
  domain: string;
  language: PluginLanguage;
  bundleFile?: File;
  bundleUrl?: string;
  sourceMapUrl?: string;
  documentation?: string;
  resourceLimits?: Partial<ResourceLimits>;
  testManifestId?: string;
}

export interface PluginSubmissionError {
  code: string;
  message: string;
  severity: "error" | "warning";
  location?: string;
}

export interface PluginSubmissionResult {
  id: string;
  status: "pending" | "approved" | "rejected";
  message: string;
  warnings?: PluginSubmissionError[];
}

export interface UsePluginSubmissionReturn {
  // State
  step: SubmissionStep;
  isSubmitting: boolean;
  isValidating: boolean;
  error: Error | null;
  validationErrors: PluginSubmissionError[];
  validationWarnings: PluginSubmissionError[];
  result: PluginSubmissionResult | null;

  // Actions
  setStep: (step: SubmissionStep) => void;
  nextStep: () => void;
  prevStep: () => void;
  validateMetadata: (metadata: PluginMetadata) => Promise<boolean>;
  validateBundle: (file: File) => Promise<boolean>;
  submit: (data: PluginSubmissionData) => Promise<void>;
  reset: () => void;
}

export type SubmissionStep = 
  | "metadata"
  | "domain"
  | "bundle"
  | "resources"
  | "documentation"
  | "review"
  | "complete";

const STEPS: SubmissionStep[] = [
  "metadata",
  "domain",
  "bundle",
  "resources",
  "documentation",
  "review",
  "complete",
];

// =============================================================================
// API Functions
// =============================================================================

const API_BASE = "/api/v1/plugins";

async function submitPlugin(
  data: PluginSubmissionData
): Promise<PluginSubmissionResult> {
  let bundleBase64: string | undefined;

  if (data.bundleFile) {
    const arrayBuffer = await data.bundleFile.arrayBuffer();
    bundleBase64 = btoa(
      new Uint8Array(arrayBuffer).reduce(
        (data, byte) => data + String.fromCharCode(byte),
        ""
      )
    );
  }

  const response = await fetch(API_BASE, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      metadata: data.metadata,
      type: data.type,
      domain: data.domain,
      language: data.language,
      bundleUrl: data.bundleUrl,
      bundleBase64,
      sourceMapUrl: data.sourceMapUrl,
      documentation: data.documentation,
      resourceLimits: data.resourceLimits,
      testManifestId: data.testManifestId,
    }),
  });

  if (!response.ok) {
    const errorData = await response.json();
    throw new Error(errorData.error || "Failed to submit plugin");
  }

  return response.json();
}

async function validatePluginMetadata(
  metadata: PluginMetadata
): Promise<{ valid: boolean; errors: PluginSubmissionError[] }> {
  // Client-side validation
  const errors: PluginSubmissionError[] = [];

  if (!metadata.id || metadata.id.length < 3) {
    errors.push({
      code: "INVALID_ID",
      message: "Plugin ID must be at least 3 characters",
      severity: "error",
    });
  }

  if (!/^[a-z0-9][a-z0-9-]*[a-z0-9]$/.test(metadata.id)) {
    errors.push({
      code: "INVALID_ID_FORMAT",
      message: "Plugin ID must contain only lowercase letters, numbers, and hyphens",
      severity: "error",
    });
  }

  if (!metadata.name || metadata.name.length < 2) {
    errors.push({
      code: "INVALID_NAME",
      message: "Plugin name must be at least 2 characters",
      severity: "error",
    });
  }

  if (!/^\d+\.\d+\.\d+/.test(metadata.version)) {
    errors.push({
      code: "INVALID_VERSION",
      message: "Version must be a valid semver (e.g., 1.0.0)",
      severity: "error",
    });
  }

  if (!metadata.description || metadata.description.length < 10) {
    errors.push({
      code: "INVALID_DESCRIPTION",
      message: "Description must be at least 10 characters",
      severity: "error",
    });
  }

  return { valid: errors.length === 0, errors };
}

async function validatePluginBundle(
  file: File
): Promise<{ valid: boolean; errors: PluginSubmissionError[] }> {
  const errors: PluginSubmissionError[] = [];

  // Check file size (5MB max)
  const MAX_SIZE = 5 * 1024 * 1024;
  if (file.size > MAX_SIZE) {
    errors.push({
      code: "BUNDLE_TOO_LARGE",
      message: `Bundle size (${(file.size / 1024 / 1024).toFixed(2)}MB) exceeds maximum of 5MB`,
      severity: "error",
    });
  }

  // Check file type
  const validTypes = [
    "application/javascript",
    "text/javascript",
    "application/wasm",
  ];
  if (!validTypes.includes(file.type) && !file.name.endsWith(".js") && !file.name.endsWith(".wasm")) {
    errors.push({
      code: "INVALID_BUNDLE_TYPE",
      message: "Bundle must be a JavaScript or WebAssembly file",
      severity: "error",
    });
  }

  // Read and scan for prohibited imports (for JS)
  if (file.type.includes("javascript") || file.name.endsWith(".js")) {
    const text = await file.text();
    const prohibitedImports = [
      "child_process",
      "fs",
      "net",
      "http",
      "https",
    ];

    for (const imp of prohibitedImports) {
      if (text.includes(`'${imp}'`) || text.includes(`"${imp}"`)) {
        errors.push({
          code: "PROHIBITED_IMPORT",
          message: `Use of "${imp}" is prohibited for security reasons`,
          severity: "error",
        });
      }
    }
  }

  return { valid: errors.length === 0, errors };
}

// =============================================================================
// Hook
// =============================================================================

export function usePluginSubmission(): UsePluginSubmissionReturn {
  const [step, setStep] = useState<SubmissionStep>("metadata");
  const [validationErrors, setValidationErrors] = useState<PluginSubmissionError[]>([]);
  const [validationWarnings, setValidationWarnings] = useState<PluginSubmissionError[]>([]);
  const [result, setResult] = useState<PluginSubmissionResult | null>(null);
  const [isValidating, setIsValidating] = useState(false);

  const submitMutation = useMutation({
    mutationFn: submitPlugin,
    onSuccess: (data) => {
      setResult(data);
      setValidationWarnings(data.warnings || []);
      setStep("complete");
    },
    onError: (error: Error) => {
      setValidationErrors([
        {
          code: "SUBMISSION_FAILED",
          message: error.message,
          severity: "error",
        },
      ]);
    },
  });

  const nextStep = useCallback(() => {
    const currentIndex = STEPS.indexOf(step);
    if (currentIndex < STEPS.length - 1) {
      setStep(STEPS[currentIndex + 1]);
    }
  }, [step]);

  const prevStep = useCallback(() => {
    const currentIndex = STEPS.indexOf(step);
    if (currentIndex > 0) {
      setStep(STEPS[currentIndex - 1]);
    }
  }, [step]);

  const validateMetadata = useCallback(async (metadata: PluginMetadata): Promise<boolean> => {
    setIsValidating(true);
    try {
      const result = await validatePluginMetadata(metadata);
      setValidationErrors(result.errors);
      return result.valid;
    } finally {
      setIsValidating(false);
    }
  }, []);

  const validateBundle = useCallback(async (file: File): Promise<boolean> => {
    setIsValidating(true);
    try {
      const result = await validatePluginBundle(file);
      setValidationErrors(result.errors);
      return result.valid;
    } finally {
      setIsValidating(false);
    }
  }, []);

  const submit = useCallback(async (data: PluginSubmissionData): Promise<void> => {
    setValidationErrors([]);
    await submitMutation.mutateAsync(data);
  }, [submitMutation]);

  const reset = useCallback(() => {
    setStep("metadata");
    setValidationErrors([]);
    setValidationWarnings([]);
    setResult(null);
    submitMutation.reset();
  }, [submitMutation]);

  return {
    step,
    isSubmitting: submitMutation.isPending,
    isValidating,
    error: submitMutation.error,
    validationErrors,
    validationWarnings,
    result,
    setStep,
    nextStep,
    prevStep,
    validateMetadata,
    validateBundle,
    submit,
    reset,
  };
}

/**
 * PluginSubmissionForm Component
 *
 * Multi-step form for submitting new kernel plugins to the registry.
 *
 * @doc.type component
 * @doc.purpose Allow developers to submit kernel plugins
 * @doc.layer product
 * @doc.pattern Form
 */

import { useState, useCallback, useRef } from "react";
import { Button, TextField, Select, Badge, Stepper } from "@ghatana/design-system";
import {
  usePluginSubmission,
  type PluginMetadata,
  type PluginType,
  type PluginLanguage,
  type PluginSubmissionData,
  type PluginSubmissionError,
  type SubmissionStep,
} from "../hooks/usePluginSubmission";

// =============================================================================
// Props
// =============================================================================

export interface PluginSubmissionFormProps {
  onSuccess?: (pluginId: string) => void;
  onCancel?: () => void;
  className?: string;
}

// =============================================================================
// Constants
// =============================================================================

const PLUGIN_TYPES: { value: PluginType; label: string; description: string }[] = [
  {
    value: "kernel",
    label: "Kernel",
    description: "Simulation computation engine",
  },
  {
    value: "promptPack",
    label: "Prompt Pack",
    description: "Custom domain prompts for NL authoring",
  },
  {
    value: "visualizer",
    label: "Visualizer",
    description: "Custom rendering for simulations",
  },
];

const LANGUAGES: { value: PluginLanguage; label: string }[] = [
  { value: "typescript", label: "TypeScript" },
  { value: "javascript", label: "JavaScript" },
  { value: "wasm", label: "WebAssembly" },
];

const DOMAINS = [
  { value: "PHYSICS", label: "Physics" },
  { value: "CHEMISTRY", label: "Chemistry" },
  { value: "BIOLOGY", label: "Biology" },
  { value: "MEDICINE", label: "Medicine" },
  { value: "ECONOMICS", label: "Economics" },
  { value: "CS_DISCRETE", label: "Computer Science" },
  { value: "MATH", label: "Mathematics" },
  { value: "ENGINEERING", label: "Engineering" },
  { value: "CUSTOM", label: "Custom Domain" },
];

const STEPS: { id: SubmissionStep; label: string }[] = [
  { id: "metadata", label: "Metadata" },
  { id: "domain", label: "Domain & Type" },
  { id: "bundle", label: "Bundle" },
  { id: "resources", label: "Resources" },
  { id: "documentation", label: "Documentation" },
  { id: "review", label: "Review" },
];

// =============================================================================
// Component
// =============================================================================

export const PluginSubmissionForm = ({
  onSuccess,
  onCancel,
  className = "",
}: PluginSubmissionFormProps) => {
  const {
    step,
    isSubmitting,
    isValidating,
    validationErrors,
    validationWarnings,
    result,
    // setStep available for future direct step navigation
    nextStep,
    prevStep,
    validateMetadata,
    validateBundle,
    submit,
    reset,
  } = usePluginSubmission();

  // Form state
  const [metadata, setMetadata] = useState<PluginMetadata>({
    id: "",
    name: "",
    version: "1.0.0",
    description: "",
    author: "",
    license: "MIT",
    repository: "",
    tags: [],
  });
  const [pluginType, setPluginType] = useState<PluginType>("kernel");
  const [domain, setDomain] = useState("PHYSICS");
  const [customDomain, setCustomDomain] = useState("");
  const [language, setLanguage] = useState<PluginLanguage>("typescript");
  const [bundleFile, setBundleFile] = useState<File | null>(null);
  const [bundleUrl, setBundleUrl] = useState("");
  const [sourceMapUrl, setSourceMapUrl] = useState("");
  const [documentation, setDocumentation] = useState("");
  const [maxMemoryMB, setMaxMemoryMB] = useState(128);
  const [maxExecutionTimeMs, setMaxExecutionTimeMs] = useState(5000);
  const [tagInput, setTagInput] = useState("");

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Handlers
  const handleMetadataChange = useCallback(
    (field: keyof PluginMetadata, value: string | string[]) => {
      setMetadata((prev) => ({ ...prev, [field]: value }));
    },
    []
  );

  const handleAddTag = useCallback(() => {
    if (tagInput && !metadata.tags?.includes(tagInput)) {
      setMetadata((prev) => ({
        ...prev,
        tags: [...(prev.tags || []), tagInput.toLowerCase()],
      }));
      setTagInput("");
    }
  }, [tagInput, metadata.tags]);

  const handleRemoveTag = useCallback((tag: string) => {
    setMetadata((prev) => ({
      ...prev,
      tags: prev.tags?.filter((t) => t !== tag) || [],
    }));
  }, []);

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file) {
        setBundleFile(file);
        await validateBundle(file);
      }
    },
    [validateBundle]
  );

  const handleNextStep = useCallback(async () => {
    if (step === "metadata") {
      const isValid = await validateMetadata(metadata);
      if (isValid) nextStep();
    } else if (step === "bundle") {
      if (bundleFile || bundleUrl) {
        nextStep();
      }
    } else {
      nextStep();
    }
  }, [step, metadata, bundleFile, bundleUrl, validateMetadata, nextStep]);

  const handleSubmit = useCallback(async () => {
    const data: PluginSubmissionData = {
      metadata,
      type: pluginType,
      domain: domain === "CUSTOM" ? customDomain : domain,
      language,
      bundleFile: bundleFile || undefined,
      bundleUrl: bundleUrl || undefined,
      sourceMapUrl: sourceMapUrl || undefined,
      documentation: documentation || undefined,
      resourceLimits: {
        maxMemoryMB,
        maxExecutionTimeMs,
      },
    };

    await submit(data);
  }, [
    metadata,
    pluginType,
    domain,
    customDomain,
    language,
    bundleFile,
    bundleUrl,
    sourceMapUrl,
    documentation,
    maxMemoryMB,
    maxExecutionTimeMs,
    submit,
  ]);

  const handleSuccess = useCallback(() => {
    if (result?.id && onSuccess) {
      onSuccess(result.id);
    }
    reset();
  }, [result, onSuccess, reset]);

  // Render validation errors
  const renderErrors = (errors: PluginSubmissionError[]) => (
    <div className="space-y-2 mt-4">
      {errors.map((error, i) => (
        <div
          key={i}
          className={`p-3 rounded-lg text-sm ${
            error.severity === "error"
              ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
              : "bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-400"
          }`}
        >
          <span className="font-medium">{error.code}:</span> {error.message}
        </div>
      ))}
    </div>
  );

  // Step content renderers
  const renderMetadataStep = () => (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
        Plugin Metadata
      </h2>
      <p className="text-gray-600 dark:text-gray-400">
        Provide basic information about your plugin.
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <TextField
          label="Plugin ID"
          placeholder="my-physics-kernel"
          value={metadata.id}
          onChange={(e) => handleMetadataChange("id", e.target.value)}
          description="Unique identifier (lowercase, hyphens allowed)"
        />
        <TextField
          label="Name"
          placeholder="My Physics Kernel"
          value={metadata.name}
          onChange={(e) => handleMetadataChange("name", e.target.value)}
        />
        <TextField
          label="Version"
          placeholder="1.0.0"
          value={metadata.version}
          onChange={(e) => handleMetadataChange("version", e.target.value)}
          description="Semantic version (e.g., 1.0.0)"
        />
        <TextField
          label="Author"
          placeholder="Your Name"
          value={metadata.author}
          onChange={(e) => handleMetadataChange("author", e.target.value)}
        />
      </div>

      <TextField
        label="Description"
        placeholder="A custom physics simulation kernel for..."
        value={metadata.description}
        onChange={(e) => handleMetadataChange("description", e.target.value)}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <TextField
          label="License"
          placeholder="MIT"
          value={metadata.license || ""}
          onChange={(e) => handleMetadataChange("license", e.target.value)}
        />
        <TextField
          label="Repository URL"
          placeholder="https://github.com/..."
          value={metadata.repository || ""}
          onChange={(e) => handleMetadataChange("repository", e.target.value)}
        />
      </div>

      {/* Tags */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Tags
        </label>
        <div className="flex gap-2 mb-2">
          <TextField
            placeholder="Add a tag..."
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleAddTag()}
          />
          <Button variant="outline" onClick={handleAddTag}>
            Add
          </Button>
        </div>
        <div className="flex flex-wrap gap-2">
          {metadata.tags?.map((tag) => (
            <Badge
              key={tag}
              variant="soft"
              tone="primary"
              className="cursor-pointer"
              onClick={() => handleRemoveTag(tag)}
            >
              {tag} ×
            </Badge>
          ))}
        </div>
      </div>
    </div>
  );

  const renderDomainStep = () => (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
        Domain & Type
      </h2>
      <p className="text-gray-600 dark:text-gray-400">
        Select the plugin type and target domain.
      </p>

      {/* Plugin Type */}
      <div>
        <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
          Plugin Type
        </label>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {PLUGIN_TYPES.map((type) => (
            <button
              key={type.value}
              type="button"
              onClick={() => setPluginType(type.value)}
              className={`
                p-4 rounded-lg border-2 text-left transition-colors
                ${
                  pluginType === type.value
                    ? "border-blue-500 bg-blue-50 dark:bg-blue-900/20"
                    : "border-gray-200 dark:border-gray-700 hover:border-gray-300"
                }
              `}
            >
              <div className="font-medium text-gray-900 dark:text-white">
                {type.label}
              </div>
              <div className="text-sm text-gray-500 dark:text-gray-400">
                {type.description}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Domain */}
      <Select
        label="Target Domain"
        value={domain}
        onChange={(e) => setDomain(e.target.value)}
        options={DOMAINS}
      />

      {domain === "CUSTOM" && (
        <TextField
          label="Custom Domain Name"
          placeholder="e.g., ASTRONOMY"
          value={customDomain}
          onChange={(e) => setCustomDomain(e.target.value.toUpperCase())}
        />
      )}

      {/* Language */}
      <Select
        label="Implementation Language"
        value={language}
        onChange={(e) => setLanguage(e.target.value as PluginLanguage)}
        options={LANGUAGES}
      />
    </div>
  );

  const renderBundleStep = () => (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
        Bundle Upload
      </h2>
      <p className="text-gray-600 dark:text-gray-400">
        Upload your compiled plugin bundle or provide a URL.
      </p>

      {/* File Upload */}
      <div
        className={`
          border-2 border-dashed rounded-lg p-8 text-center
          ${
            bundleFile
              ? "border-green-500 bg-green-50 dark:bg-green-900/20"
              : "border-gray-300 dark:border-gray-600"
          }
        `}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".js,.wasm"
          onChange={handleFileChange}
          className="hidden"
        />
        {bundleFile ? (
          <div>
            <div className="text-4xl mb-2">✅</div>
            <div className="font-medium text-gray-900 dark:text-white">
              {bundleFile.name}
            </div>
            <div className="text-sm text-gray-500">
              {(bundleFile.size / 1024).toFixed(1)} KB
            </div>
            <Button
              variant="outline"
              size="sm"
              className="mt-4"
              onClick={() => setBundleFile(null)}
            >
              Remove
            </Button>
          </div>
        ) : (
          <div>
            <div className="text-4xl mb-2">📦</div>
            <div className="font-medium text-gray-900 dark:text-white mb-2">
              Drag & drop your bundle here
            </div>
            <Button
              variant="outline"
              onClick={() => fileInputRef.current?.click()}
            >
              Browse Files
            </Button>
          </div>
        )}
      </div>

      <div className="text-center text-gray-500">or</div>

      {/* URL Input */}
      <TextField
        label="Bundle URL"
        placeholder="https://cdn.example.com/my-plugin.js"
        value={bundleUrl}
        onChange={(e) => setBundleUrl(e.target.value)}
        description="HTTPS URL to your hosted bundle"
      />

      <TextField
        label="Source Map URL (optional)"
        placeholder="https://cdn.example.com/my-plugin.js.map"
        value={sourceMapUrl}
        onChange={(e) => setSourceMapUrl(e.target.value)}
      />
    </div>
  );

  const renderResourcesStep = () => (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
        Resource Limits
      </h2>
      <p className="text-gray-600 dark:text-gray-400">
        Configure resource constraints for your plugin execution.
      </p>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Max Memory (MB): {maxMemoryMB}
          </label>
          <input
            type="range"
            min={16}
            max={256}
            value={maxMemoryMB}
            onChange={(e) => setMaxMemoryMB(parseInt(e.target.value))}
            className="w-full"
          />
          <div className="flex justify-between text-xs text-gray-500">
            <span>16 MB</span>
            <span>256 MB</span>
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Max Execution Time (ms): {maxExecutionTimeMs}
          </label>
          <input
            type="range"
            min={100}
            max={10000}
            step={100}
            value={maxExecutionTimeMs}
            onChange={(e) => setMaxExecutionTimeMs(parseInt(e.target.value))}
            className="w-full"
          />
          <div className="flex justify-between text-xs text-gray-500">
            <span>100 ms</span>
            <span>10,000 ms</span>
          </div>
        </div>
      </div>

      <div className="bg-blue-50 dark:bg-blue-900/20 p-4 rounded-lg">
        <h4 className="font-medium text-blue-900 dark:text-blue-100 mb-2">
          ℹ️ Resource Guidelines
        </h4>
        <ul className="text-sm text-blue-700 dark:text-blue-300 space-y-1">
          <li>• Lower limits improve performance on mobile devices</li>
          <li>• Complex simulations may need higher memory limits</li>
          <li>• Long execution times can cause UI freezes</li>
        </ul>
      </div>
    </div>
  );

  const renderDocumentationStep = () => (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
        Documentation
      </h2>
      <p className="text-gray-600 dark:text-gray-400">
        Provide usage documentation for your plugin (Markdown supported).
      </p>

      <textarea
        value={documentation}
        onChange={(e) => setDocumentation(e.target.value)}
        placeholder={`# My Plugin

## Installation

Describe how to use your plugin...

## Configuration Options

- \`option1\`: Description
- \`option2\`: Description

## Examples

\`\`\`typescript
// Example usage
\`\`\`
`}
        className="w-full h-64 p-4 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white font-mono text-sm"
      />
    </div>
  );

  const renderReviewStep = () => (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
        Review Submission
      </h2>
      <p className="text-gray-600 dark:text-gray-400">
        Please review your plugin details before submitting.
      </p>

      <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-4 space-y-4">
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <span className="text-gray-500">Plugin ID:</span>
            <span className="ml-2 font-medium">{metadata.id}</span>
          </div>
          <div>
            <span className="text-gray-500">Name:</span>
            <span className="ml-2 font-medium">{metadata.name}</span>
          </div>
          <div>
            <span className="text-gray-500">Version:</span>
            <span className="ml-2 font-medium">{metadata.version}</span>
          </div>
          <div>
            <span className="text-gray-500">Type:</span>
            <span className="ml-2 font-medium">{pluginType}</span>
          </div>
          <div>
            <span className="text-gray-500">Domain:</span>
            <span className="ml-2 font-medium">
              {domain === "CUSTOM" ? customDomain : domain}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Language:</span>
            <span className="ml-2 font-medium">{language}</span>
          </div>
          <div>
            <span className="text-gray-500">Bundle:</span>
            <span className="ml-2 font-medium">
              {bundleFile?.name || bundleUrl || "Not provided"}
            </span>
          </div>
          <div>
            <span className="text-gray-500">Memory Limit:</span>
            <span className="ml-2 font-medium">{maxMemoryMB} MB</span>
          </div>
        </div>

        <div>
          <span className="text-gray-500">Description:</span>
          <p className="mt-1 text-gray-900 dark:text-white">
            {metadata.description}
          </p>
        </div>

        {metadata.tags && metadata.tags.length > 0 && (
          <div>
            <span className="text-gray-500">Tags:</span>
            <div className="flex flex-wrap gap-2 mt-1">
              {metadata.tags.map((tag) => (
                <Badge key={tag} variant="soft" tone="neutral">
                  {tag}
                </Badge>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );

  const renderCompleteStep = () => (
    <div className="text-center py-8">
      <div className="text-6xl mb-4">🎉</div>
      <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
        Plugin Submitted!
      </h2>
      <p className="text-gray-600 dark:text-gray-400 mb-4">
        Your plugin has been submitted for review.
      </p>
      {result && (
        <div className="bg-gray-50 dark:bg-gray-800 rounded-lg p-4 inline-block text-left">
          <div className="text-sm">
            <span className="text-gray-500">Plugin ID:</span>
            <span className="ml-2 font-mono">{result.id}</span>
          </div>
          <div className="text-sm">
            <span className="text-gray-500">Status:</span>
            <Badge
              variant="soft"
              tone={result.status === "approved" ? "success" : "warning"}
              className="ml-2"
            >
              {result.status}
            </Badge>
          </div>
        </div>
      )}
      {validationWarnings.length > 0 && renderErrors(validationWarnings)}
      <div className="mt-6">
        <Button variant="solid" tone="primary" onClick={handleSuccess}>
          Done
        </Button>
      </div>
    </div>
  );

  // Get current step content
  const getStepContent = () => {
    switch (step) {
      case "metadata":
        return renderMetadataStep();
      case "domain":
        return renderDomainStep();
      case "bundle":
        return renderBundleStep();
      case "resources":
        return renderResourcesStep();
      case "documentation":
        return renderDocumentationStep();
      case "review":
        return renderReviewStep();
      case "complete":
        return renderCompleteStep();
      default:
        return null;
    }
  };

  const currentStepIndex = STEPS.findIndex((s) => s.id === step);

  return (
    <div className={`max-w-3xl mx-auto ${className}`}>
      {/* Stepper */}
      {step !== "complete" && (
        <Stepper
          steps={STEPS.map((s) => ({ key: s.id, label: s.label }))}
          activeStep={currentStepIndex}
          className="mb-8"
        />
      )}

      {/* Step Content */}
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6">
        {getStepContent()}

        {/* Validation Errors */}
        {validationErrors.length > 0 && renderErrors(validationErrors)}

        {/* Navigation */}
        {step !== "complete" && (
          <div className="flex justify-between mt-8 pt-4 border-t border-gray-200 dark:border-gray-700">
            <div>
              {currentStepIndex > 0 && (
                <Button variant="ghost" onClick={prevStep}>
                  ← Previous
                </Button>
              )}
              {onCancel && currentStepIndex === 0 && (
                <Button variant="ghost" onClick={onCancel}>
                  Cancel
                </Button>
              )}
            </div>
            <div>
              {step === "review" ? (
                <Button
                  variant="solid"
                  tone="primary"
                  onClick={handleSubmit}
                  disabled={isSubmitting}
                >
                  {isSubmitting ? "Submitting..." : "Submit Plugin"}
                </Button>
              ) : (
                <Button
                  variant="solid"
                  tone="primary"
                  onClick={handleNextStep}
                  disabled={isValidating}
                >
                  {isValidating ? "Validating..." : "Next →"}
                </Button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default PluginSubmissionForm;

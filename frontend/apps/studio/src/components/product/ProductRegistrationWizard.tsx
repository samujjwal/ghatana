/**
 * Product Registration Wizard - guided flow for registering new products.
 *
 * @doc.type component
 * @doc.purpose Guide users through product registration with step-by-step wizard
 * @doc.layer studio
 */

import React, { useState } from "react";

interface ProductRegistrationData {
  productId: string;
  productName: string;
  productKind: string;
  owner: string;
  surfaces: SurfaceConfig[];
  lifecycleProfile: string;
}

interface SurfaceConfig {
  id: string;
  type: string;
  language?: string;
  runtime?: string;
  buildSystem?: string;
  path: string;
  autoDetected?: boolean;
}

interface ProductRegistrationWizardProps {
  onComplete: (data: ProductRegistrationData) => void;
  onCancel: () => void;
}

const STEPS = [
  { id: "basic", title: "Basic Information" },
  { id: "surfaces", title: "Surface Configuration" },
  { id: "lifecycle", title: "Lifecycle Profile" },
  { id: "review", title: "Review and Create" },
] as const;

/**
 * Polyglot surface detection - detects language, runtime, and build system from path.
 */
function detectSurfaceConfig(path: string): Partial<SurfaceConfig> {
  const lowerPath = path.toLowerCase();
  
  // Detect Rust
  if (lowerPath.includes('cargo.toml') || lowerPath.endsWith('.rs')) {
    return {
      language: 'rust',
      runtime: 'rust-native',
      buildSystem: 'cargo',
      autoDetected: true,
    };
  }
  
  // Detect Python
  if (lowerPath.includes('pyproject.toml') || lowerPath.includes('setup.py') || lowerPath.endsWith('.py')) {
    return {
      language: 'python',
      runtime: 'python',
      buildSystem: 'pyproject',
      autoDetected: true,
    };
  }
  
  // Detect Java/Gradle
  if (lowerPath.includes('build.gradle') || lowerPath.includes('build.gradle.kts') || lowerPath.endsWith('.java')) {
    return {
      language: 'java',
      runtime: 'java-jre',
      buildSystem: 'gradle',
      autoDetected: true,
    };
  }
  
  // Detect TypeScript/Node
  if (lowerPath.includes('package.json') || lowerPath.includes('tsconfig.json') || lowerPath.endsWith('.ts') || lowerPath.endsWith('.tsx')) {
    return {
      language: 'typescript',
      runtime: 'nodejs',
      buildSystem: 'pnpm',
      autoDetected: true,
    };
  }
  
  // Default to TypeScript for web products
  return {
    language: 'typescript',
    runtime: 'nodejs',
    buildSystem: 'pnpm',
    autoDetected: false,
  };
}

export function ProductRegistrationWizard({ onComplete, onCancel }: ProductRegistrationWizardProps) {
  const [currentStep, setCurrentStep] = useState(0);
  const [data, setData] = useState<ProductRegistrationData>({
    productId: "",
    productName: "",
    productKind: "product",
    owner: "",
    surfaces: [],
    lifecycleProfile: "standard-web-api-product",
  });

  const canProceed = () => {
    switch (currentStep) {
      case 0:
        return data.productId && data.productName && data.owner;
      case 1:
        return data.surfaces.length > 0;
      case 2:
        return data.lifecycleProfile;
      default:
        return true;
    }
  };

  const handleNext = () => {
    if (currentStep < STEPS.length - 1) {
      setCurrentStep(currentStep + 1);
    } else {
      onComplete(data);
    }
  };

  const handleBack = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  const addSurface = () => {
    const newSurface: SurfaceConfig = {
      id: `${data.productId}-surface-${data.surfaces.length + 1}`,
      type: "backend-api",
      language: "typescript",
      runtime: "nodejs",
      buildSystem: "pnpm",
      path: `products/${data.productId}`,
    };
    setData({ ...data, surfaces: [...data.surfaces, newSurface] });
  };

  const removeSurface = (index: number) => {
    setData({ ...data, surfaces: data.surfaces.filter((_, i) => i !== index) });
  };

  const updateSurface = (index: number, field: keyof SurfaceConfig, value: string) => {
    const updatedSurfaces = [...data.surfaces];
    updatedSurfaces[index] = { ...updatedSurfaces[index], [field]: value };
    
    // Auto-detect language, runtime, and build system when path changes
    if (field === 'path') {
      const detected = detectSurfaceConfig(value);
      updatedSurfaces[index] = { ...updatedSurfaces[index], ...detected };
    }
    
    setData({ ...data, surfaces: updatedSurfaces });
  };

  return (
    <div className="product-registration-wizard">
      <div className="wizard-header">
        <h2>Register New Product</h2>
        <div className="step-indicator">
          {STEPS.map((step, index) => (
            <div
              key={step.id}
              className={`step ${index <= currentStep ? "active" : ""} ${index < currentStep ? "completed" : ""}`}
            >
              <span className="step-number">{index + 1}</span>
              <span className="step-title">{step.title}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="wizard-content">
        {currentStep === 0 && <BasicInfoStep data={data} setData={setData} />}
        {currentStep === 1 && (
          <SurfaceConfigStep
            surfaces={data.surfaces}
            onAdd={addSurface}
            onRemove={removeSurface}
            onUpdate={updateSurface}
          />
        )}
        {currentStep === 2 && <LifecycleProfileStep data={data} setData={setData} />}
        {currentStep === 3 && <ReviewStep data={data} />}
      </div>

      <div className="wizard-footer">
        <button onClick={onCancel} className="cancel-button">
          Cancel
        </button>
        {currentStep > 0 && (
          <button onClick={handleBack} className="back-button">
            Back
          </button>
        )}
        <button
          onClick={handleNext}
          className="next-button"
          disabled={!canProceed()}
        >
          {currentStep === STEPS.length - 1 ? "Create Product" : "Next"}
        </button>
      </div>
    </div>
  );
}

function BasicInfoStep({ data, setData }: { data: ProductRegistrationData; setData: (d: ProductRegistrationData) => void }) {
  return (
    <div className="basic-info-step">
      <h3>Basic Information</h3>
      <div className="form-field">
        <label htmlFor="productId">Product ID</label>
        <input
          id="productId"
          type="text"
          value={data.productId}
          onChange={(e) => setData({ ...data, productId: e.target.value })}
          placeholder="e.g., my-awesome-product"
        />
        <small className="field-hint">Unique identifier for the product (kebab-case)</small>
      </div>
      <div className="form-field">
        <label htmlFor="productName">Product Name</label>
        <input
          id="productName"
          type="text"
          value={data.productName}
          onChange={(e) => setData({ ...data, productName: e.target.value })}
          placeholder="e.g., My Awesome Product"
        />
        <small className="field-hint">Human-readable name for the product</small>
      </div>
      <div className="form-field">
        <label htmlFor="owner">Owner</label>
        <input
          id="owner"
          type="text"
          value={data.owner}
          onChange={(e) => setData({ ...data, owner: e.target.value })}
          placeholder="e.g., My Team"
        />
        <small className="field-hint">Team or individual responsible for the product</small>
      </div>
    </div>
  );
}

function SurfaceConfigStep({
  surfaces,
  onAdd,
  onRemove,
  onUpdate,
}: {
  surfaces: SurfaceConfig[];
  onAdd: () => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, field: keyof SurfaceConfig, value: string) => void;
}) {
  return (
    <div className="surface-config-step">
      <h3>Surface Configuration</h3>
      {surfaces.map((surface, index) => (
        <div key={index} className="surface-item">
          <div className="surface-header">
            <span className="surface-id">Surface #{index + 1}</span>
            <button onClick={() => onRemove(index)} className="remove-button">
              Remove
            </button>
          </div>
          <div className="surface-fields">
            <div className="form-field">
              <label>Type</label>
              <select
                value={surface.type}
                onChange={(e) => onUpdate(index, "type", e.target.value)}
              >
                <option value="backend-api">Backend API</option>
                <option value="web">Web Application</option>
                <option value="sdk">SDK/Library</option>
                <option value="worker">Worker</option>
              </select>
            </div>
            <div className="form-field">
              <label>Language</label>
              <select
                value={surface.language || ""}
                onChange={(e) => onUpdate(index, "language", e.target.value)}
              >
                <option value="">Select language</option>
                <option value="typescript">TypeScript</option>
                <option value="java">Java</option>
                <option value="rust">Rust</option>
                <option value="python">Python</option>
              </select>
            </div>
            <div className="form-field">
              <label>Runtime</label>
              <select
                value={surface.runtime || ""}
                onChange={(e) => onUpdate(index, "runtime", e.target.value)}
              >
                <option value="">Select runtime</option>
                <option value="nodejs">Node.js</option>
                <option value="java-jre">Java JRE</option>
                <option value="rust-native">Rust Native</option>
                <option value="python">Python</option>
              </select>
            </div>
            <div className="form-field">
              <label>Build System</label>
              <select
                value={surface.buildSystem || ""}
                onChange={(e) => onUpdate(index, "buildSystem", e.target.value)}
              >
                <option value="">Select build system</option>
                <option value="pnpm">pnpm</option>
                <option value="gradle">Gradle</option>
                <option value="cargo">Cargo</option>
                <option value="pyproject">pyproject</option>
              </select>
            </div>
            <div className="form-field">
              <label>Path</label>
              <input
                type="text"
                value={surface.path}
                onChange={(e) => onUpdate(index, "path", e.target.value)}
              />
            </div>
          </div>
        </div>
      ))}
      <button onClick={onAdd} className="add-surface-button">
        + Add Surface
      </button>
    </div>
  );
}

function LifecycleProfileStep({ data, setData }: { data: ProductRegistrationData; setData: (d: ProductRegistrationData) => void }) {
  return (
    <div className="lifecycle-profile-step">
      <h3>Lifecycle Profile</h3>
      <div className="form-field">
        <label htmlFor="lifecycleProfile">Profile</label>
        <select
          id="lifecycleProfile"
          value={data.lifecycleProfile}
          onChange={(e) => setData({ ...data, lifecycleProfile: e.target.value })}
        >
          <option value="standard-web-api-product">Standard Web API Product</option>
          <option value="backend-only-java-service">Backend-only Java Service</option>
          <option value="standard-polyglot-product">Standard Polyglot Product</option>
          <option value="fast-gate-profile">Fast Gate Profile</option>
          <option value="focused-gate-profile">Focused Gate Profile</option>
          <option value="nightly-gate-profile">Nightly Gate Profile</option>
          <option value="release-gate-profile">Release Gate Profile</option>
        </select>
        <small className="field-hint">Lifecycle profile determines default gates and adapters</small>
      </div>
    </div>
  );
}

function ReviewStep({ data }: { data: ProductRegistrationData }) {
  return (
    <div className="review-step">
      <h3>Review and Create</h3>
      <div className="review-section">
        <h4>Basic Information</h4>
        <div className="review-item">
          <span className="label">Product ID:</span>
          <span className="value">{data.productId}</span>
        </div>
        <div className="review-item">
          <span className="label">Product Name:</span>
          <span className="value">{data.productName}</span>
        </div>
        <div className="review-item">
          <span className="label">Owner:</span>
          <span className="value">{data.owner}</span>
        </div>
      </div>
      <div className="review-section">
        <h4>Surfaces ({data.surfaces.length})</h4>
        {data.surfaces.map((surface, index) => (
          <div key={index} className="review-item surface-review">
            <span className="label">{surface.id}</span>
            <span className="value">
              {surface.type} | {surface.language} | {surface.runtime} | {surface.buildSystem}
            </span>
          </div>
        ))}
      </div>
      <div className="review-section">
        <h4>Lifecycle Profile</h4>
        <div className="review-item">
          <span className="label">Profile:</span>
          <span className="value">{data.lifecycleProfile}</span>
        </div>
      </div>
    </div>
  );
}

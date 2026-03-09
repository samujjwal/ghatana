/**
 * Canvas Code Generation Hook
 * 
 * React hook for generating code from canvas designs.
 * Integrates GenerationAgent with React components.
 * 
 * @doc.type hook
 * @doc.purpose Code generation from canvas
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import type { CanvasState } from '../../../components/canvas/workspace/canvasAtoms';
import type { LifecyclePhase } from '../../../types/lifecycle';
import type { ValidationReport } from '../agents/ValidationAgent';
import {
    GenerationAgent,
    type GenerationOptions,
    type CodeGenerationResult,
    type GeneratedArtifact,
    type ArtifactType,
} from '../agents/GenerationAgent';

// ============================================================================
// Hook Options
// ============================================================================

export interface UseCodeGenerationOptions {
    /** Canvas state to generate from */
    canvasState: CanvasState;

    /** Current lifecycle phase */
    lifecyclePhase: LifecyclePhase;

    /** Validation report (optional but recommended) */
    validationReport?: ValidationReport | null;

    /** Enable auto-generation */
    enabled?: boolean;

    /** Generation options */
    generationOptions?: Partial<GenerationOptions>;
}

// ============================================================================
// Hook Return Type
// ============================================================================

export interface UseCodeGenerationReturn {
    /** Generate code */
    generate: (options?: Partial<GenerationOptions>) => Promise<void>;

    /** Generation state */
    isGenerating: boolean;
    generationResult: CodeGenerationResult | null;

    /** Download generated files */
    downloadArtifacts: () => void;
    downloadArtifact: (artifactId: string) => void;
    downloadAsZip: () => Promise<void>;

    /** Preview artifacts */
    previewArtifact: (artifactId: string) => void;
    previewedArtifact: GeneratedArtifact | null;

    /** Filter artifacts */
    getArtifactsByType: (type: ArtifactType) => GeneratedArtifact[];
    getArtifactsByLanguage: (language: string) => GeneratedArtifact[];

    /** Copy to clipboard */
    copyArtifactToClipboard: (artifactId: string) => Promise<void>;

    /** Generation status */
    canGenerate: boolean;
    generationErrors: string[];
    generationWarnings: string[];
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * useCodeGeneration Hook
 * 
 * Provides code generation capabilities with preview, download, and export.
 * 
 * @example
 * ```tsx
 * const {
 *   generate,
 *   isGenerating,
 *   generationResult,
 *   downloadAsZip,
 * } = useCodeGeneration({
 *   canvasState,
 *   lifecyclePhase,
 *   validationReport,
 *   enabled: true,
 * });
 * 
 * // Generate code
 * await generate({ language: 'typescript', includeTests: true });
 * 
 * // Download as ZIP
 * await downloadAsZip();
 * ```
 */
export function useCodeGeneration(options: UseCodeGenerationOptions): UseCodeGenerationReturn {
    const {
        canvasState,
        lifecyclePhase,
        validationReport = null,
        enabled = true,
        generationOptions = {},
    } = options;

    // State
    const [isGenerating, setIsGenerating] = useState(false);
    const [generationResult, setGenerationResult] = useState<CodeGenerationResult | null>(null);
    const [previewedArtifact, setPreviewedArtifact] = useState<GeneratedArtifact | null>(null);

    // Agent instance
    const agent = useMemo(() => new GenerationAgent(), []);

    /**
     * Check if generation is possible
     */
    const canGenerate = useMemo(() => {
        if (!enabled) return false;
        if (!canvasState || !canvasState.elements || canvasState.elements.length === 0) {
            return false;
        }

        // Check validation errors if report exists
        if (validationReport) {
            const errors = validationReport.issues.filter(i => i.severity === 'error');
            if (errors.length > 0) {
                return false;
            }
        }

        return true;
    }, [enabled, canvasState, validationReport]);

    /**
     * Get generation errors/warnings
     */
    const generationErrors = useMemo(() => {
        if (!enabled) return ['Code generation is disabled'];
        if (!canvasState || !canvasState.elements || canvasState.elements.length === 0) {
            return ['Canvas is empty'];
        }
        if (validationReport) {
            const errors = validationReport.issues.filter(i => i.severity === 'error');
            if (errors.length > 0) {
                return [`Fix ${errors.length} validation errors before generating code`];
            }
        }
        return generationResult?.errors || [];
    }, [enabled, canvasState, validationReport, generationResult]);

    const generationWarnings = useMemo(() => {
        return generationResult?.warnings || [];
    }, [generationResult]);

    /**
     * Generate code from canvas
     */
    const generate = useCallback(async (overrideOptions: Partial<GenerationOptions> = {}) => {
        if (!canGenerate) {
            console.warn('[useCodeGeneration] Cannot generate - preconditions not met');
            return;
        }

        setIsGenerating(true);
        try {
            const options: GenerationOptions = {
                language: 'typescript',
                includeTests: true,
                includeDocumentation: true,
                includeConfiguration: true,
                validationReport: validationReport || undefined,
                ...generationOptions,
                ...overrideOptions,
            };

            const result = await agent.generate(canvasState, lifecyclePhase, options);
            setGenerationResult(result);

            if (result.success) {
                console.log(`[useCodeGeneration] Generated ${result.artifacts.length} artifacts`);
            } else {
                console.error('[useCodeGeneration] Generation failed:', result.errors);
            }
        } catch (error) {
            console.error('[useCodeGeneration] Generation error:', error);
            setGenerationResult({
                success: false,
                artifacts: [],
                summary: 'Generation failed',
                statistics: {
                    totalFiles: 0,
                    totalLines: 0,
                    byType: {} as unknown,
                    byLanguage: {},
                },
                errors: [error instanceof Error ? error.message : 'Unknown error'],
                warnings: [],
            });
        } finally {
            setIsGenerating(false);
        }
    }, [canGenerate, canvasState, lifecyclePhase, validationReport, generationOptions, agent]);

    /**
     * Download all artifacts as individual files
     */
    const downloadArtifacts = useCallback(() => {
        if (!generationResult || generationResult.artifacts.length === 0) {
            console.warn('[useCodeGeneration] No artifacts to download');
            return;
        }

        for (const artifact of generationResult.artifacts) {
            downloadArtifact(artifact.id);
        }
    }, [generationResult]);

    /**
     * Download single artifact
     */
    const downloadArtifact = useCallback((artifactId: string) => {
        if (!generationResult) return;

        const artifact = generationResult.artifacts.find(a => a.id === artifactId);
        if (!artifact) {
            console.warn(`[useCodeGeneration] Artifact ${artifactId} not found`);
            return;
        }

        const blob = new Blob([artifact.content], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = artifact.path.split('/').pop() || 'file.txt';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }, [generationResult]);

    /**
     * Download all artifacts as ZIP
     */
    const downloadAsZip = useCallback(async () => {
        if (!generationResult || generationResult.artifacts.length === 0) {
            console.warn('[useCodeGeneration] No artifacts to download');
            return;
        }

        // Dynamic import JSZip to avoid bundle size
        const JSZip = (await import('jszip')).default;
        const zip = new JSZip();

        // Add each artifact to ZIP
        for (const artifact of generationResult.artifacts) {
            zip.file(artifact.path, artifact.content);
        }

        // Generate ZIP blob
        const blob = await zip.generateAsync({ type: 'blob' });

        // Download ZIP
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'generated-code.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }, [generationResult]);

    /**
     * Preview artifact
     */
    const previewArtifact = useCallback((artifactId: string) => {
        if (!generationResult) return;

        const artifact = generationResult.artifacts.find(a => a.id === artifactId);
        if (artifact) {
            setPreviewedArtifact(artifact);
        }
    }, [generationResult]);

    /**
     * Get artifacts by type
     */
    const getArtifactsByType = useCallback((type: ArtifactType): GeneratedArtifact[] => {
        if (!generationResult) return [];
        return generationResult.artifacts.filter(a => a.type === type);
    }, [generationResult]);

    /**
     * Get artifacts by language
     */
    const getArtifactsByLanguage = useCallback((language: string): GeneratedArtifact[] => {
        if (!generationResult) return [];
        return generationResult.artifacts.filter(a => a.language === language);
    }, [generationResult]);

    /**
     * Copy artifact to clipboard
     */
    const copyArtifactToClipboard = useCallback(async (artifactId: string) => {
        if (!generationResult) return;

        const artifact = generationResult.artifacts.find(a => a.id === artifactId);
        if (!artifact) {
            console.warn(`[useCodeGeneration] Artifact ${artifactId} not found`);
            return;
        }

        try {
            await navigator.clipboard.writeText(artifact.content);
            console.log(`[useCodeGeneration] Copied ${artifact.path} to clipboard`);
        } catch (error) {
            console.error('[useCodeGeneration] Failed to copy to clipboard:', error);
        }
    }, [generationResult]);

    return {
        generate,
        isGenerating,
        generationResult,
        downloadArtifacts,
        downloadArtifact,
        downloadAsZip,
        previewArtifact,
        previewedArtifact,
        getArtifactsByType,
        getArtifactsByLanguage,
        copyArtifactToClipboard,
        canGenerate,
        generationErrors,
        generationWarnings,
    };
}

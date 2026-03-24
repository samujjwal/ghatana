/**
 * @doc.type hook
 * @doc.purpose Code review mode hook for Journey 18.1 (Engineering Lead - Code Review Mode)
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Review status types
 */
export type ReviewStatus = 'pending' | 'approved' | 'changes-requested' | 'commented';

/**
 * Annotation severity
 */
export type AnnotationSeverity = 'error' | 'warning' | 'info' | 'suggestion';

/**
 * Annotation interface
 */
export interface Annotation {
    id: string;
    fileId: string;
    filePath: string;
    lineNumber: number;
    columnNumber?: number;
    severity: AnnotationSeverity;
    message: string;
    author: string;
    createdAt: Date;
    resolved: boolean;
    replies?: AnnotationReply[];
}

/**
 * Annotation reply interface
 */
export interface AnnotationReply {
    id: string;
    author: string;
    message: string;
    createdAt: Date;
}

/**
 * File under review
 */
export interface ReviewFile {
    id: string;
    path: string;
    content: string;
    language: string;
    changeType: 'added' | 'modified' | 'deleted';
    additions: number;
    deletions: number;
    annotations: Annotation[];
}

/**
 * Review decision
 */
export interface ReviewDecision {
    status: ReviewStatus;
    reviewer: string;
    comment: string;
    timestamp: Date;
}

/**
 * Static analysis result
 */
export interface StaticAnalysisResult {
    fileId: string;
    complexity: {
        cyclomatic: number;
        cognitive: number;
        maintainabilityIndex: number;
    };
    issues: {
        errors: number;
        warnings: number;
        info: number;
    };
    metrics: {
        linesOfCode: number;
        commentDensity: number;
        duplicationPercentage: number;
    };
    hotspots: {
        lineNumber: number;
        type: 'complexity' | 'duplication' | 'security';
        message: string;
    }[];
}

/**
 * Hook configuration
 */
export interface UseReviewModeConfig {
    /**
     * Reviewer name
     */
    reviewer: string;

    /**
     * Enable static analysis
     */
    enableStaticAnalysis?: boolean;

    /**
     * Auto-save annotations
     */
    autoSave?: boolean;

    /**
     * Maximum allowed complexity
     */
    maxComplexity?: number;

    /**
     * Maximum allowed duplication
     */
    maxDuplication?: number;
}

/**
 * Hook return value
 */
export interface UseReviewModeResult {
    // Files
    files: ReviewFile[];
    selectedFile: ReviewFile | null;
    selectFile: (fileId: string) => void;
    addFile: (file: Omit<ReviewFile, 'id' | 'annotations'>) => string;
    updateFile: (fileId: string, updates: Partial<ReviewFile>) => void;
    deleteFile: (fileId: string) => void;

    // Annotations
    annotations: Annotation[];
    addAnnotation: (annotation: Omit<Annotation, 'id' | 'createdAt' | 'resolved' | 'replies'>) => string;
    updateAnnotation: (annotationId: string, updates: Partial<Annotation>) => void;
    deleteAnnotation: (annotationId: string) => void;
    resolveAnnotation: (annotationId: string) => void;
    addReply: (annotationId: string, message: string) => void;
    getAnnotationsForFile: (fileId: string) => Annotation[];
    getUnresolvedAnnotations: () => Annotation[];

    // Review decisions
    decision: ReviewDecision | null;
    approve: (comment: string) => void;
    requestChanges: (comment: string) => void;
    comment: (comment: string) => void;
    clearDecision: () => void;

    // Static analysis
    analysisResults: Map<string, StaticAnalysisResult>;
    runStaticAnalysis: (fileId: string) => Promise<StaticAnalysisResult>;
    getAnalysisForFile: (fileId: string) => StaticAnalysisResult | undefined;

    // Review state
    reviewStatus: ReviewStatus;
    canApprove: boolean;
    stats: {
        totalFiles: number;
        filesReviewed: number;
        totalAnnotations: number;
        unresolvedAnnotations: number;
        criticalIssues: number;
    };

    // Actions
    reset: () => void;
    exportReview: () => string;
    importReview: (data: string) => void;
}

/**
 * Code Review Mode Hook
 * 
 * Provides comprehensive code review functionality with annotation management,
 * approval workflow, and static analysis integration.
 * 
 * @example
 * ```tsx
 * const {
 *   files,
 *   selectedFile,
 *   selectFile,
 *   addAnnotation,
 *   approve,
 *   requestChanges,
 * } = useReviewMode({ reviewer: 'john.doe@example.com' });
 * ```
 */
export const useReviewMode = (config: UseReviewModeConfig): UseReviewModeResult => {
    const {
        reviewer,
        enableStaticAnalysis = true,
        autoSave = false,
        maxComplexity = 15,
        maxDuplication = 5,
    } = config;

    const [files, setFiles] = useState<ReviewFile[]>([]);
    const [selectedFileId, setSelectedFileId] = useState<string | null>(null);
    const [annotations, setAnnotations] = useState<Annotation[]>([]);
    const [decision, setDecision] = useState<ReviewDecision | null>(null);
    const [analysisResults, setAnalysisResults] = useState<Map<string, StaticAnalysisResult>>(new Map());

    /**
     * Get selected file
     */
    const selectedFile = useMemo(() => {
        return files.find(f => f.id === selectedFileId) || null;
    }, [files, selectedFileId]);

    /**
     * Select file
     */
    const selectFile = useCallback((fileId: string) => {
        setSelectedFileId(fileId);
    }, []);

    /**
     * Add file
     */
    const addFile = useCallback((file: Omit<ReviewFile, 'id' | 'annotations'>) => {
        const id = `file-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newFile: ReviewFile = {
            ...file,
            id,
            annotations: [],
        };

        setFiles(prev => [...prev, newFile]);

        // Auto-run static analysis if enabled
        if (enableStaticAnalysis) {
            runStaticAnalysis(id);
        }

        return id;
    }, [enableStaticAnalysis]);

    /**
     * Update file
     */
    const updateFile = useCallback((fileId: string, updates: Partial<ReviewFile>) => {
        setFiles(prev =>
            prev.map(file =>
                file.id === fileId ? { ...file, ...updates } : file
            )
        );
    }, []);

    /**
     * Delete file
     */
    const deleteFile = useCallback((fileId: string) => {
        setFiles(prev => prev.filter(f => f.id !== fileId));
        setAnnotations(prev => prev.filter(a => a.fileId !== fileId));
        setAnalysisResults(prev => {
            const newMap = new Map(prev);
            newMap.delete(fileId);
            return newMap;
        });

        if (selectedFileId === fileId) {
            setSelectedFileId(null);
        }
    }, [selectedFileId]);

    /**
     * Add annotation
     */
    const addAnnotation = useCallback((
        annotation: Omit<Annotation, 'id' | 'createdAt' | 'resolved' | 'replies'>
    ) => {
        const id = `annotation-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newAnnotation: Annotation = {
            ...annotation,
            id,
            createdAt: new Date(),
            resolved: false,
            replies: [],
        };

        setAnnotations(prev => [...prev, newAnnotation]);

        // Update file's annotations array
        setFiles(prev =>
            prev.map(file =>
                file.id === annotation.fileId
                    ? { ...file, annotations: [...file.annotations, newAnnotation] }
                    : file
            )
        );

        return id;
    }, []);

    /**
     * Update annotation
     */
    const updateAnnotation = useCallback((annotationId: string, updates: Partial<Annotation>) => {
        setAnnotations(prev =>
            prev.map(annotation =>
                annotation.id === annotationId ? { ...annotation, ...updates } : annotation
            )
        );

        // Update file's annotations array
        setFiles(prev =>
            prev.map(file => ({
                ...file,
                annotations: file.annotations.map(a =>
                    a.id === annotationId ? { ...a, ...updates } : a
                ),
            }))
        );
    }, []);

    /**
     * Delete annotation
     */
    const deleteAnnotation = useCallback((annotationId: string) => {
        const annotation = annotations.find(a => a.id === annotationId);
        if (!annotation) return;

        setAnnotations(prev => prev.filter(a => a.id !== annotationId));

        // Update file's annotations array
        setFiles(prev =>
            prev.map(file =>
                file.id === annotation.fileId
                    ? { ...file, annotations: file.annotations.filter(a => a.id !== annotationId) }
                    : file
            )
        );
    }, [annotations]);

    /**
     * Resolve annotation
     */
    const resolveAnnotation = useCallback((annotationId: string) => {
        updateAnnotation(annotationId, { resolved: true });
    }, [updateAnnotation]);

    /**
     * Add reply to annotation
     */
    const addReply = useCallback((annotationId: string, message: string) => {
        const annotation = annotations.find(a => a.id === annotationId);
        if (!annotation) return;

        const reply: AnnotationReply = {
            id: `reply-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            author: reviewer,
            message,
            createdAt: new Date(),
        };

        updateAnnotation(annotationId, {
            replies: [...(annotation.replies || []), reply],
        });
    }, [annotations, reviewer, updateAnnotation]);

    /**
     * Get annotations for file
     */
    const getAnnotationsForFile = useCallback((fileId: string) => {
        return annotations.filter(a => a.fileId === fileId);
    }, [annotations]);

    /**
     * Get unresolved annotations
     */
    const getUnresolvedAnnotations = useCallback(() => {
        return annotations.filter(a => !a.resolved);
    }, [annotations]);

    /**
     * Approve review
     */
    const approve = useCallback((comment: string) => {
        setDecision({
            status: 'approved',
            reviewer,
            comment,
            timestamp: new Date(),
        });
    }, [reviewer]);

    /**
     * Request changes
     */
    const requestChanges = useCallback((comment: string) => {
        setDecision({
            status: 'changes-requested',
            reviewer,
            comment,
            timestamp: new Date(),
        });
    }, [reviewer]);

    /**
     * Add comment without approval
     */
    const comment = useCallback((commentText: string) => {
        setDecision({
            status: 'commented',
            reviewer,
            comment: commentText,
            timestamp: new Date(),
        });
    }, [reviewer]);

    /**
     * Clear decision
     */
    const clearDecision = useCallback(() => {
        setDecision(null);
    }, []);

    /**
     * Run static analysis on file
     */
    const runStaticAnalysis = useCallback(async (fileId: string): Promise<StaticAnalysisResult> => {
        const file = files.find(f => f.id === fileId);
        if (!file) {
            throw new Error(`File not found: ${fileId}`);
        }

        // Simulate static analysis (in real implementation, call StaticAnalysisClient)
        await new Promise(resolve => setTimeout(resolve, 500));

        const lines = file.content.split('\n');
        const linesOfCode = lines.filter(line => line.trim().length > 0).length;
        const commentLines = lines.filter(line => line.trim().startsWith('//')).length;

        // Calculate cyclomatic complexity (simplified)
        const ifCount = (file.content.match(/\bif\b/g) || []).length;
        const forCount = (file.content.match(/\bfor\b/g) || []).length;
        const whileCount = (file.content.match(/\bwhile\b/g) || []).length;
        const caseCount = (file.content.match(/\bcase\b/g) || []).length;
        const cyclomaticComplexity = 1 + ifCount + forCount + whileCount + caseCount;

        const result: StaticAnalysisResult = {
            fileId,
            complexity: {
                cyclomatic: cyclomaticComplexity,
                cognitive: Math.min(cyclomaticComplexity * 1.2, 50),
                maintainabilityIndex: Math.max(100 - (cyclomaticComplexity * 2), 0),
            },
            issues: {
                errors: cyclomaticComplexity > maxComplexity ? 1 : 0,
                warnings: cyclomaticComplexity > maxComplexity * 0.8 ? 1 : 0,
                info: 0,
            },
            metrics: {
                linesOfCode,
                commentDensity: linesOfCode > 0 ? (commentLines / linesOfCode) * 100 : 0,
                duplicationPercentage: 0, // Would require more sophisticated analysis
            },
            hotspots: [],
        };

        // Add hotspot if complexity is high
        if (cyclomaticComplexity > maxComplexity) {
            result.hotspots.push({
                lineNumber: 1,
                type: 'complexity',
                message: `High cyclomatic complexity: ${cyclomaticComplexity} (max: ${maxComplexity})`,
            });
        }

        setAnalysisResults(prev => new Map(prev).set(fileId, result));

        return result;
    }, [files, maxComplexity]);

    /**
     * Get analysis for file
     */
    const getAnalysisForFile = useCallback((fileId: string) => {
        return analysisResults.get(fileId);
    }, [analysisResults]);

    /**
     * Review status
     */
    const reviewStatus: ReviewStatus = decision?.status || 'pending';

    /**
     * Can approve (all unresolved errors must be addressed)
     */
    const canApprove = useMemo(() => {
        const unresolvedErrors = annotations.filter(
            a => !a.resolved && a.severity === 'error'
        );
        return unresolvedErrors.length === 0;
    }, [annotations]);

    /**
     * Review statistics
     */
    const stats = useMemo(() => {
        const totalFiles = files.length;
        const filesReviewed = files.filter(f => f.annotations.length > 0).length;
        const totalAnnotations = annotations.length;
        const unresolvedAnnotations = annotations.filter(a => !a.resolved).length;
        const criticalIssues = annotations.filter(a => !a.resolved && a.severity === 'error').length;

        return {
            totalFiles,
            filesReviewed,
            totalAnnotations,
            unresolvedAnnotations,
            criticalIssues,
        };
    }, [files, annotations]);

    /**
     * Reset review state
     */
    const reset = useCallback(() => {
        setFiles([]);
        setSelectedFileId(null);
        setAnnotations([]);
        setDecision(null);
        setAnalysisResults(new Map());
    }, []);

    /**
     * Export review as JSON
     */
    const exportReview = useCallback(() => {
        const exportData = {
            reviewer,
            files,
            annotations,
            decision,
            analysisResults: Array.from(analysisResults.entries()),
            exportedAt: new Date().toISOString(),
        };

        return JSON.stringify(exportData, null, 2);
    }, [reviewer, files, annotations, decision, analysisResults]);

    /**
     * Import review from JSON
     */
    const importReview = useCallback((data: string) => {
        try {
            const parsed = JSON.parse(data);

            if (parsed.files) {
                setFiles(parsed.files);
            }
            if (parsed.annotations) {
                setAnnotations(parsed.annotations.map((a: unknown) => ({
                    ...a,
                    createdAt: new Date(a.createdAt),
                })));
            }
            if (parsed.decision) {
                setDecision({
                    ...parsed.decision,
                    timestamp: new Date(parsed.decision.timestamp),
                });
            }
            if (parsed.analysisResults) {
                setAnalysisResults(new Map(parsed.analysisResults));
            }
        } catch (error) {
            console.error('Failed to import review:', error);
            throw new Error('Invalid review data format');
        }
    }, []);

    return {
        // Files
        files,
        selectedFile,
        selectFile,
        addFile,
        updateFile,
        deleteFile,

        // Annotations
        annotations,
        addAnnotation,
        updateAnnotation,
        deleteAnnotation,
        resolveAnnotation,
        addReply,
        getAnnotationsForFile,
        getUnresolvedAnnotations,

        // Review decisions
        decision,
        approve,
        requestChanges,
        comment,
        clearDecision,

        // Static analysis
        analysisResults,
        runStaticAnalysis,
        getAnalysisForFile,

        // Review state
        reviewStatus,
        canApprove,
        stats,

        // Actions
        reset,
        exportReview,
        importReview,
    };
};

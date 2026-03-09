import React, { useState, useRef } from 'react';
import { RoleInheritanceTree } from '../RoleInheritanceTree';
import { RoleDefinition } from '@/types/role';

/**
 * Export/Import Demo
 * 
 * Demonstrates data portability with export/import functionality
 */
export const ExportImportDemo: React.FC = () => {
    const [personaId] = useState('export-import-demo');
    const [exportFormat, setExportFormat] = useState<'json' | 'csv'>('json');
    const [importData, setImportData] = useState<string>('');
    const [previewData, setPreviewData] = useState<any>(null);
    const [validationErrors, setValidationErrors] = useState<string[]>([]);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleExport = (data: { nodes: any[]; edges: any[] }) => {
        if (exportFormat === 'json') {
            const json = JSON.stringify(data, null, 2);
            const blob = new Blob([json], { type: 'application/json' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `role-hierarchy-${Date.now()}.json`;
            a.click();
            URL.revokeObjectURL(url);
        } else {
            // CSV format
            const csv = convertToCSV(data);
            const blob = new Blob([csv], { type: 'text/csv' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `role-hierarchy-${Date.now()}.csv`;
            a.click();
            URL.revokeObjectURL(url);
        }
    };

    const convertToCSV = (data: { nodes: any[]; edges: any[] }) => {
        const headers = ['ID', 'Name', 'Permissions', 'Parent Roles'];
        const rows = data.nodes.map((node) => [
            node.id,
            node.data?.label || '',
            node.data?.permissions?.join('; ') || '',
            node.data?.parentRoles?.join('; ') || '',
        ]);

        return [
            headers.join(','),
            ...rows.map((row) => row.map((cell) => `"${cell}"`).join(',')),
        ].join('\n');
    };

    const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file) return;

        const reader = new FileReader();
        reader.onload = (e) => {
            const content = e.target?.result as string;
            setImportData(content);
            validateImport(content);
        };
        reader.readAsText(file);
    };

    const validateImport = (data: string) => {
        const errors: string[] = [];

        try {
            const parsed = JSON.parse(data);

            if (!parsed.nodes || !Array.isArray(parsed.nodes)) {
                errors.push('Missing or invalid "nodes" array');
            }

            if (!parsed.edges || !Array.isArray(parsed.edges)) {
                errors.push('Missing or invalid "edges" array');
            }

            if (parsed.nodes && parsed.nodes.length === 0) {
                errors.push('No nodes found in import data');
            }

            // Validate node structure
            parsed.nodes?.forEach((node: any, idx: number) => {
                if (!node.id) {
                    errors.push(`Node ${idx} missing required "id" field`);
                }
            });

            if (errors.length === 0) {
                setPreviewData(parsed);
            }
        } catch (err) {
            errors.push('Invalid JSON format');
        }

        setValidationErrors(errors);
    };

    const handleImport = () => {
        if (validationErrors.length > 0) {
            alert('Please fix validation errors before importing');
            return;
        }

        // In real implementation, this would update the role hierarchy
        alert('Import successful! (Demo mode - no actual changes made)');
        setImportData('');
        setPreviewData(null);
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 dark:bg-slate-900 p-8">
            <div className="max-w-7xl mx-auto">
                {/* Header */}
                <div className="mb-8">
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                        Export / Import Demo
                    </h1>
                    <p className="text-slate-600 dark:text-neutral-400">
                        Export role hierarchies or import from external sources
                    </p>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    {/* Left: Tree & Export */}
                    <div className="space-y-6">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-xl font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                📤 Export Hierarchy
                            </h2>

                            {/* Format Selection */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Export Format
                                </label>
                                <div className="flex space-x-4">
                                    <button
                                        onClick={() => setExportFormat('json')}
                                        className={`flex-1 py-2 px-4 rounded-lg border-2 transition-colors ${exportFormat === 'json'
                                                ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 text-blue-700 dark:text-blue-300'
                                                : 'border-slate-300 dark:border-neutral-600 hover:border-slate-400'
                                            }`}
                                    >
                                        JSON
                                    </button>
                                    <button
                                        onClick={() => setExportFormat('csv')}
                                        className={`flex-1 py-2 px-4 rounded-lg border-2 transition-colors ${exportFormat === 'csv'
                                                ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30 text-blue-700 dark:text-blue-300'
                                                : 'border-slate-300 dark:border-neutral-600 hover:border-slate-400'
                                            }`}
                                    >
                                        CSV
                                    </button>
                                </div>
                            </div>

                            {/* Tree Visualization */}
                            <div className="h-[400px] border border-slate-200 dark:border-neutral-600 rounded-lg overflow-hidden">
                                <RoleInheritanceTree
                                    personaId={personaId}
                                    interactive={true}
                                    onExport={handleExport}
                                />
                            </div>

                            <div className="mt-4 p-4 bg-blue-50 dark:bg-indigo-600/30 rounded-lg">
                                <p className="text-sm text-blue-800 dark:text-blue-200">
                                    💡 Click the export button in the tree to download the hierarchy in {exportFormat.toUpperCase()} format
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Right: Import */}
                    <div className="space-y-6">
                        <div className="bg-white dark:bg-neutral-800 rounded-lg shadow-lg p-6">
                            <h2 className="text-xl font-semibold mb-4 text-slate-900 dark:text-neutral-100">
                                📥 Import Hierarchy
                            </h2>

                            {/* File Upload */}
                            <div className="mb-4">
                                <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                    Select File
                                </label>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    accept=".json"
                                    onChange={handleFileSelect}
                                    className="w-full px-4 py-2 border border-slate-300 dark:border-neutral-600 rounded-lg
                                             bg-white dark:bg-neutral-700 text-slate-900 dark:text-neutral-100
                                             file:mr-4 file:py-2 file:px-4 file:rounded-lg file:border-0
                                             file:bg-blue-500 file:text-white file:cursor-pointer
                                             hover:file:bg-blue-600"
                                />
                            </div>

                            {/* Validation Errors */}
                            {validationErrors.length > 0 && (
                                <div className="mb-4 p-4 bg-red-50 dark:bg-rose-600/30 rounded-lg">
                                    <h3 className="text-sm font-semibold text-red-800 dark:text-red-200 mb-2">
                                        ⚠️ Validation Errors
                                    </h3>
                                    <ul className="space-y-1">
                                        {validationErrors.map((error, idx) => (
                                            <li key={idx} className="text-sm text-red-700 dark:text-red-300">
                                                • {error}
                                            </li>
                                        ))}
                                    </ul>
                                </div>
                            )}

                            {/* Preview */}
                            {previewData && validationErrors.length === 0 && (
                                <div className="mb-4 p-4 bg-green-50 dark:bg-green-600/30 rounded-lg">
                                    <h3 className="text-sm font-semibold text-green-800 dark:text-green-200 mb-2">
                                        ✅ Preview
                                    </h3>
                                    <div className="space-y-2 text-sm text-green-700 dark:text-green-300">
                                        <div className="flex justify-between">
                                            <span>Nodes:</span>
                                            <span className="font-semibold">{previewData.nodes.length}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span>Edges:</span>
                                            <span className="font-semibold">{previewData.edges.length}</span>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Import Button */}
                            <button
                                onClick={handleImport}
                                disabled={!previewData || validationErrors.length > 0}
                                className="w-full py-3 px-4 bg-blue-500 text-white rounded-lg font-medium
                                         hover:bg-blue-600 disabled:bg-slate-300 dark:disabled:bg-slate-700
                                         disabled:cursor-not-allowed transition-colors"
                            >
                                Import Hierarchy
                            </button>

                            {/* Raw Data Preview */}
                            {importData && (
                                <div className="mt-4">
                                    <label className="block text-sm font-medium text-slate-700 dark:text-neutral-300 mb-2">
                                        Raw Data
                                    </label>
                                    <pre className="p-4 bg-slate-100 dark:bg-slate-900 rounded-lg text-xs overflow-auto max-h-60">
                                        {importData}
                                    </pre>
                                </div>
                            )}
                        </div>

                        {/* Import Instructions */}
                        <div className="bg-blue-50 dark:bg-indigo-600/30 rounded-lg p-6">
                            <h3 className="text-lg font-semibold text-blue-900 dark:text-blue-100 mb-3">
                                📋 Import Requirements
                            </h3>
                            <ul className="space-y-2 text-sm text-blue-800 dark:text-blue-200">
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>File must be valid JSON format</span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>Must contain "nodes" and "edges" arrays</span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>Each node must have unique "id" field</span>
                                </li>
                                <li className="flex items-start">
                                    <span className="mr-2">•</span>
                                    <span>Maximum 1000 nodes per import</span>
                                </li>
                            </ul>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ExportImportDemo;

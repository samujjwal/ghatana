/**
 * Bulk Import Modal Component
 * 
 * Enables batch upload of domains and concepts via JSON/CSV files.
 * Validates file structure, shows preview, and handles batch creation.
 * 
 * @doc.type component
 * @doc.purpose Bulk content import for efficient data migration
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from './ui';
import { Button, Spinner } from '@ghatana/design-system';

interface DomainImport {
    name: string;
    domain: string;
    curriculumLevel: string;
    description?: string;
    learningObjectives?: string[];
    concepts?: ConceptImport[];
}

interface ConceptImport {
    conceptName: string;
    definition: string;
    difficulty?: string;
    learningObjectives?: string[];
    prerequisites?: string[];
    competencies?: string[];
    keywords?: string[];
}

interface BulkImportModalProps {
    isOpen: boolean;
    onClose: () => void;
}

interface ImportResult {
    success: number;
    failed: number;
    errors: string[];
    createdDomains: { id: string; name: string }[];
}

export function BulkImportModal({ isOpen, onClose }: BulkImportModalProps) {
    const [file, setFile] = useState<File | null>(null);
    const [preview, setPreview] = useState<DomainImport[]>([]);
    const [errors, setErrors] = useState<string[]>([]);
    const [importResult, setImportResult] = useState<ImportResult | null>(null);
    const queryClient = useQueryClient();

    const importMutation = useMutation({
        mutationFn: async (domains: DomainImport[]) => {
            const results: ImportResult = {
                success: 0,
                failed: 0,
                errors: [],
                createdDomains: [],
            };

            for (const domain of domains) {
                try {
                    const res = await fetch('/admin/api/v1/content/db/domains', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(domain),
                    });

                    if (!res.ok) {
                        const error = await res.text();
                        results.failed++;
                        results.errors.push(`${domain.name}: ${error}`);
                    } else {
                        const created = await res.json();
                        results.success++;
                        results.createdDomains.push({ id: created.id, name: domain.name });
                    }
                } catch (error) {
                    results.failed++;
                    results.errors.push(
                        `${domain.name}: ${error instanceof Error ? error.message : String(error)}`
                    );
                }
            }

            return results;
        },
        onSuccess: (results) => {
            setImportResult(results);
            queryClient.invalidateQueries({ queryKey: ['domains'] });

            if (results.failed === 0) {
                setTimeout(() => {
                    handleClose();
                }, 3000);
            }
        },
    });

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const selectedFile = e.target.files?.[0];
        if (!selectedFile) return;

        setFile(selectedFile);
        setErrors([]);
        setPreview([]);
        setImportResult(null);

        try {
            const text = await selectedFile.text();
            const fileExt = selectedFile.name.split('.').pop()?.toLowerCase();

            let parsed: DomainImport[] = [];

            if (fileExt === 'json') {
                parsed = parseJSON(text);
            } else if (fileExt === 'csv') {
                parsed = parseCSV(text);
            } else {
                setErrors(['Unsupported file format. Please upload JSON or CSV.']);
                return;
            }

            const validationErrors = validateDomains(parsed);
            if (validationErrors.length > 0) {
                setErrors(validationErrors);
            } else {
                setPreview(parsed);
            }
        } catch (error) {
            setErrors([`Failed to parse file: ${error instanceof Error ? error.message : String(error)}`]);
        }
    };

    const parseJSON = (text: string): DomainImport[] => {
        const data = JSON.parse(text);
        return Array.isArray(data) ? data : [data];
    };

    const parseCSV = (text: string): DomainImport[] => {
        const lines = text.trim().split('\n');
        if (lines.length < 2) throw new Error('CSV must have header and at least one data row');

        const headers = lines[0].split(',').map(h => h.trim());
        const domains: Map<string, DomainImport> = new Map();

        for (let i = 1; i < lines.length; i++) {
            const values = lines[i].split(',').map(v => v.trim().replace(/^"|"$/g, ''));
            const row: Record<string, string> = {};

            headers.forEach((header, idx) => {
                row[header] = values[idx] || '';
            });

            const domainKey = `${row.domainName}-${row.domain}-${row.curriculumLevel}`;

            if (!domains.has(domainKey)) {
                domains.set(domainKey, {
                    name: row.domainName,
                    domain: row.domain,
                    curriculumLevel: row.curriculumLevel,
                    description: row.domainDescription || '',
                    concepts: [],
                });
            }

            if (row.conceptName) {
                domains.get(domainKey)!.concepts!.push({
                    conceptName: row.conceptName,
                    definition: row.conceptDefinition || '',
                    difficulty: row.conceptDifficulty || 'BASIC',
                    keywords: row.keywords ? row.keywords.split(';').map(k => k.trim()) : [],
                });
            }
        }

        return Array.from(domains.values());
    };

    const validateDomains = (domains: DomainImport[]): string[] => {
        const errors: string[] = [];
        const validDomains = ['PHYSICS', 'CHEMISTRY', 'BIOLOGY', 'MATHEMATICS', 'CS_DISCRETE', 'ECONOMICS', 'ENGINEERING', 'MEDICINE'];
        const validLevels = ['ELEMENTARY', 'MIDDLE_SCHOOL', 'HIGH_SCHOOL', 'UNDERGRADUATE', 'GRADUATE', 'PROFESSIONAL'];

        domains.forEach((domain, idx) => {
            if (!domain.name?.trim()) {
                errors.push(`Row ${idx + 1}: Domain name is required`);
            }
            if (!validDomains.includes(domain.domain)) {
                errors.push(`Row ${idx + 1}: Invalid domain "${domain.domain}". Must be one of: ${validDomains.join(', ')}`);
            }
            if (!validLevels.includes(domain.curriculumLevel)) {
                errors.push(`Row ${idx + 1}: Invalid curriculum level "${domain.curriculumLevel}". Must be one of: ${validLevels.join(', ')}`);
            }
        });

        return errors;
    };

    const handleImport = () => {
        if (preview.length > 0) {
            importMutation.mutate(preview);
        }
    };

    const handleClose = () => {
        setFile(null);
        setPreview([]);
        setErrors([]);
        setImportResult(null);
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 dark:bg-black/70 flex items-center justify-center z-50 p-4">
            <Card className="w-full max-w-4xl max-h-[90vh] overflow-y-auto p-6">
                <div className="flex justify-between items-start mb-4">
                    <div>
                        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">Bulk Import Domains</h2>
                        <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                            Upload JSON or CSV file to import multiple domains and concepts
                        </p>
                    </div>
                    <button
                        onClick={handleClose}
                        className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                    >
                        ✕
                    </button>
                </div>

                {/* File Format Instructions */}
                <Card className="p-4 mb-4 bg-blue-50 dark:bg-blue-900/20">
                    <h3 className="font-semibold mb-2">File Format Requirements</h3>
                    <div className="text-sm space-y-2">
                        <div>
                            <strong>JSON Format:</strong>
                            <pre className="mt-1 p-2 bg-white dark:bg-gray-800 rounded text-xs overflow-x-auto">
                                {`[
  {
    "name": "Physics 101",
    "domain": "PHYSICS",
    "curriculumLevel": "HIGH_SCHOOL",
    "description": "Introduction to physics",
    "concepts": [
      {
        "conceptName": "Newton's Laws",
        "definition": "The three laws of motion",
        "difficulty": "BASIC",
        "keywords": ["motion", "force"]
      }
    ]
  }
]`}
                            </pre>
                        </div>
                        <div>
                            <strong>CSV Format:</strong>
                            <pre className="mt-1 p-2 bg-white dark:bg-gray-800 rounded text-xs overflow-x-auto">
                                {`domainName,domain,curriculumLevel,conceptName,conceptDefinition,keywords
Physics 101,PHYSICS,HIGH_SCHOOL,Newton's Laws,The three laws of motion,motion;force`}
                            </pre>
                        </div>
                        <div className="text-xs text-gray-600 dark:text-gray-400">
                            <strong>Valid domains:</strong> PHYSICS, CHEMISTRY, BIOLOGY, MATHEMATICS, CS_DISCRETE, ECONOMICS, ENGINEERING, MEDICINE<br />
                            <strong>Valid levels:</strong> ELEMENTARY, MIDDLE_SCHOOL, HIGH_SCHOOL, UNDERGRADUATE, GRADUATE, PROFESSIONAL
                        </div>
                    </div>
                </Card>

                {/* File Upload */}
                <div className="mb-4">
                    <input
                        type="file"
                        accept=".json,.csv"
                        onChange={handleFileChange}
                        className="block w-full text-sm text-gray-500 dark:text-gray-400
                            file:mr-4 file:py-2 file:px-4
                            file:rounded file:border-0
                            file:text-sm file:font-semibold
                            file:bg-blue-50 file:text-blue-700
                            hover:file:bg-blue-100
                            dark:file:bg-blue-900/20 dark:file:text-blue-300"
                    />
                    {file && (
                        <p className="mt-2 text-sm text-gray-600 dark:text-gray-400">
                            Selected: {file.name} ({(file.size / 1024).toFixed(2)} KB)
                        </p>
                    )}
                </div>

                {/* Errors */}
                {errors.length > 0 && (
                    <Card className="p-4 mb-4 bg-red-50 dark:bg-red-900/20">
                        <h3 className="font-semibold text-red-800 dark:text-red-300 mb-2">Validation Errors</h3>
                        <ul className="list-disc list-inside text-sm text-red-700 dark:text-red-400 space-y-1">
                            {errors.map((error, idx) => (
                                <li key={idx}>{error}</li>
                            ))}
                        </ul>
                    </Card>
                )}

                {/* Import Result */}
                {importResult && (
                    <Card className={`p-4 mb-4 ${importResult.failed === 0 ? 'bg-green-50 dark:bg-green-900/20' : 'bg-orange-50 dark:bg-orange-900/20'}`}>
                        <h3 className="font-semibold mb-2">Import Results</h3>
                        <div className="text-sm space-y-1">
                            <p className="text-green-700 dark:text-green-300">✓ Successfully imported: {importResult.success} domains</p>
                            {importResult.failed > 0 && (
                                <>
                                    <p className="text-red-700 dark:text-red-400">✗ Failed: {importResult.failed} domains</p>
                                    <ul className="list-disc list-inside text-red-600 dark:text-red-400 ml-4">
                                        {importResult.errors.map((error, idx) => (
                                            <li key={idx}>{error}</li>
                                        ))}
                                    </ul>
                                </>
                            )}
                        </div>
                        {importResult.failed === 0 && (
                            <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
                                Closing in 3 seconds...
                            </p>
                        )}
                    </Card>
                )}

                {/* Preview Table */}
                {preview.length > 0 && !importResult && (
                    <div className="mb-4">
                        <h3 className="font-semibold mb-2">Preview ({preview.length} domains)</h3>
                        <div className="border border-gray-300 dark:border-gray-700 rounded overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead className="bg-gray-100 dark:bg-gray-800">
                                    <tr>
                                        <th className="px-4 py-2 text-left">Domain Name</th>
                                        <th className="px-4 py-2 text-left">Domain</th>
                                        <th className="px-4 py-2 text-left">Level</th>
                                        <th className="px-4 py-2 text-left">Concepts</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {preview.map((domain, idx) => (
                                        <tr key={idx} className="border-t border-gray-200 dark:border-gray-700">
                                            <td className="px-4 py-2">{domain.name}</td>
                                            <td className="px-4 py-2">{domain.domain}</td>
                                            <td className="px-4 py-2">{domain.curriculumLevel}</td>
                                            <td className="px-4 py-2">{domain.concepts?.length || 0}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}

                {/* Actions */}
                <div className="flex gap-2 justify-end">
                    <Button variant="outline" onClick={handleClose} disabled={importMutation.isPending}>
                        Cancel
                    </Button>
                    {preview.length > 0 && !importResult && (
                        <Button
                            onClick={handleImport}
                            disabled={importMutation.isPending}
                        >
                            {importMutation.isPending ? (
                                <>
                                    <Spinner className="mr-2" />
                                    Importing...
                                </>
                            ) : (
                                `Import ${preview.length} Domain${preview.length > 1 ? 's' : ''}`
                            )}
                        </Button>
                    )}
                </div>
            </Card>
        </div>
    );
}

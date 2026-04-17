/**
 * Lifecycle Export Service
 * 
 * Service for exporting lifecycle data in multiple formats.
 * 
 * @doc.type service
 * @doc.purpose Lifecycle data export
 * @doc.layer product
 * @doc.pattern Service
 */

import type { LifecyclePhase } from '@/types/lifecycle';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';

export interface LifecycleExportData {
    projectId: string;
    projectName?: string;
    exportDate: string;
    phases: Array<{
        phase: LifecyclePhase;
        artifacts: Array<{
            id: string;
            kind: LifecycleArtifactKind;
            title: string;
            status: string;
            payload: Record<string, unknown>;
            createdAt: string;
            updatedAt: string;
        }>;
    }>;
}

export type LifecycleExportFormat = 'pdf' | 'json' | 'markdown';

/**
 * Export lifecycle data
 */
export async function exportLifecycleReport(
    data: LifecycleExportData,
    format: LifecycleExportFormat
): Promise<void> {
    switch (format) {
        case 'json':
            return exportLifecycleJSON(data);
        case 'markdown':
            return exportLifecycleMarkdown(data);
        case 'pdf':
            return exportLifecyclePDF(data);
    }
}

function exportLifecycleJSON(data: LifecycleExportData): void {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    downloadFile(blob, `${data.projectName || data.projectId}-lifecycle.json`);
}

function exportLifecycleMarkdown(data: LifecycleExportData): void {
    let md = `# ${data.projectName || data.projectId} - Lifecycle\n\n**Export:** ${data.exportDate}\n\n`;
    data.phases.forEach((phase) => {
        md += `## ${phase.phase}\n\n`;
        phase.artifacts.forEach((a) => {
            md += `### ${a.title}\n- **Status:** ${a.status}\n- **Type:** ${a.kind}\n\n`;
        });
    });
    const blob = new Blob([md], { type: 'text/markdown' });
    downloadFile(blob, `${data.projectName || data.projectId}-lifecycle.md`);
}

async function exportLifecyclePDF(data: LifecycleExportData): Promise<void> {
    const html = `<!DOCTYPE html><html><head><title>${data.projectName || data.projectId}</title>
    <style>body{font-family:sans-serif;padding:20px;}h1{border-bottom:2px solid #333;}</style>
    </head><body><h1>${data.projectName || data.projectId}</h1>
    ${data.phases.map(p => `<h2>${p.phase}</h2>${p.artifacts.map(a => `<div><h3>${a.title}</h3><p>${a.status}</p></div>`).join('')}`).join('')}
    </body></html>`;
    const w = window.open('', '_blank');
    if (w) { w.document.write(html); w.document.close(); setTimeout(() => w.print(), 250); }
}

function downloadFile(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

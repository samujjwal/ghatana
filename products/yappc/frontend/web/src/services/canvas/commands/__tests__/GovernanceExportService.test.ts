/**
 * GovernanceExportService Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for GovernanceExportService
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  fetchGovernanceExport,
  downloadGovernanceExport,
  GovernanceExportError,
} from '../../services/canvas/commands/GovernanceExportService';

// ─── Helpers ─────────────────────────────────────────────────────────────────

function makeOkResponse(body: string, contentType: string): Response {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': contentType },
  });
}

function makeErrorResponse(status: number, statusText: string): Response {
  return new Response(null, { status, statusText });
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('GovernanceExportService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());

    // Stub DOM APIs used for download trigger
    const anchor = {
      href: '',
      download: '',
      click: vi.fn(),
    };
    vi.spyOn(document, 'createElement').mockReturnValue(anchor as unknown as HTMLElement);
    vi.spyOn(document.body, 'appendChild').mockImplementation(() => anchor as unknown as Node);
    vi.spyOn(document.body, 'removeChild').mockImplementation(() => anchor as unknown as Node);
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn().mockReturnValue('blob:test-url'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('fetchGovernanceExport', () => {
    it('throws GovernanceExportError when artifactId is empty', async () => {
      await expect(fetchGovernanceExport('', 'json')).rejects.toBeInstanceOf(GovernanceExportError);
    });

    it('calls the correct URL for JSON format', async () => {
      vi.mocked(fetch).mockResolvedValue(makeOkResponse('{"records":[]}', 'application/json'));

      await fetchGovernanceExport('art-123', 'json');

      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/yappc/artifacts/art-123/audit-export?format=json',
        expect.objectContaining({ method: 'GET', headers: expect.objectContaining({ Accept: 'application/json' }) })
      );
    });

    it('calls the correct URL for CSV format', async () => {
      vi.mocked(fetch).mockResolvedValue(makeOkResponse('id,verb\n1,CREATED\n', 'text/csv'));

      await fetchGovernanceExport('art-456', 'csv');

      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/yappc/artifacts/art-456/audit-export?format=csv',
        expect.objectContaining({ headers: expect.objectContaining({ Accept: 'text/csv' }) })
      );
    });

    it('throws GovernanceExportError with status code on 404', async () => {
      vi.mocked(fetch).mockResolvedValue(makeErrorResponse(404, 'Not Found'));

      const err = await fetchGovernanceExport('art-missing', 'json').catch((e: unknown) => e);

      expect(err).toBeInstanceOf(GovernanceExportError);
      expect((err as GovernanceExportError).statusCode).toBe(404);
    });

    it('throws GovernanceExportError with status code on 403', async () => {
      vi.mocked(fetch).mockResolvedValue(makeErrorResponse(403, 'Forbidden'));

      const err = await fetchGovernanceExport('art-forbidden', 'json').catch((e: unknown) => e);

      expect(err).toBeInstanceOf(GovernanceExportError);
      expect((err as GovernanceExportError).statusCode).toBe(403);
    });

    it('URL-encodes the artifactId', async () => {
      vi.mocked(fetch).mockResolvedValue(makeOkResponse('{}', 'application/json'));

      await fetchGovernanceExport('art/with-slash', 'json');

      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('art%2Fwith-slash'),
        expect.any(Object)
      );
    });
  });

  describe('downloadGovernanceExport', () => {
    it('triggers a browser download with default filename', async () => {
      vi.mocked(fetch).mockResolvedValue(
        makeOkResponse('{"records":[{"id":1},{"id":2}]}', 'application/json')
      );

      const anchor = document.createElement('a') as unknown as { download: string; click: () => void };
      const result = await downloadGovernanceExport('art-789', 'json');

      expect(anchor.download).toBe('artifact-art-789-audit.json');
      expect(result.artifactId).toBe('art-789');
      expect(result.format).toBe('json');
    });

    it('respects a custom filename', async () => {
      vi.mocked(fetch).mockResolvedValue(makeOkResponse('{}', 'application/json'));

      const anchor = document.createElement('a') as unknown as { download: string };
      await downloadGovernanceExport('art-789', 'json', 'my-export.json');

      expect(anchor.download).toBe('my-export.json');
    });

    it('parses recordCount from JSON blob', async () => {
      vi.mocked(fetch).mockResolvedValue(
        makeOkResponse('{"records":[{"id":1},{"id":2},{"id":3}]}', 'application/json')
      );

      const result = await downloadGovernanceExport('art-789', 'json');

      expect(result.recordCount).toBe(3);
    });

    it('sets recordCount to 0 for CSV format', async () => {
      vi.mocked(fetch).mockResolvedValue(makeOkResponse('id,verb\n1,CREATED\n', 'text/csv'));

      const result = await downloadGovernanceExport('art-789', 'csv');

      expect(result.recordCount).toBe(0);
    });

    it('revokes the object URL after download', async () => {
      vi.mocked(fetch).mockResolvedValue(makeOkResponse('{}', 'application/json'));

      await downloadGovernanceExport('art-abc', 'json');

      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test-url');
    });

    it('propagates GovernanceExportError from fetchGovernanceExport', async () => {
      vi.mocked(fetch).mockResolvedValue(makeErrorResponse(500, 'Internal Server Error'));

      await expect(downloadGovernanceExport('art-fail', 'json')).rejects.toBeInstanceOf(
        GovernanceExportError
      );
    });
  });
});

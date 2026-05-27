/**
 * Audit Page for PHR healthcare system.
 *
 * Displays immutable access history for patient data, including
 * access events, consent grants, and consent revocations.
 * Queries the real PHR audit/evidence API â€” no mock data.
 *
 * @doc.type page
 * @doc.purpose Audit page for immutable access history
 * @doc.layer frontend
 */
import React, { useCallback, useEffect, useState } from 'react';
import { fetchAuditEvents } from '../api/phrApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { AuditEvent } from '../types';

type AuditFilter = 'all' | 'access' | 'consent' | 'emergency';

export function AuditPage(): React.ReactElement {
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<AuditFilter>('all');
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent | null>(null);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);
  const [showExportModal, setShowExportModal] = useState<boolean>(false);
  const [exportAcknowledged, setExportAcknowledged] = useState<boolean>(false);
  const [exporting, setExporting] = useState<boolean>(false);

  const loadAuditEvents = useCallback((activeFilter: AuditFilter): void => {
    setLoading(true);
    setError(null);
    fetchAuditEvents({ filter: activeFilter })
      .then((page) => {
        setAuditEvents(page.events);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : t('audit.loading'));
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    loadAuditEvents(filter);
  }, [filter, loadAuditEvents]);

  const handleFilterChange = (nextFilter: AuditFilter): void => {
    setFilter(nextFilter);
  };

  const handleEventClick = (event: AuditEvent): void => {
    setSelectedEvent(event);
  };

  const handleCloseDetail = (): void => {
    setSelectedEvent(null);
  };

  const handleExportClick = (): void => {
    setShowExportModal(true);
    setExportAcknowledged(false);
  };

  const handleCloseExportModal = (): void => {
    setShowExportModal(false);
    setExportAcknowledged(false);
  };

  const handleExport = async (): Promise<void> => {
    if (!exportAcknowledged) return;
    setExporting(true);
    try {
      const csvContent = generateAuditCsv(auditEvents);
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      const url = URL.createObjectURL(blob);
      link.setAttribute('href', url);
      link.setAttribute('download', `audit-export-${new Date().toISOString().split('T')[0]}.csv`);
      link.style.visibility = 'hidden';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setShowExportModal(false);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Export failed');
    } finally {
      setExporting(false);
    }
  };

  const generateAuditCsv = (events: AuditEvent[]): string => {
    const headers = ['ID', 'Timestamp', 'Event Type', 'Principal', 'Resource Type', 'Resource ID', 'Status'];
    const rows = events.map(e => [
      e.id,
      e.timestamp,
      e.eventType,
      e.principal,
      e.resourceType,
      e.resourceId || '',
      e.success ? 'SUCCESS' : 'FAILED'
    ]);
    return [headers, ...rows].map(row => row.join(',')).join('\n');
  };

  const filterButtons: Array<{ key: AuditFilter; label: string }> = [
    { key: 'all', label: t('audit.filter.all') },
    { key: 'access', label: t('audit.filter.access') },
    { key: 'consent', label: t('audit.filter.consent') },
  ];

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">{t('audit.title')}</h1>
        <button
          onClick={handleExportClick}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          disabled={auditEvents.length === 0}
        >
          Export CSV
        </button>
      </div>

      <div className="mb-6 flex gap-4">
        {filterButtons.map(({ key, label }) => (
          <button
            key={key}
            onClick={() => handleFilterChange(key)}
            aria-pressed={filter === key}
            className={`px-4 py-2 rounded ${filter === key ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
          >
            {label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="text-center py-8">{t('audit.loading')}</div>
      ) : error ? (
        <div role="alert" className="text-center py-8 text-red-700">{error}</div>
      ) : (
        <div className="bg-white shadow rounded-lg overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('audit.timestamp')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('audit.eventType')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('audit.principal')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('audit.resource')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('audit.status')}
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('audit.details')}
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {auditEvents.map((event) => (
                <tr 
                  key={event.id}
                  onClick={() => handleEventClick(event)}
                  className="cursor-pointer hover:bg-gray-50"
                >
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {formatPhrDateTime(event.timestamp)}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {event.eventType}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {event.principal}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {event.resourceType}
                    {event.resourceId !== null && ` (${event.resourceId})`}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <span
                      className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                        event.success
                          ? 'bg-green-100 text-green-800'
                          : 'bg-red-100 text-red-800'
                      }`}
                    >
                      {event.success ? t('audit.success') : t('audit.failed')}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {Object.entries(event.details ?? {}).map(([key, value]) => (
                      <div key={key}>
                        {key}: {value}
                      </div>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {auditEvents.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              {t('audit.empty')}
            </div>
          )}
        </div>
      )}

      {/* Audit Detail Drawer */}
      {selectedEvent && (
        <div className="fixed inset-0 z-50 overflow-hidden">
          <div className="absolute inset-0 bg-black bg-opacity-50" onClick={handleCloseDetail} />
          <div className="absolute right-0 top-0 h-full w-full max-w-lg bg-white shadow-xl overflow-y-auto">
            <div className="p-6">
              <div className="flex justify-between items-center mb-6">
                <h2 className="text-xl font-bold">Audit Event Details</h2>
                <button
                  onClick={handleCloseDetail}
                  className="text-gray-500 hover:text-gray-700"
                  aria-label="Close"
                >
                  ✕
                </button>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700">{t('audit.timestamp')}</label>
                  <p className="mt-1 text-sm text-gray-900">{formatPhrDateTime(selectedEvent.timestamp)}</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">{t('audit.eventType')}</label>
                  <p className="mt-1 text-sm text-gray-900">{selectedEvent.eventType}</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">{t('audit.principal')}</label>
                  <p className="mt-1 text-sm text-gray-900">{selectedEvent.principal}</p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">{t('audit.resource')}</label>
                  <p className="mt-1 text-sm text-gray-900">
                    {selectedEvent.resourceType}
                    {selectedEvent.resourceId !== null && ` (${selectedEvent.resourceId})`}
                  </p>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">{t('audit.status')}</label>
                  <span
                    className={`mt-1 inline-flex px-2 py-1 text-xs leading-5 font-semibold rounded-full ${
                      selectedEvent.success
                        ? 'bg-green-100 text-green-800'
                        : 'bg-red-100 text-red-800'
                    }`}
                  >
                    {selectedEvent.success ? t('audit.success') : t('audit.failed')}
                  </span>
                </div>

                {selectedEvent.details && Object.keys(selectedEvent.details).length > 0 && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700">{t('audit.details')}</label>
                    <div className="mt-1 bg-gray-50 p-3 rounded-md">
                      {Object.entries(selectedEvent.details).map(([key, value]) => (
                        <div key={key} className="text-sm">
                          <span className="font-medium">{key}:</span> {String(value)}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Export Modal with Privacy Policy */}
      {showExportModal && (
        <div className="fixed inset-0 z-50 overflow-hidden">
          <div className="absolute inset-0 bg-black bg-opacity-50" onClick={handleCloseExportModal} />
          <div className="absolute inset-0 flex items-center justify-center p-4">
            <div className="bg-white rounded-lg shadow-xl max-w-lg w-full max-h-full overflow-y-auto">
              <div className="p-6">
                <h2 className="text-xl font-bold mb-4">Export Audit Trail</h2>
                
                <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded-md">
                  <h3 className="font-semibold text-yellow-800 mb-2">Privacy Policy Notice</h3>
                  <p className="text-sm text-yellow-700 mb-2">
                    By exporting this audit trail, you agree to the following:
                  </p>
                  <ul className="text-sm text-yellow-700 list-disc list-inside space-y-1">
                    <li>This data contains sensitive access information</li>
                    <li>Store exported files securely and delete when no longer needed</li>
                    <li>Do not share with unauthorized personnel</li>
                    <li>Comply with applicable data protection regulations</li>
                    <li>Report any unauthorized access immediately</li>
                  </ul>
                </div>

                <div className="mb-4">
                  <label className="flex items-start space-x-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={exportAcknowledged}
                      onChange={(e) => setExportAcknowledged(e.target.checked)}
                      className="mt-1"
                    />
                    <span className="text-sm text-gray-700">
                      I acknowledge the privacy policy and agree to handle this data responsibly
                    </span>
                  </label>
                </div>

                <div className="flex justify-end space-x-3">
                  <button
                    onClick={handleCloseExportModal}
                    className="px-4 py-2 bg-gray-200 text-gray-700 rounded hover:bg-gray-300"
                  >
                    Cancel
                  </button>
                  <button
                    onClick={handleExport}
                    disabled={!exportAcknowledged || exporting}
                    className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-300 disabled:cursor-not-allowed"
                  >
                    {exporting ? 'Exporting...' : 'Export'}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

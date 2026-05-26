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

  const filterButtons: Array<{ key: AuditFilter; label: string }> = [
    { key: 'all', label: t('audit.filter.all') },
    { key: 'access', label: t('audit.filter.access') },
    { key: 'consent', label: t('audit.filter.consent') },
  ];

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">{t('audit.title')}</h1>

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
                <tr key={event.id}>
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
    </section>
  );
}

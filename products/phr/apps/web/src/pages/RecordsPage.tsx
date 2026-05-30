import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchRecords } from '../api/recordsApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { PatientRecordSummary } from '../types';
import { usePhrSession } from '../auth/PhrSessionContext';
import { SafeError } from '../components/SafeError';

export function RecordsPage(): React.ReactElement {
  const [records, setRecords] = useState<PatientRecordSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const { session } = usePhrSession();
  
  // Filter state
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [resourceTypeFilter, setResourceTypeFilter] = useState<string>('');
  const [dateFromFilter, setDateFromFilter] = useState<string>('');
  const [dateToFilter, setDateToFilter] = useState<string>('');
  
  // Pagination state
  const [limit] = useState<number>(50);
  const [offset, setOffset] = useState<number>(0);
  const [totalCount, setTotalCount] = useState<number>(0);

  useEffect(() => {
    if (!session) {
      setError(t('error.sessionRequired'));
      setLoading(false);
      return;
    }

    fetchRecords(session.principalId, {
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    }, {
      category: categoryFilter || undefined,
      resourceType: resourceTypeFilter || undefined,
      dateFrom: dateFromFilter || undefined,
      dateTo: dateToFilter || undefined,
      limit,
      offset,
    })
      .then((data) => {
        setRecords(data);
        setTotalCount(data.length);
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : t('error.recordsLoad')))
      .finally(() => setLoading(false));
  }, [session, categoryFilter, resourceTypeFilter, dateFromFilter, dateToFilter, limit, offset]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('records.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error} correlationId={session?.tenantId + '-' + session?.principalId} />;

  // Extract unique categories and resource types for filter options
  const categories = Array.from(new Set(records.map(r => r.category)));
  const resourceTypes = Array.from(new Set(records.map(r => r.resourceType)));

  return (
    <Card>
      <CardHeader title={t('records.title')} subheader={t('records.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          <div className="filter-bar">
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">{t('records.filter.category')}</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
            
            <select
              value={resourceTypeFilter}
              onChange={(e) => setResourceTypeFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">{t('records.filter.resourceType')}</option>
              {resourceTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
            
            <input
              type="date"
              value={dateFromFilter}
              onChange={(e) => setDateFromFilter(e.target.value)}
              className="filter-input"
              placeholder={t('records.filter.fromDate')}
            />
            
            <input
              type="date"
              value={dateToFilter}
              onChange={(e) => setDateToFilter(e.target.value)}
              className="filter-input"
              placeholder={t('records.filter.toDate')}
            />
            
            {(categoryFilter || resourceTypeFilter || dateFromFilter || dateToFilter) && (
              <button
                onClick={() => {
                  setCategoryFilter('');
                  setResourceTypeFilter('');
                  setDateFromFilter('');
                  setDateToFilter('');
                }}
                className="filter-clear"
              >
                {t('records.filter.clear')}
              </button>
            )}
          </div>

          {records.length === 0 ? (
            <div className="empty-state">{t('records.empty.filtered')}</div>
          ) : (
            <>
              <div className="pagination-info" role="status">
                {t('records.pagination.showing' as any, {
                  start: offset + 1,
                  end: Math.min(offset + limit, totalCount),
                  total: totalCount
                })}
              </div>
              <ul className="record-list" role="list">
                {records.map((record) => (
                  <li key={record.id} className="record-item">
                    <Link className="data-card" to={`/records/${record.id}`}>
                      <div>
                        <strong>{record.title}</strong>
                        <p className="muted">
                          {t('records.updated', {
                            resourceType: record.resourceType,
                            date: formatPhrDateTime(record.updatedAt),
                          })}
                        </p>
                        {record.provenance && (
                          <p className="muted" aria-label="Provenance information">
                            Source: {String(record.provenance.source ?? t('common.unknown'))}
                            {record.provenance.accessedAt as string && (
                              <span> • Accessed: {formatPhrDateTime(record.provenance.accessedAt as string)}</span>
                            )}
                          </p>
                        )}
                      </div>
                      <span className="pill">{record.category}</span>
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="pagination-controls" role="navigation" aria-label={t('records.pagination.label' as any)}>
                <button
                  onClick={() => setOffset(Math.max(0, offset - limit))}
                  disabled={offset === 0}
                  className="pagination-button"
                  aria-label={t('records.pagination.previous' as any)}
                >
                  {t('records.pagination.previous' as any)}
                </button>
                <button
                  onClick={() => setOffset(offset + limit)}
                  disabled={offset + limit >= totalCount}
                  className="pagination-button"
                  aria-label={t('records.pagination.next' as any)}
                >
                  {t('records.pagination.next' as any)}
                </button>
              </div>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

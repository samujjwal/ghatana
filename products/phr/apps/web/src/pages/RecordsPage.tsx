import React, { useEffect, useState } from 'react';
import { Button, Card, CardContent, CardHeader, Input, Select } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchRecords } from '../api/recordsApi';
import { toSessionContext } from '../api/requestApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { PatientRecordSummary } from '../types';
import { usePhrSession } from '../auth/PhrSessionContext';
import { SafeError } from '../components/SafeError';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';

export function RecordsPage(): React.ReactElement {
  const [records, setRecords] = useState<PatientRecordSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
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
      setError({ message: t('error.sessionRequired') });
      setLoading(false);
      return;
    }

    fetchRecords(session.principalId, toSessionContext(session), {
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
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('error.recordsLoad'))))
      .finally(() => setLoading(false));
  }, [session, categoryFilter, resourceTypeFilter, dateFromFilter, dateToFilter, limit, offset]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('records.loading')}</div>;
  if (error) return <SafeError title={t('dashboard.errorPrefix')} message={error.message} correlationId={error.correlationId} />;

  // Extract unique categories and resource types for filter options
  const categories = Array.from(new Set(records.map(r => r.category)));
  const resourceTypes = Array.from(new Set(records.map(r => r.resourceType)));

  return (
    <Card>
      <CardHeader title={t('records.title')} subheader={t('records.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          <div className="filter-bar">
            <Select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
            >
              <option value="">{t('records.filter.category')}</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </Select>
            
            <Select
              value={resourceTypeFilter}
              onChange={(e) => setResourceTypeFilter(e.target.value)}
            >
              <option value="">{t('records.filter.resourceType')}</option>
              {resourceTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </Select>
            
            <Input
              type="date"
              value={dateFromFilter}
              onChange={(e) => setDateFromFilter(e.target.value)}
              placeholder={t('records.filter.fromDate')}
            />
            
            <Input
              type="date"
              value={dateToFilter}
              onChange={(e) => setDateToFilter(e.target.value)}
              placeholder={t('records.filter.toDate')}
            />
            
            {(categoryFilter || resourceTypeFilter || dateFromFilter || dateToFilter) && (
              <Button
                type="button"
                onClick={() => {
                  setCategoryFilter('');
                  setResourceTypeFilter('');
                  setDateFromFilter('');
                  setDateToFilter('');
                }}
                variant="secondary"
              >
                {t('records.filter.clear')}
              </Button>
            )}
          </div>

          {records.length === 0 ? (
            <div className="empty-state">{t('records.empty.filtered')}</div>
          ) : (
            <>
              <div className="pagination-info" role="status">
                {t('records.pagination.showing', {
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
                          <p className="muted" aria-label={t('records.provenance.label')}>
                            {t('records.provenance.source', { source: String(record.provenance.source ?? t('common.unknown')) })}
                            {record.provenance.accessedAt as string && (
                              <span>{t('records.provenance.accessed', { date: formatPhrDateTime(record.provenance.accessedAt as string) })}</span>
                            )}
                          </p>
                        )}
                      </div>
                      <span className="pill">{record.category}</span>
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="pagination-controls" role="navigation" aria-label={t('records.pagination.label')}>
                <Button
                  type="button"
                  onClick={() => setOffset(Math.max(0, offset - limit))}
                  disabled={offset === 0}
                  aria-label={t('records.pagination.previous')}
                  variant="secondary"
                >
                  {t('records.pagination.previous')}
                </Button>
                <Button
                  type="button"
                  onClick={() => setOffset(offset + limit)}
                  disabled={offset + limit >= totalCount}
                  aria-label={t('records.pagination.next')}
                  variant="secondary"
                >
                  {t('records.pagination.next')}
                </Button>
              </div>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

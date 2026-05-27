import React, { useEffect, useState } from 'react';
import { Card, CardContent, CardHeader } from '@ghatana/design-system';
import { Link } from 'react-router-dom';
import { fetchRecords } from '../api/phrApi';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { PatientRecordSummary } from '../types';
import { usePhrSession } from '../auth/PhrSessionContext';

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

  useEffect(() => {
    if (!session) {
      setError('No session available');
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
    })
      .then(setRecords)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load records'))
      .finally(() => setLoading(false));
  }, [session, categoryFilter, resourceTypeFilter, dateFromFilter, dateToFilter]);

  if (loading) return <div className="loading">{t('records.loading')}</div>;
  if (error) return <div className="error">{t('dashboard.errorPrefix')}: {error}</div>;

  // Extract unique categories and resource types for filter options
  const categories = Array.from(new Set(records.map(r => r.category)));
  const resourceTypes = Array.from(new Set(records.map(r => r.resourceType)));

  return (
    <Card>
      <CardHeader title={t('records.title')} subheader={t('records.subheader')} />
      <CardContent>
        <div className="stack gap-md">
          {/* Filters */}
          <div className="filter-bar">
            <select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">Filter by category</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
            
            <select
              value={resourceTypeFilter}
              onChange={(e) => setResourceTypeFilter(e.target.value)}
              className="filter-select"
            >
              <option value="">Filter by resource type</option>
              {resourceTypes.map(type => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
            
            <input
              type="date"
              value={dateFromFilter}
              onChange={(e) => setDateFromFilter(e.target.value)}
              className="filter-input"
              placeholder="From date"
            />
            
            <input
              type="date"
              value={dateToFilter}
              onChange={(e) => setDateToFilter(e.target.value)}
              className="filter-input"
              placeholder="To date"
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
                Clear filters
              </button>
            )}
          </div>

          {/* Records list */}
          {records.length === 0 ? (
            <div className="empty-state">No records found matching your filters</div>
          ) : (
            records.map((record) => (
              <Link key={record.id} className="data-card" to={`/records/${record.id}`}>
                <div>
                  <strong>{record.title}</strong>
                  <p className="muted">
                    {t('records.updated', {
                      resourceType: record.resourceType,
                      date: formatPhrDateTime(record.updatedAt),
                    })}
                  </p>
                </div>
                <span className="pill">{record.category}</span>
              </Link>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  );
}

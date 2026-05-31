/**
 * Audit Page for PHR healthcare system.
 *
 * Displays immutable access history for patient data, including
 * access events, consent grants, and consent revocations.
 * Queries the real PHR audit/evidence API.
 *
 * @doc.type page
 * @doc.purpose Audit page for immutable access history
 * @doc.layer frontend
 */
import React, { useCallback, useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import {
  Badge,
  Button,
  Checkbox,
  Input,
  Modal,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@ghatana/design-system';
import { fetchAuditEvents } from '../api/auditApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { formatPhrDateTime, t } from '../i18n/phrI18n';
import type { AuditEvent } from '../types';

type AuditFilter = 'all' | 'access' | 'consent' | 'emergency';
type AuditPolicyMode = 'patient-self' | 'clinician-scoped' | 'admin-tenant' | 'unsupported';
type TranslationKey = Parameters<typeof t>[0];

interface AuditPolicy {
  mode: AuditPolicyMode;
  titleKey: TranslationKey;
  subheaderKey: TranslationKey;
  allowedFilters: AuditFilter[];
  canExport: boolean;
  requiresPatientScope: boolean;
}

const AUDIT_FILTERS: AuditFilter[] = ['all', 'access', 'consent', 'emergency'];

function getAuditPolicy(role: string): AuditPolicy {
  if (role === 'patient') {
    return {
      mode: 'patient-self',
      titleKey: 'audit.policy.patient.title',
      subheaderKey: 'audit.policy.patient.subheader',
      allowedFilters: AUDIT_FILTERS,
      canExport: true,
      requiresPatientScope: false,
    };
  }
  if (role === 'clinician') {
    return {
      mode: 'clinician-scoped',
      titleKey: 'audit.policy.clinician.title',
      subheaderKey: 'audit.policy.clinician.subheader',
      allowedFilters: ['access', 'emergency'],
      canExport: false,
      requiresPatientScope: true,
    };
  }
  if (role === 'admin') {
    return {
      mode: 'admin-tenant',
      titleKey: 'audit.policy.admin.title',
      subheaderKey: 'audit.policy.admin.subheader',
      allowedFilters: AUDIT_FILTERS,
      canExport: true,
      requiresPatientScope: false,
    };
  }
  return {
    mode: 'unsupported',
    titleKey: 'audit.policy.unsupported.title',
    subheaderKey: 'audit.policy.unsupported.subheader',
    allowedFilters: [],
    canExport: false,
    requiresPatientScope: false,
  };
}

export function AuditPage(): React.ReactElement {
  const { session } = usePhrSession();
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const policy = getAuditPolicy(session?.role ?? 'unsupported');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<SafeApiErrorState | null>(null);
  const [filter, setFilter] = useState<AuditFilter>('all');
  const [scopePatientId, setScopePatientId] = useState<string>('');
  const [appliedPatientScope, setAppliedPatientScope] = useState<string>('');
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent | null>(null);
  const [showExportModal, setShowExportModal] = useState<boolean>(false);
  const [exportAcknowledged, setExportAcknowledged] = useState<boolean>(false);
  const [exporting, setExporting] = useState<boolean>(false);

  const patientScope = policy.mode === 'patient-self'
    ? session?.principalId
    : policy.mode === 'clinician-scoped'
      ? appliedPatientScope
      : undefined;
  const isScopeReady = !policy.requiresPatientScope || Boolean(patientScope);

  const loadAuditEvents = useCallback((activeFilter: AuditFilter): void => {
    if (!session) return;
    const activePolicy = getAuditPolicy(session.role);
    const scopedPatientId = activePolicy.mode === 'patient-self'
      ? session.principalId
      : activePolicy.mode === 'clinician-scoped'
        ? appliedPatientScope
        : undefined;

    if (activePolicy.mode === 'unsupported') {
      setAuditEvents([]);
      setLoading(false);
      return;
    }
    if (activePolicy.requiresPatientScope && !scopedPatientId) {
      setAuditEvents([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    fetchAuditEvents({
      filter: activeFilter,
      patientId: scopedPatientId,
      tenantId: session.tenantId,
      principalId: session.principalId,
      role: session.role,
    })
      .then((page) => {
        setAuditEvents(page.events);
      })
      .catch((err: unknown) => {
        setError(toSafeApiErrorState(err, t('audit.error.load')));
      })
      .finally(() => {
        setLoading(false);
      });
  }, [appliedPatientScope, session]);

  useEffect(() => {
    loadAuditEvents(filter);
  }, [filter, loadAuditEvents]);

  useEffect(() => {
    if (!policy.allowedFilters.includes(filter)) {
      setFilter(policy.allowedFilters[0] ?? 'all');
    }
  }, [filter, policy.allowedFilters]);

  const handleFilterChange = (nextFilter: AuditFilter): void => {
    if (!policy.allowedFilters.includes(nextFilter)) return;
    setFilter(nextFilter);
  };

  const handleEventClick = (event: AuditEvent): void => {
    setSelectedEvent(event);
  };

  const handleCloseDetail = (): void => {
    setSelectedEvent(null);
  };

  const handleExportClick = (): void => {
    if (!policy.canExport) return;
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
      URL.revokeObjectURL(url);
      setShowExportModal(false);
    } catch (err: unknown) {
      setError(toSafeApiErrorState(err, t('audit.export.failed')));
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

  const filterButtons: Array<{ key: AuditFilter; label: string }> = policy.allowedFilters.map((key) => ({
    key,
    label: t(`audit.filter.${key}`),
  }));

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">{t(policy.titleKey)}</h1>
          <p className="mt-1 text-sm text-gray-600">{t(policy.subheaderKey)}</p>
        </div>
        <Button
          type="button"
          onClick={handleExportClick}
          disabled={!policy.canExport || auditEvents.length === 0}
        >
          {t('audit.export.csv')}
        </Button>
      </div>

      {policy.mode === 'unsupported' && (
        <SafeError message={t('audit.policy.unsupported.message')} severity="warning" />
      )}

      {policy.mode === 'clinician-scoped' && (
        <form
          className="mb-6 flex gap-3"
          onSubmit={(event: React.FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            setAppliedPatientScope(scopePatientId.trim());
          }}
        >
          <label className="sr-only" htmlFor="audit-patient-scope">{t('audit.scope.patientId.label')}</label>
          <Input
            id="audit-patient-scope"
            value={scopePatientId}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) => setScopePatientId(event.target.value)}
            placeholder={t('audit.scope.patientId.placeholder')}
            required
          />
          <Button
            type="submit"
            disabled={!scopePatientId.trim()}
          >
            {t('audit.scope.apply')}
          </Button>
        </form>
      )}

      <div className="mb-6 flex gap-4">
        {filterButtons.map(({ key, label }) => (
          <Button
            key={key}
            type="button"
            onClick={() => handleFilterChange(key)}
            aria-pressed={filter === key}
            variant={filter === key ? 'primary' : 'secondary'}
          >
            {label}
          </Button>
        ))}
      </div>

      {policy.requiresPatientScope && !isScopeReady ? (
        <div className="text-center py-8 text-gray-500">{t('audit.scope.required')}</div>
      ) : loading ? (
        <div className="text-center py-8">{t('audit.loading')}</div>
      ) : error ? (
        <SafeError message={error.message} correlationId={error.correlationId} onDismiss={() => setError(null)} />
      ) : (
        <div className="overflow-hidden">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell component="th">
                  {t('audit.timestamp')}
                </TableCell>
                <TableCell component="th">
                  {t('audit.eventType')}
                </TableCell>
                <TableCell component="th">
                  {t('audit.principal')}
                </TableCell>
                <TableCell component="th">
                  {t('audit.resource')}
                </TableCell>
                <TableCell component="th">
                  {t('audit.status')}
                </TableCell>
                <TableCell component="th">
                  {t('audit.details')}
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {auditEvents.map((event) => (
                <TableRow
                  key={event.id}
                  onClick={() => handleEventClick(event)}
                  className="cursor-pointer"
                >
                  <TableCell>
                    {formatPhrDateTime(event.timestamp)}
                  </TableCell>
                  <TableCell>
                    {event.eventType}
                  </TableCell>
                  <TableCell>
                    {event.principal}
                  </TableCell>
                  <TableCell>
                    {event.resourceType}
                    {event.resourceId !== null && ` (${event.resourceId})`}
                  </TableCell>
                  <TableCell>
                    <Badge variant={event.success ? 'success' : 'destructive'}>
                      {event.success ? t('audit.success') : t('audit.failed')}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {event.details && Object.keys(event.details).length > 0 ? t('audit.details.available') : t('audit.details.empty')}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {auditEvents.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              {t('audit.empty')}
            </div>
          )}
        </div>
      )}

      {selectedEvent && (
        <Modal isOpen onClose={handleCloseDetail} title={t('audit.detail.title')} size="md">
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
                  <Badge variant={selectedEvent.success ? 'success' : 'destructive'}>
                    {selectedEvent.success ? t('audit.success') : t('audit.failed')}
                  </Badge>
                </div>

                {selectedEvent.details && Object.keys(selectedEvent.details).length > 0 && (
                  <div>
                    <label className="block text-sm font-medium text-gray-700">{t('audit.details')}</label>
                    <div className="mt-1 bg-gray-50 p-3 rounded-md">
                      <p className="text-sm">{t('audit.details.redacted')}</p>
                    </div>
                  </div>
                )}
          </div>
        </Modal>
      )}

      {showExportModal && (
        <Modal isOpen onClose={handleCloseExportModal} title={t('audit.export.title')} size="md">
                <div className="mb-4 p-4 bg-yellow-50 border border-yellow-200 rounded-md">
                  <h3 className="font-semibold text-yellow-800 mb-2">{t('audit.export.noticeTitle')}</h3>
                  <p className="text-sm text-yellow-700 mb-2">
                    {t('audit.export.noticeIntro')}
                  </p>
                  <ul className="text-sm text-yellow-700 list-disc list-inside space-y-1">
                    <li>{t('audit.export.policy.sensitive')}</li>
                    <li>{t('audit.export.policy.storeSecurely')}</li>
                    <li>{t('audit.export.policy.noSharing')}</li>
                    <li>{t('audit.export.policy.compliance')}</li>
                    <li>{t('audit.export.policy.reportAccess')}</li>
                  </ul>
                </div>

                <div className="mb-4">
                  <Checkbox
                    checked={exportAcknowledged}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setExportAcknowledged(e.target.checked)}
                    label={t('audit.export.acknowledgement')}
                  />
                </div>

                <div className="flex justify-end space-x-3">
                  <Button
                    type="button"
                    onClick={handleCloseExportModal}
                    variant="secondary"
                  >
                    {t('audit.export.cancel')}
                  </Button>
                  <Button
                    type="button"
                    onClick={handleExport}
                    disabled={!exportAcknowledged || exporting}
                  >
                    {exporting ? t('audit.export.exporting') : t('audit.export.submit')}
                  </Button>
                </div>
        </Modal>
      )}
    </section>
  );
}

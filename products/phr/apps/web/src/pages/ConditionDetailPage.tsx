import React, { useEffect, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { useParams } from 'react-router-dom';
import { Badge, Card, CardContent, CardHeader } from '@ghatana/design-system';
import { fetchConditionDetail } from '../api/clinicalApi';
import { toSessionContext } from '../api/requestApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrSession } from '../auth/PhrSessionContext';
import { t } from '../i18n/phrI18n';
import type { ConditionSummary } from '../types';

export function ConditionDetailPage(): React.ReactElement {
  const { conditionId } = useParams<{ conditionId: string }>();
  const { session } = usePhrSession();
  const [condition, setCondition] = useState<ConditionSummary | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<SafeApiErrorState | null>(null);

  useEffect(() => {
    if (!session || !conditionId) return;
    fetchConditionDetail(conditionId, session.principalId, toSessionContext(session))
      .then(setCondition)
      .catch((err: unknown) => setError(toSafeApiErrorState(err, t('conditionDetail.error.load'))))
      .finally(() => setLoading(false));
  }, [session, conditionId]);

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('conditionDetail.loading')}</div>;
  if (error) return <SafeError title={t('conditionDetail.error.load')} message={error.message} correlationId={error.correlationId} />;
  if (!condition) return <SafeError title={t('conditionDetail.error.load')} message={t('conditionDetail.error.load')} />;

  return (
    <div className="stack gap-lg">
      <Card>
        <CardHeader title={condition.name} subheader={t('conditionDetail.subheader')} />
        <CardContent>
          <dl className="detail-list stack gap-sm">
            <div><dt>{t('conditionDetail.status')}</dt><dd><Badge variant={condition.status === 'active' ? 'destructive' : 'secondary'}>{condition.status}</Badge></dd></div>
            <div><dt>{t('conditionDetail.code')}</dt><dd>{condition.code || condition.icdCode || '-'}</dd></div>
            {condition.onsetDate && (
              <div><dt>{t('conditionDetail.onsetDate')}</dt><dd>{new Date(condition.onsetDate).toLocaleDateString()}</dd></div>
            )}
            {condition.resolvedDate && (
              <div><dt>{t('conditionDetail.resolvedDate')}</dt><dd>{new Date(condition.resolvedDate).toLocaleDateString()}</dd></div>
            )}
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}

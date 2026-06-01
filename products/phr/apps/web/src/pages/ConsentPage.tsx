import React, { useCallback, useEffect, useReducer, useState } from 'react';
import { SafeError } from '../components/SafeError';
import { Badge, Button, Card, CardContent, CardHeader, Checkbox, Input } from '@ghatana/design-system';
import { createConsentGrant, fetchConsentGrants, revokeConsentGrant } from '../api/consentApi';
import { toSafeApiErrorState, type SafeApiErrorState } from '../api/safeApiError';
import { usePhrRequestContext } from '../auth/PhrAccessContext';
import { formatPhrDate, t } from '../i18n/phrI18n';
import type { ConsentGrant, ConsentGrantRequest } from '../types';

type ConsentBadgeTone = 'success' | 'warning' | 'danger';

const consentBadgeTone: Record<ConsentGrant['status'], ConsentBadgeTone> = {
  active: 'success',
  expiring: 'warning',
  revoked: 'danger',
};

interface GrantFormState {
  recipientId: string;
  purpose: string;
  resourceTypes: string;
  allDocuments: boolean;
  specificDocumentIds: string;
  actions: string;
  expiresAt: string;
}

const EMPTY_GRANT_FORM: GrantFormState = {
  recipientId: '',
  purpose: '',
  resourceTypes: '',
  allDocuments: false,
  specificDocumentIds: '',
  actions: '',
  expiresAt: '',
};

type ConsentPageAction =
  | { type: 'open_grant_form' }
  | { type: 'close_grant_form' }
  | { type: 'set_field'; field: keyof GrantFormState; value: string }
  | { type: 'set_boolean_field'; field: keyof GrantFormState; value: boolean }
  | { type: 'set_submitting'; value: boolean }
  | { type: 'set_error'; error: SafeApiErrorState | null }
  | { type: 'set_success'; message: string | null };

interface ConsentPageState {
  showGrantForm: boolean;
  form: GrantFormState;
  submitting: boolean;
  formError: SafeApiErrorState | null;
  successMessage: string | null;
}

function consentPageReducer(state: ConsentPageState, action: ConsentPageAction): ConsentPageState {
  switch (action.type) {
    case 'open_grant_form':
      return { ...state, showGrantForm: true, form: EMPTY_GRANT_FORM, formError: null, successMessage: null };
    case 'close_grant_form':
      return { ...state, showGrantForm: false, formError: null };
    case 'set_field':
      return { ...state, form: { ...state.form, [action.field]: action.value } };
    case 'set_boolean_field':
      return { ...state, form: { ...state.form, [action.field]: action.value } };
    case 'set_submitting':
      return { ...state, submitting: action.value };
    case 'set_error':
      return { ...state, formError: action.error };
    case 'set_success':
      return { ...state, successMessage: action.message };
  }
  return state;
}

const INITIAL_STATE: ConsentPageState = {
  showGrantForm: false,
  form: EMPTY_GRANT_FORM,
  submitting: false,
  formError: null,
  successMessage: null,
};

export function ConsentPage(): React.ReactElement {
  const apiContext = usePhrRequestContext();
  const { principalId } = apiContext;
  const [consents, setConsents] = useState<ConsentGrant[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<SafeApiErrorState | null>(null);
  const [pendingRevokeId, setPendingRevokeId] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const [state, dispatch] = useReducer(consentPageReducer, INITIAL_STATE);
  const formErrorRef = React.useRef<HTMLDivElement>(null);
  const successMessageRef = React.useRef<HTMLDivElement>(null);

  // Focus error message when error is set for accessibility
  React.useEffect(() => {
    if (state.formError && formErrorRef.current) {
      formErrorRef.current.focus();
    }
  }, [state.formError]);

  // Focus success message when set for accessibility
  React.useEffect(() => {
    if (state.successMessage && successMessageRef.current) {
      successMessageRef.current.focus();
    }
  }, [state.successMessage]);

  const loadConsents = useCallback((): void => {
    setLoading(true);
    setLoadError(null);
    fetchConsentGrants(principalId, apiContext)
      .then(setConsents)
      .catch((err: unknown) => setLoadError(toSafeApiErrorState(err, t('error.consentsLoad'))))
      .finally(() => setLoading(false));
  }, [apiContext]);

  useEffect(() => {
    loadConsents();
  }, [loadConsents]);

  const handleGrantSubmit = async (event: React.FormEvent<HTMLFormElement>): Promise<void> => {
    event.preventDefault();
    dispatch({ type: 'set_error', error: null });

    const { recipientId, purpose, resourceTypes, actions, expiresAt } = state.form;
    if (!recipientId.trim() || !purpose.trim() || !resourceTypes.trim() || !actions.trim() || !expiresAt.trim()) {
      dispatch({ type: 'set_error', error: { message: t('consents.validation.required') } });
      return;
    }
    if (new Date(expiresAt).getTime() <= Date.now()) {
      dispatch({ type: 'set_error', error: { message: t('consents.validation.expiryFuture') } });
      return;
    }
    const allowedActions = actions.split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);
    if (allowedActions.some((action) => action !== 'read' && action !== 'download')) {
      dispatch({ type: 'set_error', error: { message: t('consents.validation.actions') } });
      return;
    }

    const request: ConsentGrantRequest = {
      patientId: principalId,
      recipientId: recipientId.trim(),
      purpose: purpose.trim(),
      scope: {
        resourceTypes: resourceTypes.split(',').map((s) => s.trim()).filter(Boolean),
        allDocuments: state.form.allDocuments,
        specificDocumentIds: state.form.specificDocumentIds ? state.form.specificDocumentIds.split(',').map((s) => s.trim()).filter(Boolean) : undefined,
        actions: allowedActions,
      },
      expiresAt: expiresAt.trim(),
    };

    dispatch({ type: 'set_submitting', value: true });
    try {
      await createConsentGrant(request, apiContext);
      dispatch({ type: 'set_success', message: t('consents.grant.success') });
      dispatch({ type: 'close_grant_form' });
      loadConsents();
    } catch (err: unknown) {
      dispatch({ type: 'set_error', error: toSafeApiErrorState(err, t('consents.error.create')) });
    } finally {
      dispatch({ type: 'set_submitting', value: false });
    }
  };

  const handleRevoke = async (consent: ConsentGrant): Promise<void> => {
    setRevokingId(consent.id);
    dispatch({ type: 'set_error', error: null });
    try {
      await revokeConsentGrant(consent.id, principalId, apiContext);
      dispatch({ type: 'set_success', message: t('consents.revoke.success') });
      setPendingRevokeId(null);
      loadConsents();
    } catch (err: unknown) {
      dispatch({ type: 'set_error', error: toSafeApiErrorState(err, t('consents.error.revoke')) });
    } finally {
      setRevokingId(null);
    }
  };

  if (loading) return <div className="loading" role="status" aria-live="polite">{t('consents.loading')}</div>;
  if (loadError) return <SafeError title={t('dashboard.errorPrefix')} message={loadError.message} correlationId={loadError.correlationId} />;

  return (
    <Card>
      <CardHeader title={t('consents.title')} subheader={t('consents.subheader')} />
      <CardContent>
        {state.successMessage && (
          <div 
            ref={successMessageRef}
            role="status" 
            className="success-message mb-4"
            tabIndex={-1}
          >
            {state.successMessage}
          </div>
        )}
        {state.formError && (
          <div ref={formErrorRef} tabIndex={-1}>
            <SafeError
              title={t('dashboard.errorPrefix')}
              message={state.formError.message}
              correlationId={state.formError.correlationId}
            />
          </div>
        )}

        <div className="stack gap-md">
          {consents.length === 0 ? <p className="empty" role="status">{t('consents.empty')}</p> : null}
          {consents.map((consent) => (
            <section key={consent.id} className="data-card">
              <div>
                <strong>{consent.recipient}</strong>
                <p className="muted">
                  {consent.purpose} - {t('consents.expires', { date: formatPhrDate(consent.expiresAt) })}
                </p>
              </div>
              <div className="row gap-sm">
                <Badge tone={consentBadgeTone[consent.status]}>{consent.status}</Badge>
                {consent.status !== 'revoked' && (
                  pendingRevokeId === consent.id ? (
                    <>
                      <span className="muted">{t('consents.revoke.confirmInline')}</span>
                      <Button
                        variant="destructive"
                        onClick={() => void handleRevoke(consent)}
                        disabled={revokingId === consent.id}
                        aria-busy={revokingId === consent.id}
                        aria-label={`${t('consents.revoke')} ${consent.recipient}`}
                      >
                        {t('consents.revoke')}
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => setPendingRevokeId(null)}
                        disabled={revokingId === consent.id}
                      >
                        {t('consents.revoke.cancel')}
                      </Button>
                    </>
                  ) : (
                    <Button
                      variant="destructive"
                      onClick={() => setPendingRevokeId(consent.id)}
                      aria-label={`${t('consents.revoke')} ${consent.recipient}`}
                    >
                      {t('consents.revoke')}
                    </Button>
                  )
                )}
              </div>
            </section>
          ))}
        </div>

        <div className="mt-6">
          {!state.showGrantForm ? (
            <Button
              variant="primary"
              onClick={() => dispatch({ type: 'open_grant_form' })}
            >
              {t('consents.grantNew')}
            </Button>
          ) : (
            <form onSubmit={(e) => void handleGrantSubmit(e)} className="stack gap-md mt-4">
              <h3 className="font-semibold">{t('consents.grant.title')}</h3>
              <Input
                aria-label={t('consents.grant.recipientId')}
                placeholder={t('consents.grant.recipientId.placeholder')}
                value={state.form.recipientId}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  dispatch({ type: 'set_field', field: 'recipientId', value: e.target.value })
                }
                required
              />
              <Input
                aria-label={t('consents.grant.purpose')}
                placeholder={t('consents.grant.purpose.placeholder')}
                value={state.form.purpose}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  dispatch({ type: 'set_field', field: 'purpose', value: e.target.value })
                }
                required
              />
              <Input
                aria-label={t('consents.grant.resourceTypes')}
                placeholder={t('consents.grant.resourceTypes.placeholder')}
                value={state.form.resourceTypes}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  dispatch({ type: 'set_field', field: 'resourceTypes', value: e.target.value })
                }
                required
              />
              <Checkbox
                checked={state.form.allDocuments}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  dispatch({ type: 'set_boolean_field', field: 'allDocuments', value: e.target.checked })
                }
                label={t('consents.grant.allDocuments')}
              />
              {!state.form.allDocuments && (
                <Input
                  aria-label={t('consents.grant.specificDocumentIds')}
                  placeholder={t('consents.grant.specificDocumentIds.placeholder')}
                  value={state.form.specificDocumentIds}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                    dispatch({ type: 'set_field', field: 'specificDocumentIds', value: e.target.value })
                  }
                />
              )}
              <Input
                aria-label={t('consents.grant.actions')}
                placeholder={t('consents.grant.actions.placeholder')}
                value={state.form.actions}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  dispatch({ type: 'set_field', field: 'actions', value: e.target.value })
                }
              />
              <Input
                aria-label={t('consents.grant.expiresAt')}
                type="date"
                value={state.form.expiresAt}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  dispatch({ type: 'set_field', field: 'expiresAt', value: e.target.value })
                }
                required
              />
              <div className="row gap-sm">
                <Button type="submit" variant="primary" disabled={state.submitting}>
                  {state.submitting ? t('appointments.submitting') : t('consents.grant.submit')}
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => dispatch({ type: 'close_grant_form' })}
                >
                  {t('consents.grant.cancel')}
                </Button>
              </div>
            </form>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

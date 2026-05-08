import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';
import { Button } from '../../components/ui/Button';

// ============================================================================
// Types
// ============================================================================

type PlanTier = 'free' | 'pro' | 'enterprise';
type PaymentStatus = 'paid' | 'pending' | 'failed';

interface PlanInfo {
  name: string;
  tier: PlanTier;
  price: number;
  billingCycle: 'monthly' | 'yearly';
  nextBillingDate: string;
  seats: { used: number; total: number };
}

interface UsageMetric {
  label: string;
  used: number;
  limit: number;
  unit: string;
}

interface PaymentRecord {
  id: string;
  date: string;
  amount: number;
  status: PaymentStatus;
  invoice: string;
  description: string;
}

interface BillingResponse {
  plan: PlanInfo;
  usage: UsageMetric[];
  payments: PaymentRecord[];
}

// ============================================================================
// Constants
// ============================================================================

const TIER_BADGE: Record<PlanTier, string> = {
  free: 'bg-surface-muted text-fg-muted',
  pro: 'bg-primary/20 text-info-color',
  enterprise: 'bg-info-bg/20 text-info-color',
};

const STATUS_BADGE: Record<PaymentStatus, string> = {
  paid: 'bg-success-bg/20 text-success-color',
  pending: 'bg-warning-bg/20 text-warning-color',
  failed: 'bg-destructive-bg/20 text-destructive',
};

// ============================================================================
// API
// ============================================================================

async function fetchBilling(): Promise<BillingResponse> {
  const res = await fetch('/api/billing', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) {
    throw new Error(await readErrorResponse(res, 'Failed to load billing'));
  }
  return parseJsonResponse<BillingResponse>(res, 'billing page');
}

// ============================================================================
// Helpers
// ============================================================================

function usageBarColor(pct: number): string {
  if (pct > 90) return 'bg-destructive-bg';
  if (pct > 70) return 'bg-warning-bg';
  return 'bg-info-bg';
}

function capitalize(str: string): string {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

// ============================================================================
// Component
// ============================================================================

/**
 * BillingPage — Billing & Plans.
 *
 * @doc.type component
 * @doc.purpose Billing dashboard with plan info, usage metrics, and payment history
 * @doc.layer product
 */
const BillingPage: React.FC = () => {
  const { data, isLoading, error } = useQuery<BillingResponse>({
    queryKey: ['billing'],
    queryFn: fetchBilling,
  });

  const plan = data?.plan;
  const usage = data?.usage ?? [];
  const payments = data?.payments ?? [];

  return (
    <div className="mx-auto max-w-5xl px-6 py-8 space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-fg-muted">Billing &amp; Plans</h1>
        <p className="mt-1 text-sm text-fg-muted">Manage your subscription, usage, and payment history</p>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-destructive-border bg-destructive-bg/20 p-4">
          <p className="text-sm text-destructive">Failed to load billing information.</p>
        </div>
      ) : (
        <>
          {/* Current Plan Card */}
          {plan && (
            <div className="bg-surface border border-border rounded-lg p-6">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <div className="flex items-center gap-3 mb-2">
                    <h2 className="text-lg font-semibold text-fg-muted">{plan.name}</h2>
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${TIER_BADGE[plan.tier]}`}
                    >
                      {capitalize(plan.tier)}
                    </span>
                  </div>
                  <p className="text-sm text-fg-muted">
                    {plan.price === 0
                      ? 'Free'
                      : `$${plan.price}/${plan.billingCycle === 'monthly' ? 'mo' : 'yr'}`}
                    {' \u00B7 '}
                    {plan.seats.used} of {plan.seats.total} seats used
                    {plan.nextBillingDate &&
                      ` \u00B7 Next billing: ${new Date(plan.nextBillingDate).toLocaleDateString()}`}
                  </p>
                </div>
                <div className="flex gap-2">
                  <Button className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-fg-muted hover:bg-surface transition-colors">
                    Manage Seats
                  </Button>
                  <Button className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-info-bg transition-colors">
                    Upgrade Plan
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* Usage Metrics */}
          {usage.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold text-fg-muted mb-4">Usage</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {usage.map((metric) => {
                  const pct = metric.limit > 0 ? Math.min((metric.used / metric.limit) * 100, 100) : 0;
                  return (
                    <div key={metric.label} className="bg-surface border border-border rounded-lg p-4">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-medium text-fg-muted">{metric.label}</span>
                        <span className="text-xs text-fg-muted">{metric.unit}</span>
                      </div>
                      <p className="text-xl font-bold text-fg-muted mb-2">
                        {metric.used.toLocaleString()}{' '}
                        <span className="text-sm font-normal text-fg-muted">
                          / {metric.limit.toLocaleString()}
                        </span>
                      </p>
                      <div className="h-1.5 w-full rounded-full bg-surface">
                        <div
                          className={`h-1.5 rounded-full ${usageBarColor(pct)} transition-all`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Payment History */}
          <div>
            <h2 className="text-lg font-semibold text-fg-muted mb-4">Payment History</h2>
            {payments.length === 0 ? (
              <div className="bg-surface border border-border rounded-lg p-8 text-center">
                <p className="text-sm text-fg-muted">No payment history.</p>
              </div>
            ) : (
              <div className="bg-surface border border-border rounded-lg overflow-hidden">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-border">
                      <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">
                        Date
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">
                        Description
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">
                        Amount
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">
                        Status
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">
                        Invoice
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-800/50">
                    {payments.map((payment) => (
                      <tr key={payment.id} className="hover:bg-surface/30 transition-colors">
                        <td className="px-4 py-3 text-sm text-fg-muted">
                          {new Date(payment.date).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3 text-sm text-fg-muted">{payment.description}</td>
                        <td className="px-4 py-3 text-sm font-medium text-fg-muted">
                          ${payment.amount.toFixed(2)}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[payment.status]}`}
                          >
                            {capitalize(payment.status)}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <Button variant="link" size="small" className="text-xs text-info-color hover:text-info-color transition-colors">
                            Download
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};

export default BillingPage;

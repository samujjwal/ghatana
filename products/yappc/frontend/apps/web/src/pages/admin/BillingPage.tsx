import React from 'react';
import { useQuery } from '@tanstack/react-query';

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
  free: 'bg-zinc-700 text-zinc-300',
  pro: 'bg-blue-600/20 text-blue-400',
  enterprise: 'bg-purple-600/20 text-purple-400',
};

const STATUS_BADGE: Record<PaymentStatus, string> = {
  paid: 'bg-green-600/20 text-green-400',
  pending: 'bg-yellow-600/20 text-yellow-400',
  failed: 'bg-red-600/20 text-red-400',
};

// ============================================================================
// API
// ============================================================================

async function fetchBilling(): Promise<BillingResponse> {
  const res = await fetch('/api/billing', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load billing');
  return res.json();
}

// ============================================================================
// Helpers
// ============================================================================

function usageBarColor(pct: number): string {
  if (pct > 90) return 'bg-red-500';
  if (pct > 70) return 'bg-yellow-500';
  return 'bg-blue-500';
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
        <h1 className="text-2xl font-bold text-zinc-100">Billing &amp; Plans</h1>
        <p className="mt-1 text-sm text-zinc-400">Manage your subscription, usage, and payment history</p>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-800 bg-red-900/20 p-4">
          <p className="text-sm text-red-400">Failed to load billing information.</p>
        </div>
      ) : (
        <>
          {/* Current Plan Card */}
          {plan && (
            <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-6">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                <div>
                  <div className="flex items-center gap-3 mb-2">
                    <h2 className="text-lg font-semibold text-zinc-100">{plan.name}</h2>
                    <span
                      className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${TIER_BADGE[plan.tier]}`}
                    >
                      {capitalize(plan.tier)}
                    </span>
                  </div>
                  <p className="text-sm text-zinc-400">
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
                  <button className="rounded-lg border border-zinc-700 px-4 py-2 text-sm font-medium text-zinc-300 hover:bg-zinc-800 transition-colors">
                    Manage Seats
                  </button>
                  <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 transition-colors">
                    Upgrade Plan
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Usage Metrics */}
          {usage.length > 0 && (
            <div>
              <h2 className="text-lg font-semibold text-zinc-100 mb-4">Usage</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                {usage.map((metric) => {
                  const pct = metric.limit > 0 ? Math.min((metric.used / metric.limit) * 100, 100) : 0;
                  return (
                    <div key={metric.label} className="bg-zinc-900 border border-zinc-800 rounded-lg p-4">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-sm font-medium text-zinc-300">{metric.label}</span>
                        <span className="text-xs text-zinc-500">{metric.unit}</span>
                      </div>
                      <p className="text-xl font-bold text-zinc-100 mb-2">
                        {metric.used.toLocaleString()}{' '}
                        <span className="text-sm font-normal text-zinc-500">
                          / {metric.limit.toLocaleString()}
                        </span>
                      </p>
                      <div className="h-1.5 w-full rounded-full bg-zinc-800">
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
            <h2 className="text-lg font-semibold text-zinc-100 mb-4">Payment History</h2>
            {payments.length === 0 ? (
              <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-8 text-center">
                <p className="text-sm text-zinc-500">No payment history.</p>
              </div>
            ) : (
              <div className="bg-zinc-900 border border-zinc-800 rounded-lg overflow-hidden">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-zinc-800">
                      <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 uppercase tracking-wider">
                        Date
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 uppercase tracking-wider">
                        Description
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 uppercase tracking-wider">
                        Amount
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 uppercase tracking-wider">
                        Status
                      </th>
                      <th className="px-4 py-3 text-left text-xs font-medium text-zinc-500 uppercase tracking-wider">
                        Invoice
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-800/50">
                    {payments.map((payment) => (
                      <tr key={payment.id} className="hover:bg-zinc-800/30 transition-colors">
                        <td className="px-4 py-3 text-sm text-zinc-300">
                          {new Date(payment.date).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3 text-sm text-zinc-300">{payment.description}</td>
                        <td className="px-4 py-3 text-sm font-medium text-zinc-200">
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
                          <button className="text-xs text-blue-400 hover:text-blue-300 transition-colors">
                            Download
                          </button>
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

/**
 * Tenant Governance Page (DC-P3-005)
 *
 * Tenant-level cost and resource governance view.
 *
 * @doc.type page
 * @doc.purpose Surface tenant cost posture and resource governance signals
 * @doc.layer frontend
 * @doc.pattern Dashboard
 */

import { useQuery } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { costService } from "../api/cost.service";
import { governanceService } from "../api/governance.service";

interface TenantGovernanceSnapshot {
  totalCost: number;
  currency: string;
  datasetCount: number;
  highestDatasetCost: number;
  complianceScore: number;
  collectionsTotal: number;
  collectionsClassified: number;
  collectionsUnclassified: number;
  activeLegalHolds: number;
  totalPiiFields: number;
  auditEventsIn30Days: number;
  redactionsIn30Days: number;
  purgesIn30Days: number;
}

export function TenantGovernancePage(): ReactElement {
  const snapshotQuery = useQuery({
    queryKey: ["tenant-governance-snapshot"],
    queryFn: async (): Promise<TenantGovernanceSnapshot> => {
      const [cost, compliance, inventory] = await Promise.all([
        costService.getCostAnalysis("30d"),
        governanceService.getComplianceReport("30d"),
        governanceService.getGovernanceInventory(),
      ]);

      const highestDatasetCost = cost.byDataset.reduce(
        (max, dataset) => Math.max(max, dataset.cost),
        0,
      );

      return {
        totalCost: cost.total,
        currency: cost.currency,
        datasetCount: cost.byDataset.length,
        highestDatasetCost,
        complianceScore: compliance.summary.complianceScore,
        collectionsTotal: inventory.collectionsTotal,
        collectionsClassified: inventory.collectionsClassified,
        collectionsUnclassified: inventory.collectionsUnclassified,
        activeLegalHolds: inventory.activeLegalHolds,
        totalPiiFields: inventory.totalPiiFields,
        auditEventsIn30Days: inventory.auditEventsIn30Days ?? 0,
        redactionsIn30Days: inventory.redactionsIn30Days ?? 0,
        purgesIn30Days: inventory.purgesIn30Days ?? 0,
      };
    },
    retry: false,
    staleTime: 30_000,
  });

  return (
    <main
      className="min-h-screen bg-gray-50 dark:bg-gray-950 p-6"
      data-testid="tenant-governance-page"
    >
      <div className="mx-auto max-w-6xl space-y-6">
        <header>
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">
            Tenant Cost and Resource Governance
          </h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Cross-plane governance snapshot combining spend posture, policy
            coverage, and privacy controls.
          </p>
        </header>

        {snapshotQuery.isLoading && (
          <div className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-400">
            Loading tenant governance snapshot...
          </div>
        )}

        {snapshotQuery.isError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900/40 dark:bg-rose-900/10 dark:text-rose-300">
            {snapshotQuery.error instanceof Error
              ? snapshotQuery.error.message
              : "Failed to load tenant governance view."}
          </div>
        )}

        {snapshotQuery.data && (
          <>
            <section className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
              <article className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
                <div className="text-xs uppercase tracking-wide text-gray-500">
                  30d spend
                </div>
                <div className="mt-2 text-xl font-semibold text-gray-900 dark:text-white">
                  {snapshotQuery.data.currency}{" "}
                  {Math.round(snapshotQuery.data.totalCost * 100) / 100}
                </div>
                <div className="mt-1 text-xs text-gray-500">
                  Across {snapshotQuery.data.datasetCount} datasets
                </div>
              </article>

              <article className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
                <div className="text-xs uppercase tracking-wide text-gray-500">
                  Compliance score
                </div>
                <div className="mt-2 text-xl font-semibold text-gray-900 dark:text-white">
                  {snapshotQuery.data.complianceScore}%
                </div>
                <div className="mt-1 text-xs text-gray-500">
                  Derived from governance summary
                </div>
              </article>

              <article className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
                <div className="text-xs uppercase tracking-wide text-gray-500">
                  Collections classified
                </div>
                <div className="mt-2 text-xl font-semibold text-gray-900 dark:text-white">
                  {snapshotQuery.data.collectionsClassified}/
                  {snapshotQuery.data.collectionsTotal}
                </div>
                <div className="mt-1 text-xs text-gray-500">
                  {snapshotQuery.data.collectionsUnclassified} unclassified
                </div>
              </article>

              <article className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
                <div className="text-xs uppercase tracking-wide text-gray-500">
                  Legal holds
                </div>
                <div className="mt-2 text-xl font-semibold text-gray-900 dark:text-white">
                  {snapshotQuery.data.activeLegalHolds}
                </div>
                <div className="mt-1 text-xs text-gray-500">
                  PII fields tracked: {snapshotQuery.data.totalPiiFields}
                </div>
              </article>
            </section>

            <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
              <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100">
                Governance activity (last 30 days)
              </h2>
              <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-3 text-sm">
                <div className="rounded-lg border border-gray-100 p-3 dark:border-gray-800">
                  <div className="text-gray-500">Audit events</div>
                  <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">
                    {snapshotQuery.data.auditEventsIn30Days}
                  </div>
                </div>
                <div className="rounded-lg border border-gray-100 p-3 dark:border-gray-800">
                  <div className="text-gray-500">Redactions</div>
                  <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">
                    {snapshotQuery.data.redactionsIn30Days}
                  </div>
                </div>
                <div className="rounded-lg border border-gray-100 p-3 dark:border-gray-800">
                  <div className="text-gray-500">Retention purges</div>
                  <div className="mt-1 font-medium text-gray-900 dark:text-gray-100">
                    {snapshotQuery.data.purgesIn30Days}
                  </div>
                </div>
              </div>
            </section>
          </>
        )}
      </div>
    </main>
  );
}

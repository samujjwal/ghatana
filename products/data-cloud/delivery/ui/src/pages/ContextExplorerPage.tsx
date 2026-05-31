/**
 * ContextExplorerPage
 *
 * Collection-scoped context explorer for schema, lineage, governance,
 * freshness, and relationship traversal.
 *
 * @doc.type page
 * @doc.purpose Unified context surface for collection-aware exploration
 * @doc.layer frontend
 * @doc.pattern Page
 */

import { useQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  Clock3,
  Database,
  GitBranch,
  Network,
  RefreshCw,
  Search,
  Shield,
  Sparkles,
} from "lucide-react";
import React, {
  startTransition,
  useDeferredValue,
  useEffect,
  useMemo,
  useState,
} from "react";
import { useSearchParams } from "react-router";
import { lineageService } from "../api/lineage.service";
import { LineageGraph } from "../components/lineage/LineageGraph";
import type { CollectionContextResponse } from "../contracts/schemas";
import { collectionsApi, type Collection } from "../lib/api/collections";
import { getCollectionContext } from "../lib/api/context";

const DEPTH_OPTIONS = [1, 2, 3] as const;

function normalizeDepth(rawDepth: string | null): 1 | 2 | 3 {
  if (rawDepth === "2") {
    return 2;
  }
  if (rawDepth === "3") {
    return 3;
  }
  return 1;
}

function formatTimestamp(value?: string): string {
  if (!value) {
    return "Not available";
  }
  return new Date(value).toLocaleString();
}

function StatCard({
  title,
  value,
  detail,
  icon,
}: {
  title: string;
  value: string;
  detail: string;
  icon: React.ReactNode;
}): React.ReactElement {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
            {title}
          </p>
          <p className="mt-2 text-2xl font-semibold text-slate-950">{value}</p>
          <p className="mt-1 text-sm text-slate-600">{detail}</p>
        </div>
        <div className="rounded-2xl bg-amber-50 p-3 text-amber-700">{icon}</div>
      </div>
    </div>
  );
}

function CollectionList({
  collections,
  selectedCollection,
  onSelect,
}: {
  collections: Collection[];
  selectedCollection: string | null;
  onSelect: (collectionId: string) => void;
}): React.ReactElement {
  return (
    <div className="space-y-3">
      {collections.map((collection) => {
        const active = collection.id === selectedCollection;
        return (
          <button
            key={collection.id}
            type="button"
            onClick={() => onSelect(collection.id)}
            className={[
              "w-full rounded-2xl border p-4 text-left transition-colors",
              active
                ? "border-slate-900 bg-slate-900 text-white shadow-lg shadow-slate-300/50"
                : "border-slate-200 bg-white text-slate-900 hover:border-amber-300 hover:bg-amber-50",
            ].join(" ")}
          >
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-semibold">{collection.name}</div>
                <div
                  className={
                    active
                      ? "mt-1 text-xs text-slate-300"
                      : "mt-1 text-xs text-slate-500"
                  }
                >
                  {collection.description || "No description"}
                </div>
              </div>
              <ArrowRight
                className={
                  active ? "h-4 w-4 text-amber-300" : "h-4 w-4 text-slate-400"
                }
              />
            </div>
            <div
              className={
                active
                  ? "mt-3 flex gap-3 text-xs text-slate-200"
                  : "mt-3 flex gap-3 text-xs text-slate-500"
              }
            >
              <span>{collection.schema.fields.length} fields</span>
              <span>{collection.entityCount.toLocaleString()} rows</span>
              <span>{collection.schemaType}</span>
            </div>
          </button>
        );
      })}
    </div>
  );
}

function SectionCard({
  title,
  subtitle,
  icon,
  children,
}: {
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-5 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
          <p className="mt-1 text-sm text-slate-600">{subtitle}</p>
        </div>
        <div className="rounded-2xl bg-slate-100 p-3 text-slate-700">
          {icon}
        </div>
      </div>
      {children}
    </section>
  );
}

export function ContextExplorerPage(): React.ReactElement {
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchValue, setSearchValue] = useState("");
  const deferredSearchValue = useDeferredValue(searchValue);
  const selectedCollection = searchParams.get("collection");
  const depth = normalizeDepth(searchParams.get("depth"));

  const collectionsQuery = useQuery({
    queryKey: ["context-explorer", "collections", deferredSearchValue],
    queryFn: () =>
      collectionsApi.list({
        search: deferredSearchValue || undefined,
        pageSize: 24,
      }),
    staleTime: 60_000,
  });

  const collections = useMemo(
    () => collectionsQuery.data?.items ?? [],
    [collectionsQuery.data?.items],
  );

  useEffect(() => {
    if (!selectedCollection && collections.length > 0) {
      startTransition(() => {
        const nextParams = new URLSearchParams(searchParams);
        nextParams.set("collection", collections[0].id);
        nextParams.set("depth", String(depth));
        setSearchParams(nextParams, { replace: true });
      });
    }
  }, [collections, depth, searchParams, selectedCollection, setSearchParams]);

  const contextQuery = useQuery<CollectionContextResponse>({
    queryKey: ["context-explorer", "context", selectedCollection, depth],
    queryFn: () => getCollectionContext(selectedCollection!, { depth }),
    enabled: selectedCollection !== null,
    staleTime: 30_000,
  });

  const lineageQuery = useQuery({
    queryKey: ["context-explorer", "lineage", selectedCollection, depth],
    queryFn: () =>
      lineageService.getLineage(selectedCollection!, "BOTH", depth),
    enabled: selectedCollection !== null,
    staleTime: 30_000,
  });

  const handleCollectionSelect = (collectionId: string) => {
    startTransition(() => {
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set("collection", collectionId);
      nextParams.set("depth", String(depth));
      setSearchParams(nextParams);
    });
  };

  const handleDepthChange = (nextDepth: 1 | 2 | 3) => {
    startTransition(() => {
      const nextParams = new URLSearchParams(searchParams);
      if (selectedCollection) {
        nextParams.set("collection", selectedCollection);
      }
      nextParams.set("depth", String(nextDepth));
      setSearchParams(nextParams);
    });
  };

  const context = contextQuery.data;
  const relationships = context?.relationships ?? [];
  const schemaFields = context?.schema.fields ?? [];

  return (
    <div className="min-h-screen bg-gray-50 p-6 md:p-8">
      <div className="mx-auto max-w-7xl space-y-6">
        <header className="rounded-[2rem] border border-amber-200/70 bg-white/90 p-6 shadow-sm backdrop-blur">
          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div className="max-w-3xl">
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-amber-700">
                Context-Native Data Fabric
              </p>
              <h1 className="mt-3 text-3xl font-semibold tracking-tight text-slate-950 md:text-4xl">
                Context Explorer
              </h1>
              <p className="mt-3 text-base leading-7 text-slate-600">
                Inspect one collection through a single context surface: schema,
                lineage, governance, freshness, and graph relationships with
                traversal depth up to three hops.
              </p>
            </div>

            <div className="flex flex-col gap-3 md:min-w-[320px]">
              <label
                className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"
                htmlFor="context-search"
              >
                Search collections
              </label>
              <div className="flex items-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
                <Search className="h-4 w-4 text-slate-400" />
                <input
                  id="context-search"
                  value={searchValue}
                  onChange={(event) => setSearchValue(event.target.value)}
                  className="w-full border-0 bg-transparent p-0 text-sm text-slate-900 outline-none"
                  placeholder="orders, customers, invoices"
                />
              </div>
            </div>
          </div>

          <div className="mt-5 flex flex-wrap items-center gap-3">
            <span className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
              Traversal depth
            </span>
            {DEPTH_OPTIONS.map((option) => (
              <button
                key={option}
                type="button"
                onClick={() => handleDepthChange(option)}
                className={[
                  "rounded-full px-4 py-2 text-sm font-medium transition-colors",
                  depth === option
                    ? "bg-slate-900 text-white"
                    : "bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50",
                ].join(" ")}
              >
                {option} hop{option > 1 ? "s" : ""}
              </button>
            ))}
            <button
              type="button"
              onClick={() => {
                void collectionsQuery.refetch();
                void contextQuery.refetch();
                void lineageQuery.refetch();
              }}
              className="ml-auto inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              <RefreshCw className="h-4 w-4" />
              Refresh context
            </button>
          </div>
        </header>

        <div className="grid gap-6 xl:grid-cols-[320px,1fr]">
          <aside className="space-y-4">
            <SectionCard
              title="Collections"
              subtitle="Choose the collection that drives the context graph."
              icon={<Database className="h-5 w-5" />}
            >
              {collectionsQuery.isLoading ? (
                <p className="text-sm text-slate-500">Loading collections…</p>
              ) : collections.length === 0 ? (
                <p className="text-sm text-slate-500">
                  No collections matched this search.
                </p>
              ) : (
                <CollectionList
                  collections={collections}
                  selectedCollection={selectedCollection}
                  onSelect={handleCollectionSelect}
                />
              )}
            </SectionCard>
          </aside>

          <section className="space-y-6">
            {contextQuery.isLoading || !context ? (
              <SectionCard
                title="Collection context"
                subtitle="Waiting for a collection selection or API response."
                icon={<Sparkles className="h-5 w-5" />}
              >
                <p className="text-sm text-slate-500">
                  Loading unified context…
                </p>
              </SectionCard>
            ) : (
              <>
                <div className="grid gap-4 md:grid-cols-3">
                  <StatCard
                    title="Entities"
                    value={context.statisticalProfile.entityCount.toLocaleString()}
                    detail={`${context.statisticalProfile.sampleSize.toLocaleString()} sampled in the latest context pass`}
                    icon={<Database className="h-5 w-5" />}
                  />
                  <StatCard
                    title="Governance"
                    value={context.governance.retentionTier}
                    detail={`${context.governance.piiFields.length} PII field(s) flagged`}
                    icon={<Shield className="h-5 w-5" />}
                  />
                  <StatCard
                    title="Freshness"
                    value={`${context.generationTimeMs}ms`}
                    detail={`Sampled ${formatTimestamp(context.freshness.sampledAt)}`}
                    icon={<Clock3 className="h-5 w-5" />}
                  />
                </div>

                <div className="grid gap-6 2xl:grid-cols-[1.1fr,0.9fr]">
                  <SectionCard
                    title="Schema and governance"
                    subtitle="Field definitions, compliance tier, and freshness metadata in one surface."
                    icon={<Shield className="h-5 w-5" />}
                  >
                    <div className="grid gap-4 lg:grid-cols-[1.3fr,0.7fr]">
                      <div className="overflow-hidden rounded-2xl border border-slate-200">
                        <table className="min-w-full divide-y divide-slate-200 text-sm">
                          <thead className="bg-slate-50 text-left text-xs uppercase tracking-[0.18em] text-slate-500">
                            <tr>
                              <th className="px-4 py-3">Field</th>
                              <th className="px-4 py-3">Type</th>
                              <th className="px-4 py-3">Required</th>
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100 bg-white">
                            {schemaFields.map((field) => (
                              <tr key={`${context.collection}-${field.name}`}>
                                <td className="px-4 py-3 font-medium text-slate-900">
                                  {field.name}
                                </td>
                                <td className="px-4 py-3 text-slate-600">
                                  {field.type}
                                </td>
                                <td className="px-4 py-3 text-slate-600">
                                  {field.required ? "Yes" : "No"}
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>

                      <div className="space-y-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                            Compliance
                          </p>
                          <p className="mt-2 text-sm font-medium text-slate-900">
                            {context.governance.complianceStatus}
                          </p>
                          <p className="mt-1 text-sm text-slate-600">
                            {context.governance.policyReason ??
                              "No custom retention reason attached."}
                          </p>
                        </div>
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                            PII fields
                          </p>
                          <div className="mt-2 flex flex-wrap gap-2">
                            {context.governance.piiFields.length === 0 ? (
                              <span className="rounded-full bg-white px-3 py-1 text-xs text-slate-500 ring-1 ring-slate-200">
                                None detected
                              </span>
                            ) : (
                              context.governance.piiFields.map((field) => (
                                <span
                                  key={field}
                                  className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200"
                                >
                                  {field}
                                </span>
                              ))
                            )}
                          </div>
                        </div>
                        <div>
                          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                            Last entity update
                          </p>
                          <p className="mt-2 text-sm text-slate-700">
                            {formatTimestamp(
                              context.freshness.lastEntityUpdatedAt,
                            )}
                          </p>
                        </div>
                      </div>
                    </div>
                  </SectionCard>

                  <SectionCard
                    title="Relationship traversal"
                    subtitle={`Current response depth: ${context.relationshipDepth ?? depth} hop${(context.relationshipDepth ?? depth) > 1 ? "s" : ""}.`}
                    icon={<Network className="h-5 w-5" />}
                  >
                    {relationships.length === 0 ? (
                      <p className="text-sm text-slate-500">
                        No relationships were returned for this collection.
                      </p>
                    ) : (
                      <div className="space-y-3">
                        {relationships.map((relationship) => (
                          <div
                            key={relationship.id}
                            className="rounded-2xl border border-slate-200 bg-slate-50 p-4"
                          >
                            <div className="flex items-center justify-between gap-3">
                              <div>
                                <p className="text-sm font-semibold text-slate-900">
                                  {relationship.source}{" "}
                                  <span className="text-slate-400">→</span>{" "}
                                  {relationship.target}
                                </p>
                                <p className="mt-1 text-sm text-slate-600">
                                  {relationship.type}
                                </p>
                              </div>
                              <span className="rounded-full bg-white px-3 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200">
                                Depth{" "}
                                {relationship.depth ??
                                  context.relationshipDepth ??
                                  depth}
                              </span>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </SectionCard>
                </div>

                <SectionCard
                  title="Lineage graph"
                  subtitle="Launcher lineage and context traversal rendered together for the selected collection."
                  icon={<GitBranch className="h-5 w-5" />}
                >
                  {lineageQuery.isLoading || !lineageQuery.data ? (
                    <p className="text-sm text-slate-500">
                      Loading lineage graph…
                    </p>
                  ) : (
                    <LineageGraph
                      nodes={lineageQuery.data.nodes}
                      edges={lineageQuery.data.edges}
                      rootNode={lineageQuery.data.rootNode}
                      height="420px"
                    />
                  )}
                </SectionCard>
              </>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

export default ContextExplorerPage;

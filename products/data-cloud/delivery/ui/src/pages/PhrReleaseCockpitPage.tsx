/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * PhrReleaseCockpitPage — PHR product release readiness management UI.
 *
 * Features:
 * - Release readiness overview with statistics
 * - Release history by target (production, staging, development)
 * - Evidence inspection for each release
 * - Blocking gaps and below-target dimensions
 * - Release verdict tracking
 *
 * @doc.purpose PHR release readiness cockpit UI
 * @doc.layer data-cloud-ui
 */

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  releaseReadinessService,
  type ReleaseReadiness,
  type ReleaseReadinessStats,
} from "../api/release-readiness.service";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../components/ui/select";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "../components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../components/ui/table";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "../components/ui/sheet";
import {
  CheckCircle2,
  XCircle,
  Clock,
  AlertTriangle,
  TrendingUp,
  Filter,
  Info,
  FileText,
  Shield,
  Database,
  Code,
} from "lucide-react";

interface PhrReleaseCockpitPageProps {
  tenantId: string;
}

export function PhrReleaseCockpitPage({ tenantId }: PhrReleaseCockpitPageProps) {
  const [activeTab, setActiveTab] = useState("overview");
  const [selectedRelease, setSelectedRelease] = useState<ReleaseReadiness | null>(null);
  const [filterTarget, setFilterTarget] = useState<"ALL" | "production" | "staging" | "development">("ALL");
  const [filterVerdict, setFilterVerdict] = useState<"ALL" | "pass" | "fail">("ALL");

  // Fetch release readiness statistics
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ["phr-release-stats", tenantId],
    queryFn: () => releaseReadinessService.getReleaseReadinessStats(tenantId),
  });

  // Fetch PHR release readiness records
  const { data: releases, isLoading: releasesLoading } = useQuery({
    queryKey: ["phr-releases", tenantId, filterTarget, filterVerdict],
    queryFn: () =>
      releaseReadinessService.listReleaseReadiness({
        productId: "phr",
        releaseTarget: filterTarget === "ALL" ? undefined : filterTarget,
        releaseVerdict: filterVerdict === "ALL" ? undefined : filterVerdict,
        tenantId,
        limit: 50,
      }),
  });

  const filteredReleases = releases?.filter((release) => {
    if (filterTarget !== "ALL" && release.releaseTarget !== filterTarget) {
      return false;
    }
    if (filterVerdict !== "ALL" && release.releaseVerdict !== filterVerdict) {
      return false;
    }
    return true;
  }) || [];

  const phrStats = stats?.byProduct?.["phr"] || { total: 0, passed: 0, failed: 0 };
  const passRate = phrStats.total > 0 ? (phrStats.passed / phrStats.total) * 100 : 0;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">PHR Release Cockpit</h1>
          <p className="text-muted-foreground">
            Monitor and manage PHR product release readiness evidence
          </p>
        </div>
        <Badge variant="outline" className="text-sm">
          Tenant: {tenantId}
        </Badge>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="history">Release History</TabsTrigger>
          <TabsTrigger value="evidence">Evidence</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-6">
          {/* Statistics Cards */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Releases</CardTitle>
                <FileText className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{phrStats.total}</div>
                <p className="text-xs text-muted-foreground">
                  Across all targets
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Passed</CardTitle>
                <CheckCircle2 className="h-4 w-4 text-green-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{phrStats.passed}</div>
                <p className="text-xs text-muted-foreground">
                  {passRate.toFixed(1)}% pass rate
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Failed</CardTitle>
                <XCircle className="h-4 w-4 text-red-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{phrStats.failed}</div>
                <p className="text-xs text-muted-foreground">
                  Require attention
                </p>
              </CardContent>
            </Card>

            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Average Score</CardTitle>
                <TrendingUp className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stats?.averageScore ? (stats.averageScore * 100).toFixed(0) : "N/A"}%
                </div>
                <p className="text-xs text-muted-foreground">
                  Overall quality score
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Evidence Categories Summary */}
          <Card>
            <CardHeader>
              <CardTitle>Evidence Categories</CardTitle>
              <CardDescription>
                PHR release readiness evidence by category
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {[
                  { name: "Build", icon: Code, status: "passed" },
                  { name: "Test", icon: Shield, status: "passed" },
                  { name: "API", icon: FileText, status: "passed" },
                  { name: "FHIR", icon: Database, status: "passed" },
                  { name: "Consent", icon: Shield, status: "passed" },
                  { name: "Audit", icon: FileText, status: "passed" },
                  { name: "Tenant", icon: Shield, status: "passed" },
                  { name: "Cache", icon: Database, status: "passed" },
                  { name: "Rollback", icon: Code, status: "passed" },
                  { name: "Deployment", icon: FileText, status: "passed" },
                ].map((category) => (
                  <div
                    key={category.name}
                    className="flex items-center space-x-2 p-3 border rounded-lg"
                  >
                    <category.icon className="h-4 w-4 text-muted-foreground" />
                    <span className="flex-1 text-sm font-medium">{category.name}</span>
                    <Badge
                      variant={category.status === "passed" ? "default" : "destructive"}
                    >
                      {category.status}
                    </Badge>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="history" className="space-y-6">
          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2">
              <Filter className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-medium">Filter:</span>
            </div>
            <Select value={filterTarget} onValueChange={(v: any) => setFilterTarget(v)}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="Release Target" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Targets</SelectItem>
                <SelectItem value="production">Production</SelectItem>
                <SelectItem value="staging">Staging</SelectItem>
                <SelectItem value="development">Development</SelectItem>
              </SelectContent>
            </Select>
            <Select value={filterVerdict} onValueChange={(v: any) => setFilterVerdict(v)}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="Verdict" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All Verdicts</SelectItem>
                <SelectItem value="pass">Passed</SelectItem>
                <SelectItem value="fail">Failed</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Release History</CardTitle>
              <CardDescription>
                PHR release readiness records
              </CardDescription>
            </CardHeader>
            <CardContent>
              {releasesLoading ? (
                <div className="text-center py-8 text-muted-foreground">
                  Loading release history...
                </div>
              ) : filteredReleases.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">
                  No release records found
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Version</TableHead>
                      <TableHead>Target</TableHead>
                      <TableHead>Verdict</TableHead>
                      <TableHead>Score</TableHead>
                      <TableHead>Generated At</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredReleases.map((release) => (
                      <TableRow key={release.id}>
                        <TableCell className="font-medium">
                          {release.productVersion}
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{release.releaseTarget}</Badge>
                        </TableCell>
                        <TableCell>
                          <Badge
                            variant={
                              release.releaseVerdict === "pass"
                                ? "default"
                                : "destructive"
                            }
                          >
                            {release.releaseVerdict}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {release.averageScore
                            ? `${(release.averageScore * 100).toFixed(0)}%`
                            : "N/A"}
                        </TableCell>
                        <TableCell>
                          {new Date(release.generatedAt).toLocaleString()}
                        </TableCell>
                        <TableCell>
                          <Sheet>
                            <SheetTrigger asChild>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setSelectedRelease(release)}
                              >
                                <Info className="h-4 w-4 mr-2" />
                                Details
                              </Button>
                            </SheetTrigger>
                            <SheetContent>
                              <SheetHeader>
                                <SheetTitle>Release Details</SheetTitle>
                                <SheetDescription>
                                  Release readiness evidence for PHR version{" "}
                                  {release.productVersion}
                                </SheetDescription>
                              </SheetHeader>
                              <div className="mt-6 space-y-4">
                                <div>
                                  <h4 className="font-semibold mb-2">Blocking Gaps</h4>
                                  {release.blockingGaps.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">
                                      No blocking gaps
                                    </p>
                                  ) : (
                                    <ul className="space-y-2">
                                      {release.blockingGaps.map((gap, idx) => (
                                        <li
                                          key={idx}
                                          className="text-sm p-2 bg-red-50 border border-red-200 rounded"
                                        >
                                          <pre className="text-xs overflow-auto">
                                            {JSON.stringify(gap, null, 2)}
                                          </pre>
                                        </li>
                                      ))}
                                    </ul>
                                  )}
                                </div>
                                <div>
                                  <h4 className="font-semibold mb-2">
                                    Below Target Dimensions
                                  </h4>
                                  {release.belowTargetDimensions.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">
                                      All dimensions meet target
                                    </p>
                                  ) : (
                                    <ul className="space-y-2">
                                      {release.belowTargetDimensions.map(
                                        (dim, idx) => (
                                          <li
                                            key={idx}
                                            className="text-sm p-2 bg-yellow-50 border border-yellow-200 rounded"
                                          >
                                            <pre className="text-xs overflow-auto">
                                              {JSON.stringify(dim, null, 2)}
                                            </pre>
                                          </li>
                                        )
                                      )}
                                    </ul>
                                  )}
                                </div>
                                <div>
                                  <h4 className="font-semibold mb-2">Full Evidence</h4>
                                  <pre className="text-xs bg-muted p-4 rounded overflow-auto max-h-96">
                                    {JSON.stringify(release.evidence, null, 2)}
                                  </pre>
                                </div>
                              </div>
                            </SheetContent>
                          </Sheet>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="evidence" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Evidence Inspection</CardTitle>
              <CardDescription>
                Detailed evidence for PHR release readiness
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="text-center py-8 text-muted-foreground">
                Select a release from the history tab to view detailed evidence
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

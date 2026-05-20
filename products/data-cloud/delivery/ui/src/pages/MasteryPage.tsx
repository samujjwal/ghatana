/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * MasteryPage — Master agent learning and mastery state management.
 *
 * Features:
 * - Mastery state overview with statistics
 * - Learning deltas queue for review and approval
 * - Obsolete items list for cleanup
 * - Promotion queue for L3+ procedural skills
 *
 * @doc.purpose Mastery and learning management UI
 * @doc.layer data-cloud-ui
 */

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  masteryService,
  type MasteryItem,
  type LearningDelta,
  type ObsolescenceEvent,
  type PromotionQueueItem,
  type MasteryState,
} from "../api/mastery.service";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "../components/ui/card";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
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
} from "lucide-react";
import { emitDataCloudDiagnostic } from "../diagnostics";

interface MasteryPageProps {
  tenantId: string;
}

export function MasteryPage({ tenantId }: MasteryPageProps) {
  const [activeTab, setActiveTab] = useState("overview");
  const [selectedItem, setSelectedItem] = useState<MasteryItem | null>(null);
  const [filterState, setFilterState] = useState<MasteryState | "ALL">("ALL");
  const [filterSkillId, setFilterSkillId] = useState("");
  const [filterAgentId, setFilterAgentId] = useState("");

  // Fetch mastery statistics
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ["mastery-stats", tenantId],
    queryFn: () => masteryService.getMasteryStats(tenantId),
  });

  // Fetch mastery items
  const { data: masteryItems, isLoading: masteryLoading } = useQuery({
    queryKey: ["mastery-items", tenantId],
    queryFn: () => masteryService.queryMastery({ tenantId, limit: 50 }),
  });

  // Fetch pending learning deltas
  const { data: pendingDeltas, isLoading: deltasLoading } = useQuery({
    queryKey: ["pending-deltas", tenantId],
    queryFn: () =>
      masteryService.queryLearningDeltas(tenantId, "PENDING_EVALUATION"),
  });

  // Fetch obsolescence events
  const { data: obsoleteItems, isLoading: obsoleteLoading } = useQuery({
    queryKey: ["obsolete-items", tenantId],
    queryFn: () => masteryService.queryObsolescenceEvents(tenantId),
  });

  // Fetch promotion queue
  const { data: promotionQueue, isLoading: promotionLoading } = useQuery({
    queryKey: ["promotion-queue", tenantId],
    queryFn: () => masteryService.getPromotionQueue(tenantId),
  });

  const handleApprove = async (deltaId: string) => {
    try {
      await masteryService.approveDelta(deltaId, "Approved by admin");
      // Refetch data
      window.location.reload();
    } catch (error) {
      emitDataCloudDiagnostic("MasteryPage", "error", "Failed to approve delta", {
        deltaId,
        error,
      });
    }
  };

  const handleReject = async (deltaId: string) => {
    try {
      await masteryService.rejectDelta(deltaId, "Rejected by admin");
      window.location.reload();
    } catch (error) {
      emitDataCloudDiagnostic("MasteryPage", "error", "Failed to reject delta", {
        deltaId,
        error,
      });
    }
  };

  const handlePromote = async (deltaId: string) => {
    try {
      await masteryService.promoteDelta(deltaId);
      window.location.reload();
    } catch (error) {
      emitDataCloudDiagnostic("MasteryPage", "error", "Failed to promote delta", {
        deltaId,
        error,
      });
    }
  };

  // Filter mastery items based on filters
  const filteredMasteryItems =
    masteryItems?.filter((item) => {
      if (filterState !== "ALL" && item.state !== filterState) return false;
      if (
        filterSkillId &&
        !item.skillId.toLowerCase().includes(filterSkillId.toLowerCase())
      )
        return false;
      if (
        filterAgentId &&
        item.agentId &&
        !item.agentId.toLowerCase().includes(filterAgentId.toLowerCase())
      )
        return false;
      return true;
    }) || [];

  // Get state badge color
  const getStateBadgeVariant = (state: MasteryState) => {
    switch (state) {
      case "MASTERED":
      case "COMPETENT":
        return "default";
      case "PRACTICED":
      case "OBSERVED":
        return "secondary";
      case "MAINTENANCE_ONLY":
        return "outline";
      case "OBSOLETE":
      case "RETIRED":
      case "QUARANTINED":
        return "destructive";
      default:
        return "secondary";
    }
  };

  if (statsLoading) {
    return <div className="p-8">Loading mastery data...</div>;
  }

  return (
    <div className="container mx-auto p-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold">Mastery & Learning</h1>
        <p className="text-muted-foreground">
          Manage agent mastery state, learning deltas, and promotion queue
        </p>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="deltas">Learning Deltas</TabsTrigger>
          <TabsTrigger value="obsolete">Obsolete Items</TabsTrigger>
          <TabsTrigger value="promotion">Promotion Queue</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-6">
          {/* Statistics Cards */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Total Mastery Items
                </CardTitle>
                <TrendingUp className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats?.total || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Known Skills
                </CardTitle>
                <CheckCircle2 className="h-4 w-4 text-green-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stats?.byState?.KNOWN || 0}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Pending Deltas
                </CardTitle>
                <Clock className="h-4 w-4 text-yellow-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stats?.pendingDeltas || 0}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Obsolete Items
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-red-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">
                  {stats?.obsoleteItems || 0}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Mastery Items Table */}
          <Card>
            <CardHeader>
              <CardTitle>Mastery Items</CardTitle>
              <CardDescription>
                Current mastery state across all skills
              </CardDescription>
            </CardHeader>
            <CardContent>
              {/* Filters */}
              <div className="flex flex-wrap gap-4 mb-4">
                <div className="flex items-center gap-2">
                  <Filter className="h-4 w-4 text-muted-foreground" />
                  <span className="text-sm font-medium">Filters:</span>
                </div>
                <Input
                  placeholder="Filter by Skill ID"
                  value={filterSkillId}
                  onChange={(e) => setFilterSkillId(e.target.value)}
                  className="max-w-xs"
                />
                <Input
                  placeholder="Filter by Agent ID"
                  value={filterAgentId}
                  onChange={(e) => setFilterAgentId(e.target.value)}
                  className="max-w-xs"
                />
                <Select
                  value={filterState}
                  onValueChange={(value) =>
                    setFilterState(value as MasteryState | "ALL")
                  }
                >
                  <SelectTrigger className="w-[200px]">
                    <SelectValue placeholder="Filter by state" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">All States</SelectItem>
                    <SelectItem value="UNKNOWN">UNKNOWN</SelectItem>
                    <SelectItem value="OBSERVED">OBSERVED</SelectItem>
                    <SelectItem value="PRACTICED">PRACTICED</SelectItem>
                    <SelectItem value="COMPETENT">COMPETENT</SelectItem>
                    <SelectItem value="MASTERED">MASTERED</SelectItem>
                    <SelectItem value="MAINTENANCE_ONLY">
                      MAINTENANCE_ONLY
                    </SelectItem>
                    <SelectItem value="OBSOLETE">OBSOLETE</SelectItem>
                    <SelectItem value="RETIRED">RETIRED</SelectItem>
                    <SelectItem value="QUARANTINED">QUARANTINED</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {masteryLoading ? (
                <div>Loading...</div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Skill ID</TableHead>
                      <TableHead>Agent ID</TableHead>
                      <TableHead>State</TableHead>
                      <TableHead>Learning Level</TableHead>
                      <TableHead>Evidence Count</TableHead>
                      <TableHead>Last Transitioned</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredMasteryItems.map((item) => (
                      <TableRow key={item.masteryId}>
                        <TableCell className="font-medium">
                          {item.skillId}
                        </TableCell>
                        <TableCell>{item.agentId || "-"}</TableCell>
                        <TableCell>
                          <Badge variant={getStateBadgeVariant(item.state)}>
                            {item.state}
                          </Badge>
                        </TableCell>
                        <TableCell>{item.learningLevel}</TableCell>
                        <TableCell>{item.evidenceCount}</TableCell>
                        <TableCell>
                          {item.lastTransitionedAt
                            ? new Date(
                                item.lastTransitionedAt,
                              ).toLocaleDateString()
                            : "-"}
                        </TableCell>
                        <TableCell>
                          <Sheet>
                            <SheetTrigger asChild>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => setSelectedItem(item)}
                              >
                                <Info className="h-4 w-4 mr-1" />
                                Details
                              </Button>
                            </SheetTrigger>
                            <SheetContent>
                              <SheetHeader>
                                <SheetTitle>Mastery Item Details</SheetTitle>
                                <SheetDescription>
                                  Detailed information about this mastery item
                                </SheetDescription>
                              </SheetHeader>
                              {selectedItem && (
                                <div className="space-y-4 mt-4">
                                  <div>
                                    <h3 className="font-semibold mb-2">
                                      Basic Information
                                    </h3>
                                    <div className="grid grid-cols-2 gap-2 text-sm">
                                      <div>
                                        <span className="text-muted-foreground">
                                          Mastery ID:
                                        </span>{" "}
                                        {selectedItem.masteryId}
                                      </div>
                                      <div>
                                        <span className="text-muted-foreground">
                                          Skill ID:
                                        </span>{" "}
                                        {selectedItem.skillId}
                                      </div>
                                      <div>
                                        <span className="text-muted-foreground">
                                          Agent ID:
                                        </span>{" "}
                                        {selectedItem.agentId || "-"}
                                      </div>
                                      <div>
                                        <span className="text-muted-foreground">
                                          Tenant ID:
                                        </span>{" "}
                                        {selectedItem.tenantId}
                                      </div>
                                    </div>
                                  </div>
                                  <div>
                                    <h3 className="font-semibold mb-2">
                                      Mastery State
                                    </h3>
                                    <Badge
                                      variant={getStateBadgeVariant(
                                        selectedItem.state,
                                      )}
                                      className="mr-2"
                                    >
                                      {selectedItem.state}
                                    </Badge>
                                    <span className="text-sm text-muted-foreground">
                                      Learning Level:{" "}
                                      {selectedItem.learningLevel}
                                    </span>
                                  </div>
                                  <div>
                                    <h3 className="font-semibold mb-2">
                                      Evidence
                                    </h3>
                                    <div className="text-sm">
                                      <span className="text-muted-foreground">
                                        Evidence Count:
                                      </span>{" "}
                                      {selectedItem.evidenceCount}
                                    </div>
                                  </div>
                                  {selectedItem.versionScope && (
                                    <div>
                                      <h3 className="font-semibold mb-2">
                                        Version Scope
                                      </h3>
                                      <div className="grid grid-cols-2 gap-2 text-sm">
                                        <div>
                                          <span className="text-muted-foreground">
                                            Kind:
                                          </span>{" "}
                                          {selectedItem.versionScope.kind}
                                        </div>
                                        <div>
                                          <span className="text-muted-foreground">
                                            Name:
                                          </span>{" "}
                                          {selectedItem.versionScope.name}
                                        </div>
                                        <div>
                                          <span className="text-muted-foreground">
                                            Range:
                                          </span>{" "}
                                          {selectedItem.versionScope.range}
                                        </div>
                                        <div>
                                          <span className="text-muted-foreground">
                                            Ecosystem:
                                          </span>{" "}
                                          {selectedItem.versionScope.ecosystem}
                                        </div>
                                      </div>
                                    </div>
                                  )}
                                  <div>
                                    <h3 className="font-semibold mb-2">
                                      Timestamps
                                    </h3>
                                    <div className="grid grid-cols-2 gap-2 text-sm">
                                      <div>
                                        <span className="text-muted-foreground">
                                          Created:
                                        </span>{" "}
                                        {new Date(
                                          selectedItem.createdAt,
                                        ).toLocaleString()}
                                      </div>
                                      <div>
                                        <span className="text-muted-foreground">
                                          Last Transitioned:
                                        </span>{" "}
                                        {selectedItem.lastTransitionedAt
                                          ? new Date(
                                              selectedItem.lastTransitionedAt,
                                            ).toLocaleString()
                                          : "-"}
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              )}
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

        <TabsContent value="deltas" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Pending Learning Deltas</CardTitle>
              <CardDescription>
                Review and approve or reject learning deltas
              </CardDescription>
            </CardHeader>
            <CardContent>
              {deltasLoading ? (
                <div>Loading...</div>
              ) : pendingDeltas && pendingDeltas.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Delta ID</TableHead>
                      <TableHead>Skill ID</TableHead>
                      <TableHead>Target Kind</TableHead>
                      <TableHead>Target ID</TableHead>
                      <TableHead>Learning Level</TableHead>
                      <TableHead>Created At</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {pendingDeltas.map((delta) => (
                      <TableRow key={delta.deltaId}>
                        <TableCell className="font-medium">
                          {delta.deltaId}
                        </TableCell>
                        <TableCell>{delta.skillId}</TableCell>
                        <TableCell>{delta.targetKind}</TableCell>
                        <TableCell>{delta.targetId}</TableCell>
                        <TableCell>{delta.learningLevel}</TableCell>
                        <TableCell>
                          {new Date(delta.createdAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell>
                          <div className="flex gap-2">
                            <Button
                              size="sm"
                              variant="default"
                              onClick={() => handleApprove(delta.deltaId)}
                            >
                              <CheckCircle2 className="h-4 w-4 mr-1" />
                              Approve
                            </Button>
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => handleReject(delta.deltaId)}
                            >
                              <XCircle className="h-4 w-4 mr-1" />
                              Reject
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  No pending learning deltas
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="obsolete" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Obsolete Items</CardTitle>
              <CardDescription>
                Items marked as obsolete due to version mismatches or stale
                evidence
              </CardDescription>
            </CardHeader>
            <CardContent>
              {obsoleteLoading ? (
                <div>Loading...</div>
              ) : obsoleteItems && obsoleteItems.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Event ID</TableHead>
                      <TableHead>Skill ID</TableHead>
                      <TableHead>Detection Type</TableHead>
                      <TableHead>Evidence Refs</TableHead>
                      <TableHead>Detected At</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {obsoleteItems.map((event) => (
                      <TableRow key={event.eventId}>
                        <TableCell className="font-medium">
                          {event.eventId}
                        </TableCell>
                        <TableCell>{event.skillId}</TableCell>
                        <TableCell>
                          <Badge variant="destructive">
                            {event.detectionType}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {event.evidenceRefs
                            .map((ref) => `${ref.kind}:${ref.ref}`)
                            .join(", ")}
                        </TableCell>
                        <TableCell>
                          {new Date(event.detectedAt).toLocaleDateString()}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  No obsolete items
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="promotion" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Promotion Queue</CardTitle>
              <CardDescription>
                L3+ procedural skills awaiting promotion after evaluation
              </CardDescription>
            </CardHeader>
            <CardContent>
              {promotionLoading ? (
                <div>Loading...</div>
              ) : promotionQueue && promotionQueue.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Delta ID</TableHead>
                      <TableHead>Skill ID</TableHead>
                      <TableHead>Target Kind</TableHead>
                      <TableHead>State</TableHead>
                      <TableHead>Evaluation Score</TableHead>
                      <TableHead>Created At</TableHead>
                      <TableHead>Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {promotionQueue.map((item) => (
                      <TableRow key={item.deltaId}>
                        <TableCell className="font-medium">
                          {item.deltaId}
                        </TableCell>
                        <TableCell>{item.skillId}</TableCell>
                        <TableCell>{item.targetKind}</TableCell>
                        <TableCell>
                          <Badge
                            variant={
                              item.state === "APPROVED"
                                ? "default"
                                : "secondary"
                            }
                          >
                            {item.state}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {item.evaluationResult?.score || "-"}
                        </TableCell>
                        <TableCell>
                          {new Date(item.createdAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell>
                          {item.state === "APPROVED" && (
                            <Button
                              size="sm"
                              variant="default"
                              onClick={() => handlePromote(item.deltaId)}
                            >
                              Promote
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="text-center py-8 text-muted-foreground">
                  No items in promotion queue
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}

export default MasteryPage;

export type ContentVersionChangeKind =
  | "create"
  | "manual_edit"
  | "ai_generate"
  | "review_comment"
  | "publish"
  | "rollback"
  | "historical_preview";

export type ContentVersionReviewStatus =
  | "not_required"
  | "pending"
  | "sme_reviewed"
  | "qa_reviewed"
  | "approved"
  | "rejected";

export interface ContentVersionProvenance {
  actorId: string;
  sourcePromptId?: string;
  sourcePromptVersion?: string;
  generationRequestId?: string;
  modelProvider?: string;
  modelVersion?: string;
  retrievedSourceIds?: string[];
  validationId?: string;
  reviewDecisionId?: string;
  rolledBackFromVersion?: number;
  rolledBackFromRevisionId?: string;
}

export interface ContentVersionRecord {
  versionId: string;
  contentId: string;
  versionNumber: number;
  changeKind: ContentVersionChangeKind;
  changeSummary: string;
  snapshot: Record<string, unknown>;
  provenance: ContentVersionProvenance;
  reviewStatus: ContentVersionReviewStatus;
  publishEligible: boolean;
  createdAt: string;
  createdBy: string;
}

export interface CreateContentVersionInput {
  contentId: string;
  changeKind: Exclude<ContentVersionChangeKind, "historical_preview">;
  changeSummary: string;
  snapshot: Record<string, unknown>;
  provenance: ContentVersionProvenance;
  reviewStatus?: ContentVersionReviewStatus;
  createdAt: string;
}

export function createContentVersionRecord(
  history: readonly ContentVersionRecord[],
  input: CreateContentVersionInput,
): ContentVersionRecord {
  const versionNumber =
    Math.max(0, ...history.map((version) => version.versionNumber)) + 1;
  const reviewStatus = input.reviewStatus ?? defaultReviewStatus(input.changeKind);
  const record: ContentVersionRecord = {
    versionId: `${input.contentId}-v${versionNumber}`,
    contentId: input.contentId,
    versionNumber,
    changeKind: input.changeKind,
    changeSummary: input.changeSummary,
    snapshot: structuredClone(input.snapshot),
    provenance: { ...input.provenance },
    reviewStatus,
    publishEligible: isPublishEligible(input.changeKind, reviewStatus),
    createdAt: input.createdAt,
    createdBy: input.provenance.actorId,
  };

  assertVersionRecord(record);
  return record;
}

export function rollbackToVersion(
  history: readonly ContentVersionRecord[],
  targetVersionId: string,
  performedBy: string,
  createdAt: string,
): ContentVersionRecord {
  const target = history.find((version) => version.versionId === targetVersionId);
  if (!target) {
    throw new Error(`Cannot rollback: target version not found (${targetVersionId})`);
  }

  return createContentVersionRecord(history, {
    contentId: target.contentId,
    changeKind: "rollback",
    changeSummary: `Rolled back to version ${target.versionNumber}`,
    snapshot: target.snapshot,
    provenance: {
      actorId: performedBy,
      rolledBackFromVersion: target.versionNumber,
      rolledBackFromRevisionId: target.versionId,
    },
    reviewStatus: "pending",
    createdAt,
  });
}

export function previewHistoricalVersion(
  history: readonly ContentVersionRecord[],
  targetVersionId: string,
): Record<string, unknown> {
  const target = history.find((version) => version.versionId === targetVersionId);
  if (!target) {
    throw new Error(`Cannot preview: target version not found (${targetVersionId})`);
  }

  return structuredClone(target.snapshot);
}

export function assertVersionRecord(record: ContentVersionRecord): void {
  if (record.changeKind === "ai_generate") {
    const provenance = record.provenance;
    if (
      !provenance.sourcePromptId ||
      !provenance.sourcePromptVersion ||
      !provenance.generationRequestId ||
      !provenance.modelProvider ||
      !provenance.modelVersion
    ) {
      throw new Error(
        "AI-generated versions require prompt, generation request, provider, and model provenance",
      );
    }
  }

  if (record.changeKind === "review_comment" && !record.provenance.reviewDecisionId) {
    throw new Error("Review-comment versions require a review decision id");
  }

  if (
    record.changeKind === "rollback" &&
    (!record.provenance.rolledBackFromRevisionId ||
      record.provenance.rolledBackFromVersion === undefined)
  ) {
    throw new Error("Rollback versions require source revision provenance");
  }
}

function defaultReviewStatus(
  changeKind: Exclude<ContentVersionChangeKind, "historical_preview">,
): ContentVersionReviewStatus {
  switch (changeKind) {
    case "create":
    case "manual_edit":
    case "ai_generate":
    case "rollback":
      return "pending";
    case "review_comment":
      return "sme_reviewed";
    case "publish":
      return "approved";
  }
}

function isPublishEligible(
  changeKind: Exclude<ContentVersionChangeKind, "historical_preview">,
  reviewStatus: ContentVersionReviewStatus,
): boolean {
  return changeKind === "publish" && reviewStatus === "approved";
}

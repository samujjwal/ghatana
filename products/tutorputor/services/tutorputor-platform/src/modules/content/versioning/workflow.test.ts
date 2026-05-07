import { describe, expect, it } from "vitest";
import {
  createContentVersionRecord,
  previewHistoricalVersion,
  rollbackToVersion,
  type ContentVersionRecord,
} from "./workflow";

describe("content versioning workflow", () => {
  it("tracks create, manual edit, AI generation, review, publish, rollback, and historical preview", () => {
    const created = createContentVersionRecord([], {
      contentId: "asset-1",
      changeKind: "create",
      changeSummary: "Initial draft",
      snapshot: { title: "Free fall", status: "draft" },
      provenance: { actorId: "author-1" },
      createdAt: "2026-05-06T10:00:00.000Z",
    });
    const edited = createContentVersionRecord([created], {
      contentId: "asset-1",
      changeKind: "manual_edit",
      changeSummary: "Edited misconception language",
      snapshot: { title: "Free fall", status: "draft", misconception: "mass" },
      provenance: { actorId: "author-1" },
      createdAt: "2026-05-06T10:05:00.000Z",
    });
    const generated = createContentVersionRecord([created, edited], {
      contentId: "asset-1",
      changeKind: "ai_generate",
      changeSummary: "Generated simulation and assessment draft",
      snapshot: { title: "Free fall", status: "draft", simulation: "sim-1" },
      provenance: {
        actorId: "author-1",
        sourcePromptId: "prompt-sim",
        sourcePromptVersion: "3",
        generationRequestId: "gen-1",
        modelProvider: "openai",
        modelVersion: "edu-author-1",
        retrievedSourceIds: ["openstax-physics-2.1"],
      },
      createdAt: "2026-05-06T10:10:00.000Z",
    });
    const reviewed = createContentVersionRecord([created, edited, generated], {
      contentId: "asset-1",
      changeKind: "review_comment",
      changeSummary: "SME approved generated simulation after edits",
      snapshot: { title: "Free fall", status: "review", simulation: "sim-1" },
      provenance: {
        actorId: "sme-1",
        reviewDecisionId: "review-1",
      },
      reviewStatus: "approved",
      createdAt: "2026-05-06T10:15:00.000Z",
    });
    const published = createContentVersionRecord(
      [created, edited, generated, reviewed],
      {
        contentId: "asset-1",
        changeKind: "publish",
        changeSummary: "Published SME-approved version",
        snapshot: { title: "Free fall", status: "published", simulation: "sim-1" },
        provenance: { actorId: "publisher-1", validationId: "validation-1" },
        reviewStatus: "approved",
        createdAt: "2026-05-06T10:20:00.000Z",
      },
    );
    const history: ContentVersionRecord[] = [
      created,
      edited,
      generated,
      reviewed,
      published,
    ];
    const rollback = rollbackToVersion(
      history,
      edited.versionId,
      "publisher-1",
      "2026-05-06T10:25:00.000Z",
    );
    const preview = previewHistoricalVersion(history, generated.versionId);

    expect(history.map((version) => version.versionNumber)).toEqual([
      1, 2, 3, 4, 5,
    ]);
    expect(generated.provenance).toMatchObject({
      sourcePromptId: "prompt-sim",
      generationRequestId: "gen-1",
      modelVersion: "edu-author-1",
    });
    expect(published.publishEligible).toBe(true);
    expect(rollback).toMatchObject({
      versionNumber: 6,
      changeKind: "rollback",
      reviewStatus: "pending",
      provenance: {
        rolledBackFromVersion: 2,
        rolledBackFromRevisionId: edited.versionId,
      },
    });
    expect(preview).toEqual({
      title: "Free fall",
      status: "draft",
      simulation: "sim-1",
    });
  });

  it("rejects AI-generated versions without provenance", () => {
    expect(() =>
      createContentVersionRecord([], {
        contentId: "asset-1",
        changeKind: "ai_generate",
        changeSummary: "Generated content",
        snapshot: { title: "Generated" },
        provenance: { actorId: "author-1" },
        createdAt: "2026-05-06T10:00:00.000Z",
      }),
    ).toThrow(/AI-generated versions require/);
  });

  it("rejects review and rollback versions without audit provenance", () => {
    expect(() =>
      createContentVersionRecord([], {
        contentId: "asset-1",
        changeKind: "review_comment",
        changeSummary: "Reviewed",
        snapshot: { title: "Reviewed" },
        provenance: { actorId: "sme-1" },
        createdAt: "2026-05-06T10:00:00.000Z",
      }),
    ).toThrow(/review decision id/);

    expect(() =>
      createContentVersionRecord([], {
        contentId: "asset-1",
        changeKind: "rollback",
        changeSummary: "Rollback",
        snapshot: { title: "Old" },
        provenance: { actorId: "publisher-1" },
        createdAt: "2026-05-06T10:00:00.000Z",
      }),
    ).toThrow(/Rollback versions require/);
  });
});

import { describe, expect, it } from "vitest";

import {
  collectionStatusStyles,
  dataQualityStyles,
  getCollectionStatusStyle,
  getDataQualityStyle,
  getWorkflowStatusStyle,
  workflowStatusStyles,
} from "../theme";

describe("theme helpers", () => {
  it("maps data quality buckets to stable styles", () => {
    expect(getDataQualityStyle(95)).toBe(dataQualityStyles.high);
    expect(getDataQualityStyle(75)).toBe(dataQualityStyles.medium);
    expect(getDataQualityStyle(30)).toBe(dataQualityStyles.low);
  });

  it("falls back to draft collection style for unknown statuses", () => {
    expect(getCollectionStatusStyle("active")).toBe(collectionStatusStyles.active);
    expect(getCollectionStatusStyle("unknown-status")).toBe(collectionStatusStyles.draft);
  });

  it("falls back to pending workflow style for unknown statuses", () => {
    expect(getWorkflowStatusStyle("running")).toBe(workflowStatusStyles.running);
    expect(getWorkflowStatusStyle("unknown-status")).toBe(workflowStatusStyles.pending);
  });
});

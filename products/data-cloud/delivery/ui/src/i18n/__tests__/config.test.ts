import { describe, expect, test } from "vitest";

import i18n from "../config";

describe("i18n config", () => {
  test("supports pseudo-locale coverage", () => {
    expect(i18n.options.supportedLngs).toContain("en");
    expect(i18n.options.supportedLngs).toContain("en-XA");
  });

  test("pseudo locale renders visibly transformed strings", async () => {
    await i18n.changeLanguage("en-XA");
    expect(i18n.t("common.loading")).toContain("[!!");
    await i18n.changeLanguage("en");
  });
});

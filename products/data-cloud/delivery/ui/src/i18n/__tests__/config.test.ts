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

  test("contains required route and disabled-surface translation keys", () => {
    const requiredRouteKeys = [
      "home",
      "data",
      "events",
      "connectors",
      "pipelines",
      "trust",
      "operations",
      "memory",
      "entities",
      "context",
      "fabric",
      "agents",
      "plugins",
      "alerts",
      "settings",
    ];

    for (const routeKey of requiredRouteKeys) {
      expect(i18n.t(`routes.${routeKey}.label`, { lng: "en" })).not.toBe(
        `routes.${routeKey}.label`,
      );
      expect(i18n.t(`routes.${routeKey}.description`, { lng: "en" })).not.toBe(
        `routes.${routeKey}.description`,
      );
    }

    const requiredDisabledSurfaceKeys = [
      "disabled",
      "degraded",
      "unavailable",
      "misconfigured",
      "targetOnly",
      "previewNotAllowed",
      "disabledMessage",
      "degradedMessage",
      "unavailableMessage",
      "misconfiguredMessage",
      "targetOnlyMessage",
      "previewNotAllowedMessage",
      "affectedDependencies",
      "nextAction",
    ];

    for (const key of requiredDisabledSurfaceKeys) {
      expect(i18n.t(`disabledSurface.${key}`, { lng: "en" })).not.toBe(
        `disabledSurface.${key}`,
      );
    }
  });
});

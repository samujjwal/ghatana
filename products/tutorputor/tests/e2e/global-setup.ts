import { chromium, type FullConfig } from "@playwright/test";

const learnerUrl = process.env.BASE_URL ?? "http://127.0.0.1:3201";
const adminUrl = process.env.ADMIN_URL ?? "http://127.0.0.1:3202";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";
const platformUrl = process.env.PLATFORM_URL ?? "http://127.0.0.1:7105";

async function globalSetup(_config: FullConfig): Promise<void> {
  console.log("Starting Tutorputor canonical E2E topology checks...");

  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    const appChecks = [
      { label: "learner app", url: `${learnerUrl}/login` },
      { label: "admin app", url: `${adminUrl}/authoring` },
    ] as const;

    for (const appCheck of appChecks) {
      await page.goto(appCheck.url, { waitUntil: "domcontentloaded" });
      await page.waitForLoadState("networkidle");
      console.log(`Verified ${appCheck.label}: ${appCheck.url}`);
    }

    const serviceChecks = [
      { label: "gateway", url: `${gatewayUrl}/health` },
      { label: "platform", url: `${platformUrl}/health` },
    ] as const;

    for (const serviceCheck of serviceChecks) {
      const response = await page.request.get(serviceCheck.url);
      if (!response.ok()) {
        throw new Error(
          `${serviceCheck.label} health check failed with status ${response.status()}`,
        );
      }
      console.log(`Verified ${serviceCheck.label}: ${serviceCheck.url}`);
    }
  } finally {
    await context.close();
    await browser.close();
  }
}

export default globalSetup;

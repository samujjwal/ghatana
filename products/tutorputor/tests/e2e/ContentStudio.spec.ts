import { expect, test } from "@playwright/test";

const adminUrl = process.env.ADMIN_URL ?? "http://127.0.0.1:3202";
const gatewayUrl = process.env.GATEWAY_URL ?? "http://127.0.0.1:3200";

const trustedHeaders = {
  "x-tenant-id": "default",
  "x-user-id": "user-admin-001",
  "x-user-role": "admin",
  "x-trusted-proxy-secret":
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET ??
    "tutorputor-internal-dev-proxy-secret",
};

test.describe("TutorPutor authoring lifecycle", () => {
  test("uses the canonical admin route and exercises create, validate, and publish through real APIs", async ({
    page,
    request,
  }) => {
    await page.goto(`${adminUrl}/authoring`);
    await page.waitForLoadState("domcontentloaded");

    await expect(
      page.getByRole("heading", { name: "What do you want to teach?" }),
    ).toBeVisible();
    await expect(page.getByRole("textbox").first()).toBeVisible();

    const title = `E2E Motion Lesson ${Date.now()}`;
    const createResponse = await request.post(
      `${gatewayUrl}/api/content-studio/experiences`,
      {
        headers: trustedHeaders,
        data: {
          title,
          description: "Decisive authoring flow verification",
          gradeRange: "grade_6_8",
        },
      },
    );
    expect(createResponse.ok()).toBe(true);

    const createPayload = (await createResponse.json()) as {
      data?: {
        id?: string;
        tenantId?: string;
        experience?: { id?: string; tenantId?: string };
      };
    };
    const experienceId =
      createPayload.data?.experience?.id ?? createPayload.data?.id;
    expect(experienceId).toBeTruthy();

    const claimResponse = await request.post(
      `${gatewayUrl}/api/content-studio/experiences/${experienceId}/claims`,
      {
        headers: trustedHeaders,
        data: {
          text: "Balanced forces keep an object moving at constant velocity.",
          bloomLevel: "understand",
        },
      },
    );
    expect(claimResponse.ok()).toBe(true);

    const claimPayload = (await claimResponse.json()) as {
      data?: { id?: string; claimRef?: string };
    };
    const claimRef = claimPayload.data?.claimRef ?? claimPayload.data?.id;
    expect(claimRef).toBeTruthy();

    const taskResponse = await request.post(
      `${gatewayUrl}/api/content-studio/experiences/${experienceId}/claims/${claimRef}/tasks`,
      {
        headers: trustedHeaders,
        data: {
          prompt: "Explain how balanced forces affect motion in your own words.",
          type: "explanation",
          instructions: "Use one real-world example.",
        },
      },
    );
    expect(taskResponse.ok()).toBe(true);

    const manifestResponse = await request.post(
      `${gatewayUrl}/api/sim-author/manifests`,
      {
        headers: trustedHeaders,
        data: {
          title: `${title} Simulation`,
          domain: "PHYSICS",
          version: "1.0.0",
          description: "Minimal publishable simulation manifest",
          scenes: [],
          variables: [],
        },
      },
    );
    expect(manifestResponse.ok()).toBe(true);

    const manifestPayload = (await manifestResponse.json()) as {
      id?: string;
    };
    const manifestId = manifestPayload.id;
    expect(manifestId).toBeTruthy();

    const linkResponse = await request.post(
      `${gatewayUrl}/api/sim-author/manifests/${manifestId}/link-claim`,
      {
        headers: trustedHeaders,
        data: {
          experienceId,
          claimRef,
          interactionType: "parameter_exploration",
          goal: "Observe constant velocity under balanced forces",
        },
      },
    );
    expect(linkResponse.ok()).toBe(true);

    const validationResponse = await request.post(
      `${gatewayUrl}/api/content-studio/experiences/${experienceId}/validate`,
      {
        headers: trustedHeaders,
        data: {},
      },
    );
    expect(validationResponse.ok()).toBe(true);

    const validationPayload = (await validationResponse.json()) as {
      data?: { canPublish?: boolean; status?: string; score?: number };
    };
    expect(validationPayload.data?.canPublish).toBe(true);
    expect(validationPayload.data?.score).toBeGreaterThanOrEqual(60);

    const publishResponse = await request.post(
      `${gatewayUrl}/api/content-studio/experiences/${experienceId}/publish`,
      {
        headers: trustedHeaders,
      },
    );
    expect(publishResponse.ok()).toBe(true);

    const publishPayload = (await publishResponse.json()) as {
      data?: { id?: string; status?: string; title?: string };
    };
    expect(publishPayload.data?.id).toBe(experienceId);
    expect(String(publishPayload.data?.status).toLowerCase()).toBe("published");

    await page.goto(`${adminUrl}/authoring/${experienceId}`);
    await page.waitForLoadState("domcontentloaded");
    await expect(page).toHaveURL(`${adminUrl}/authoring/${experienceId}`);
    await expect(
      page
        .getByRole("navigation", { name: "Breadcrumb" })
        .getByText(title, { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Learning Claims" }),
    ).toBeVisible();
  });
});

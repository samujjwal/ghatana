import { describe, expect, it, vi } from "vitest";
import { AuthorizationError, ValidationError } from "@tutorputor/core";
import { LTIValidator } from "./validation.js";
import type { LtiLaunchService } from "@tutorputor/contracts/v1/services";

function createLaunchService(): LtiLaunchService {
  return {
    createLoginRedirect: vi.fn(),
    validateLaunch: vi.fn(),
  } as unknown as LtiLaunchService;
}

describe("LTIValidator", () => {
  it("rejects invalid launch payloads", () => {
    const validator = new LTIValidator(createLaunchService());

    expect(() => validator.parseLaunchRequest({ state: "" })).toThrow(
      ValidationError,
    );
  });

  it("maps invalid launch verification into an authorization error", async () => {
    const launchService = createLaunchService();
    vi.mocked(launchService.validateLaunch).mockResolvedValue({
      valid: false,
      error: "Replay attack detected",
    });

    const validator = new LTIValidator(launchService);

    await expect(
      validator.validateLaunchRequest(
        { id_token: "header.payload.signature", state: "state-1" },
        "public" as never,
      ),
    ).rejects.toBeInstanceOf(AuthorizationError);
  });

  it("returns normalized launch details for valid requests", async () => {
    const launchService = createLaunchService();
    vi.mocked(launchService.validateLaunch).mockResolvedValue({
      valid: true,
      launchContext: { platformId: "platform-1" } as never,
      userClaims: { sub: "user-1" } as never,
    });

    const validator = new LTIValidator(launchService);
    const result = await validator.validateLaunchRequest(
      { id_token: "header.payload.signature", state: "state-1" },
      "public" as never,
    );

    expect(result).toEqual({
      state: "state-1",
      verified: true,
      launchContext: { platformId: "platform-1" },
      userClaims: { sub: "user-1" },
    });
  });
});

/**
 * @doc.type service
 * @doc.purpose Canonical LTI launch validation wrapper for HTTP entrypoints
 * @doc.layer security
 * @doc.pattern Security Validation
 */

import { z } from "zod";
import { AuthorizationError, ValidationError } from "@tutorputor/core";
import type { TenantId } from "@tutorputor/contracts/v1/types";
import type { LtiLaunchService } from "@tutorputor/contracts/v1/services";

const launchRequestSchema = z.object({
  id_token: z.string().min(1),
  state: z.string().min(1),
});

export type LTILaunchRequest = z.infer<typeof launchRequestSchema>;

export interface LTILaunchValidationResult {
  state: string;
  verified: true;
  launchContext?: Awaited<ReturnType<LtiLaunchService["validateLaunch"]>>["launchContext"];
  userClaims?: Awaited<ReturnType<LtiLaunchService["validateLaunch"]>>["userClaims"];
}

export class LTIValidator {
  constructor(private readonly launchService: LtiLaunchService) {}

  parseLaunchRequest(input: unknown): LTILaunchRequest {
    const parsed = launchRequestSchema.safeParse(input);
    if (!parsed.success) {
      throw new ValidationError("ID token and state are required");
    }

    return parsed.data;
  }

  async validateLaunchRequest(
    request: unknown,
    tenantId: TenantId,
  ): Promise<LTILaunchValidationResult> {
    const parsed = this.parseLaunchRequest(request);
    const result = await this.launchService.validateLaunch({
      tenantId,
      idToken: parsed.id_token,
      state: parsed.state,
    });

    if (!result.valid) {
      throw new AuthorizationError(result.error ?? "LTI validation failed", {
        state: parsed.state,
      });
    }

    return {
      state: parsed.state,
      verified: true,
      launchContext: result.launchContext,
      userClaims: result.userClaims,
    };
  }
}

/**
 * Rate Limit Configuration DTO
 *
 * Data transfer object for rate limit configuration including tier, limits,
 * and user settings.
 *
 * @doc.type class
 * @doc.purpose Rate limit configuration data transfer
 * @doc.layer product
 * @doc.pattern DTO
 */

export interface RateLimitConfigDTO {
  userId: string;
  tier: string;
  requestsPerHour: number;
  requestsPerDay: number;
  remainingHourly: number;
  remainingDaily: number;
  resetTime: Date;
  upgradedAt?: Date;
  nextUpgradeEligible?: Date;
}

export class RateLimitConfig {
  constructor(
    public userId: string,
    public tier: string,
    public upgradedAt?: Date
  ) { }

  toDTO(): RateLimitConfigDTO {
    const tiers: { [key: string]: { requestsPerHour: number; requestsPerDay: number } } = {
      free: { requestsPerHour: 100, requestsPerDay: 500 },
      pro: { requestsPerHour: 10000, requestsPerDay: 100000 },
      enterprise: { requestsPerHour: 999999, requestsPerDay: 999999 },
    };

    const tierConfig = tiers[this.tier] || tiers['free'];

    return {
      userId: this.userId,
      tier: this.tier,
      requestsPerHour: tierConfig.requestsPerHour,
      requestsPerDay: tierConfig.requestsPerDay,
      remainingHourly: tierConfig.requestsPerHour,
      remainingDaily: tierConfig.requestsPerDay,
      resetTime: new Date(Date.now() + 3600000),
      upgradedAt: this.upgradedAt,
    };
  }
}


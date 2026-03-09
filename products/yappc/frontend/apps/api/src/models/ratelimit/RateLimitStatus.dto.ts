/**
 * Rate Limit Status DTO
 *
 * <p><b>Purpose</b><br>
 * Data transfer object representing the current rate limit status including
 * usage metrics, remaining quota, and reset information.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const status = new RateLimitStatus('user-123', 95, 100);
 * const dto = status.toDTO();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Rate limit status data transfer
 * @doc.layer product
 * @doc.pattern DTO
 */

export interface RateLimitStatusDTO {
  identifier: string;
  tier: string;
  used: number;
  limit: number;
  remaining: number;
  percentage: number;
  resetTime: Date;
  isLimited: boolean;
  lastRequestAt?: Date;
}

export class RateLimitStatus {
  constructor(
    public identifier: string,
    public used: number,
    public limit: number,
    public tier: string = 'free',
    public lastRequestAt?: Date
  ) {}

  toDTO(): RateLimitStatusDTO {
    const remaining = Math.max(0, this.limit - this.used);
    const percentage = (this.used / this.limit) * 100;
    const isLimited = this.used >= this.limit;

    return {
      identifier: this.identifier,
      tier: this.tier,
      used: this.used,
      limit: this.limit,
      remaining,
      percentage: Math.round(percentage * 100) / 100,
      resetTime: new Date(Date.now() + 3600000),
      isLimited,
      lastRequestAt: this.lastRequestAt,
    };
  }

  getStatusColor(): string {
    const percentage = (this.used / this.limit) * 100;
    if (percentage >= 90) return 'red';
    if (percentage >= 70) return 'orange';
    if (percentage >= 50) return 'yellow';
    return 'green';
  }

  getStatusLabel(): string {
    const percentage = (this.used / this.limit) * 100;
    if (percentage >= 100) return 'Limited';
    if (percentage >= 90) return 'Critical';
    if (percentage >= 70) return 'High';
    if (percentage >= 50) return 'Medium';
    return 'Low';
  }
}


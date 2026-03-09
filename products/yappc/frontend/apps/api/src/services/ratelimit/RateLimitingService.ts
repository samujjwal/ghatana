export const RATE_LIMIT_TIERS: Record<string, unknown> = {
    free: { name: 'Free', requestsPerHour: 100, requestsPerDay: 1000, burstSize: 10 },
    pro: { name: 'Pro', requestsPerHour: 1000, requestsPerDay: 10000, burstSize: 50 },
    enterprise: { name: 'Enterprise', requestsPerHour: 10000, requestsPerDay: 100000, burstSize: 500 },
};

export class RateLimitingService {
    async checkLimit(userId: string, action: string) { return true; }
    async getUsage(userId: string) { return {}; }
    async upgradeTier(userId: string, tier: string) { return {}; }
    async getRateLimitStatus(identifier: string) {
        return {
            identifier,
            tier: 'free',
            remaining: 100,
            reset: Date.now() + 3600000,
            totalRequests: 1000,
            remainingRequests: 100,
            resetTime: Date.now() + 3600000,
            isLimited: false,
            lastRequestAt: new Date(),
        };
    }
    async getMetrics() { return { totalRequests: 0, throttledRequests: 0 }; }
    async resetLimit(identifier: string) { return true; }
}

import { z } from 'zod';

export enum SubscriptionPlan {
    FREE = 'FREE',
    STARTER = 'STARTER',
    PROFESSIONAL = 'PROFESSIONAL',
    INSTITUTION = 'INSTITUTION',
    ENTERPRISE = 'ENTERPRISE',
}

export enum SubscriptionStatus {
    ACTIVE = 'ACTIVE',
    CANCELED = 'CANCELED',
    INCOMPLETE = 'INCOMPLETE',
    INCOMPLETE_EXPIRED = 'INCOMPLETE_EXPIRED',
    PAST_DUE = 'PAST_DUE',
    TRIALLING = 'TRIALLING',
    UNPAID = 'UNPAID',
}

export enum SubscriptionInterval {
    MONTHLY = 'month',
    YEARLY = 'year',
}

export const CreateSubscriptionSchema = z.object({
    plan: z.nativeEnum(SubscriptionPlan),
    interval: z.nativeEnum(SubscriptionInterval).optional(),
    paymentMethodId: z.string().optional(),
});

export type CreateSubscriptionDto = z.infer<typeof CreateSubscriptionSchema>;

export interface PlanConfig {
    id: SubscriptionPlan;
    name: string;
    priceCents: number;
    stripePriceId?: string;
    features: string[];
}

export interface PlatformSubscription {
    id: string; // Stripe ID
    userId: string;
    plan: SubscriptionPlan;
    status: SubscriptionStatus;
    currentPeriodEnd: Date;
    cancelAtPeriodEnd: boolean;
    stripeCustomerId: string;
}

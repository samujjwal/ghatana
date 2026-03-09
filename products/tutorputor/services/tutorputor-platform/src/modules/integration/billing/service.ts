import type { BillingService } from "@ghatana/tutorputor-contracts/v1/services";
import type {
    CheckoutSession,
    CheckoutSessionId,
    CheckoutStatus,
    MarketplaceListingId,
    ModuleId,
    Purchase,
    PurchaseId,
    TenantId,
    UserId
} from "@ghatana/tutorputor-contracts/v1/types";
import type { TutorPrismaClient } from "@ghatana/tutorputor-db";

export type HealthAwareBillingService = BillingService & {
    checkHealth: () => Promise<boolean>;
};

/**
 * Creates a Billing Service for marketplace payments.
 * Uses mocked PSP integration for development/testing.
 * 
 * @doc.type class
 * @doc.purpose Handle checkout sessions and purchases for marketplace
 * @doc.layer product
 * @doc.pattern Service
 */
export function createBillingService(
    prisma: TutorPrismaClient
): HealthAwareBillingService {
    return {
        async createCheckoutSession({ tenantId, userId, listingId, successUrl, cancelUrl }) {
            // Fetch the listing to get price and module info
            const listing = await prisma.marketplaceListing.findFirst({
                where: { id: listingId, tenantId, status: "ACTIVE" }
            });

            if (!listing) {
                throw new Error(`Listing ${listingId} not found or not active`);
            }

            // Check if user already purchased this module
            const existingPurchase = await prisma.purchase.findFirst({
                where: { tenantId, userId, moduleId: listing.moduleId }
            });

            if (existingPurchase) {
                throw new Error("You have already purchased this module");
            }

            // Create checkout session (mocked - in production this would call Stripe/Kill Bill)
            const session = await prisma.checkoutSession.create({
                data: {
                    tenantId,
                    userId,
                    listingId,
                    amountCents: listing.priceCents,
                    status: "PENDING",
                    paymentUrl: generateMockPaymentUrl(tenantId, listingId),
                    successUrl,
                    cancelUrl
                }
            });

            return mapToCheckoutSession(session);
        },

        async verifyPayment({ tenantId, sessionId }) {
            const session = await prisma.checkoutSession.findFirst({
                where: { id: sessionId, tenantId }
            });

            if (!session) {
                throw new Error(`Checkout session ${sessionId} not found`);
            }

            // For mock implementation: simulate successful payment
            // In production, this would verify with the PSP
            if (session.status === "PENDING") {
                // Get the listing to create purchase record
                const listing = await prisma.marketplaceListing.findFirst({
                    where: { id: session.listingId, tenantId }
                });

                if (!listing) {
                    // Mark session as failed
                    const failed = await prisma.checkoutSession.update({
                        where: { id: sessionId },
                        data: { status: "FAILED" }
                    });
                    return mapToCheckoutSession(failed);
                }

                // Complete the checkout
                const [updated, _purchase] = await prisma.$transaction([
                    prisma.checkoutSession.update({
                        where: { id: sessionId },
                        data: {
                            status: "COMPLETED",
                            completedAt: new Date()
                        }
                    }),
                    prisma.purchase.create({
                        data: {
                            tenantId,
                            userId: session.userId,
                            listingId: session.listingId,
                            moduleId: listing.moduleId,
                            amountCents: session.amountCents
                        }
                    })
                ]);

                return mapToCheckoutSession(updated);
            }

            return mapToCheckoutSession(session);
        },

        async listPurchases({ tenantId, userId, cursor, limit = 20 }) {
            const take = Math.min(limit, 50);

            const purchases = await prisma.purchase.findMany({
                where: { tenantId, userId },
                take: take + 1,
                orderBy: { purchasedAt: "desc" },
                ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {})
            });

            const hasMore = purchases.length > take;
            const trimmed = purchases.slice(0, take);

            return {
                items: trimmed.map(mapToPurchase),
                nextCursor: hasMore ? trimmed[trimmed.length - 1]?.id : null
            };
        },

        async hasPurchased({ tenantId, userId, moduleId }) {
            const purchase = await prisma.purchase.findFirst({
                where: { tenantId, userId, moduleId }
            });

            return purchase !== null;
        },

        async checkHealth() {
            await prisma.$queryRaw`SELECT 1`;
            return true;
        }
    };
}

// =============================================================================
// Helper Functions
// =============================================================================

function generateMockPaymentUrl(tenantId: string, listingId: string): string {
    // In production, this would be a real payment gateway URL
    return `https://pay.mock.tutorputor.com/checkout?tenant=${tenantId}&listing=${listingId}`;
}

function mapToCheckoutSession(session: any): CheckoutSession {
    return {
        id: session.id as CheckoutSessionId,
        tenantId: session.tenantId as TenantId,
        listingId: session.listingId as MarketplaceListingId,
        userId: session.userId as UserId,
        amountCents: session.amountCents,
        status: session.status as CheckoutStatus,
        createdAt: session.createdAt.toISOString(),
        completedAt: session.completedAt?.toISOString(),
        paymentUrl: session.paymentUrl ?? undefined
    };
}

function mapToPurchase(purchase: any): Purchase {
    return {
        id: purchase.id as PurchaseId,
        tenantId: purchase.tenantId as TenantId,
        userId: purchase.userId as UserId,
        listingId: purchase.listingId as MarketplaceListingId,
        moduleId: purchase.moduleId as ModuleId,
        amountCents: purchase.amountCents,
        purchasedAt: purchase.purchasedAt.toISOString()
    };
}

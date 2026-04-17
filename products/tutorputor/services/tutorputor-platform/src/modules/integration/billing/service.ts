import type { BillingService } from "@tutorputor/contracts/v1/services";
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
} from "@tutorputor/contracts/v1/types";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import Stripe from "stripe";

export type HealthAwareBillingService = BillingService & {
    checkHealth: () => Promise<boolean>;
};

/**
 * Creates a Billing Service for marketplace payments.
 * Uses Stripe for real payment processing.
 *
 * @doc.type class
 * @doc.purpose Handle checkout sessions and purchases for marketplace
 * @doc.layer product
 * @doc.pattern Service
 */
export function createBillingService(
    prisma: TutorPrismaClient
): HealthAwareBillingService {
    const stripe = new Stripe(process.env.STRIPE_SECRET_KEY || "", {
        apiVersion: "2026-03-25.dahlia",
    });
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

            // Create Stripe checkout session
            const stripeSession = await stripe.checkout.sessions.create({
                payment_method_types: ["card"],
                line_items: [
                    {
                        price_data: {
                            currency: "usd",
                            product_data: {
                                name: "Module Purchase",
                                description: `Purchase module ${listing.moduleId}`,
                            },
                            unit_amount: listing.priceCents,
                        },
                        quantity: 1,
                    },
                ],
                mode: "payment",
                success_url: successUrl || "",
                cancel_url: cancelUrl || "",
                metadata: {
                    tenantId,
                    userId,
                    listingId,
                },
            });

            // Create checkout session in database
            const session = await prisma.checkoutSession.create({
                data: {
                    tenantId,
                    userId,
                    listingId,
                    amountCents: listing.priceCents,
                    status: "PENDING",
                    paymentUrl: stripeSession.url || "",
                    stripeSessionId: stripeSession.id,
                    ...(successUrl ? { successUrl } : {}),
                    ...(cancelUrl ? { cancelUrl } : {})
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

            // Verify with Stripe if we have a session ID
            if (session.stripeSessionId && session.status === "PENDING") {
                try {
                    const stripeSession = await stripe.checkout.sessions.retrieve(
                        session.stripeSessionId as string
                    );

                    if (stripeSession.payment_status === "paid") {
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
                    } else if (stripeSession.payment_status === "unpaid") {
                        // Mark as failed if payment is explicitly unpaid
                        const failed = await prisma.checkoutSession.update({
                            where: { id: sessionId },
                            data: { status: "FAILED" }
                        });
                        return mapToCheckoutSession(failed);
                    }
                } catch (error) {
                    // If Stripe verification fails, keep session as pending
                    console.error("Stripe verification failed:", error);
                }
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
                ...(hasMore ? { nextCursor: trimmed[trimmed.length - 1]?.id ?? null } : {})
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
        },

        async createBillingPortalSession({ tenantId, returnUrl }) {
            // For now, return a placeholder URL
            // In production, this would call Stripe Customer Portal API
            return {
                url: `https://billing.stripe.com/session/${tenantId}?return_url=${encodeURIComponent(returnUrl)}`,
            };
        }
    };
}

// =============================================================================
// Helper Functions
// =============================================================================

function mapToCheckoutSession(session: Record<string, unknown>): CheckoutSession {
    const mapped: CheckoutSession = {
        id: session.id as CheckoutSessionId,
        tenantId: session.tenantId as TenantId,
        listingId: session.listingId as MarketplaceListingId,
        userId: session.userId as UserId,
        amountCents: Number(session.amountCents ?? 0),
        status: session.status as CheckoutStatus,
        createdAt: (session.createdAt as Date).toISOString(),
    };
    if (session.completedAt instanceof Date) {
        mapped.completedAt = session.completedAt.toISOString();
    }
    if (typeof session.paymentUrl === 'string') {
        mapped.paymentUrl = session.paymentUrl;
    }
    return mapped;
}

function mapToPurchase(purchase: Record<string, unknown>): Purchase {
    return {
        id: purchase.id as PurchaseId,
        tenantId: purchase.tenantId as TenantId,
        userId: purchase.userId as UserId,
        listingId: purchase.listingId as MarketplaceListingId,
        moduleId: purchase.moduleId as ModuleId,
        amountCents: Number(purchase.amountCents ?? 0),
        purchasedAt: (purchase.purchasedAt as Date).toISOString()
    };
}

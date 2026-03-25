import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Box, Card, Text, Button, Badge, Spinner } from "@/components/ui";
import { PageHeader } from "../components/PageHeader";
import { apiClient } from "../api/tutorputorClient";

interface MarketplaceListing {
    id: string;
    moduleId: string;
    moduleTitle?: string;
    moduleSlug?: string;
    description?: string;
    priceCents: number;
    visibility: string;
}

interface MarketplaceResponse {
    items: MarketplaceListing[];
}

/**
 * Marketplace page for browsing and purchasing modules.
 * 
 * @doc.type component
 * @doc.purpose Display marketplace listings for module purchase
 * @doc.layer product
 * @doc.pattern Page
 */
export function MarketplacePage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [filter, setFilter] = useState<"all" | "free" | "paid">("all");
    const [feedback, setFeedback] = useState<string | null>(null);

    const { data, isLoading, error } = useQuery<MarketplaceResponse>({
        queryKey: ["marketplace", "listings", filter],
        queryFn: async (): Promise<MarketplaceResponse> => {
            const response = await apiClient.listMarketplaceListings({
                status: "ACTIVE",
                visibility: "PUBLIC",
                limit: 50,
            });
            return { items: response.items };
        }
    });

    const { data: purchases } = useQuery({
        queryKey: ["marketplace", "purchases"],
        queryFn: async () => {
            try {
                return await apiClient.listMarketplacePurchases();
            } catch {
                return { items: [] };
            }
        },
    });

    const ownedModuleIds = useMemo(
        () => new Set((purchases?.items ?? []).map((purchase) => purchase.moduleId)),
        [purchases],
    );

    const checkoutMutation = useMutation({
        mutationFn: async (listing: MarketplaceListing) => {
            const session = await apiClient.createMarketplaceCheckoutSession({
                listingId: listing.id,
                successUrl: window.location.href,
                cancelUrl: window.location.href,
            });

            if (session.paymentUrl?.includes("pay.mock.tutorputor.com")) {
                await apiClient.verifyMarketplaceCheckout(session.id);
                return { mode: "verified" as const, listing };
            }

            if (session.paymentUrl) {
                window.location.assign(session.paymentUrl);
                return { mode: "redirected" as const, listing };
            }

            throw new Error("Checkout session did not include a payment URL");
        },
        onSuccess: async ({ mode, listing }) => {
            if (mode === "verified") {
                setFeedback(`Purchased ${listing.moduleTitle ?? listing.moduleId}.`);
                await queryClient.invalidateQueries({ queryKey: ["marketplace", "purchases"] });
            }
        },
        onError: (mutationError) => {
            setFeedback(
                mutationError instanceof Error
                    ? mutationError.message
                    : "Failed to start checkout.",
            );
        },
    });

    if (isLoading) {
        return (
            <Box className="flex items-center justify-center min-h-[400px]">
                <Spinner size="lg" />
            </Box>
        );
    }

    if (error) {
        return (
            <Box className="p-6">
                <Text className="text-red-600">Failed to load marketplace. Please try again.</Text>
            </Box>
        );
    }

    const listings = (data?.items ?? []).filter((listing: MarketplaceListing) => {
        if (filter === "free") return listing.priceCents === 0;
        if (filter === "paid") return listing.priceCents > 0;
        return true;
    });

    return (
        <Box className="p-6">
            <Box className="max-w-6xl mx-auto">
                <PageHeader
                    title="Module Marketplace"
                    description="Discover and enroll in learning modules"
                    actions={
                        <Box className="flex gap-2">
                            {(["all", "free", "paid"] as const).map((f) => (
                                <Button
                                    key={f}
                                    variant={filter === f ? "solid" : "outline"}
                                    tone="primary"
                                    size="sm"
                                    onClick={() => setFilter(f)}
                                >
                                    {f.charAt(0).toUpperCase() + f.slice(1)}
                                </Button>
                            ))}
                        </Box>
                    }
                />

                {feedback ? (
                    <Card className="mb-4 p-4">
                        <Text className="text-sm text-gray-700 dark:text-gray-200">{feedback}</Text>
                    </Card>
                ) : null}

                {listings.length === 0 ? (
                    <Card className="p-8 text-center">
                        <Text className="text-gray-500 dark:text-gray-300">No listings found.</Text>
                    </Card>
                ) : (
                    <Box className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                        {listings.map((listing: MarketplaceListing) => (
                            <Card
                                key={listing.id}
                                className="overflow-hidden hover:shadow-lg transition-shadow"
                            >
                                {/* Placeholder thumbnail */}
                                <Box className="h-40 bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
                                    <Text className="text-white text-4xl">📚</Text>
                                </Box>

                                <Box className="p-4">
                                    <Box className="flex items-start justify-between mb-2">
                                        <Text className="font-semibold text-gray-900 line-clamp-2">
                                            {listing.moduleTitle ?? `Module ${listing.moduleId}`}
                                        </Text>
                                        <Box className="flex items-center gap-2">
                                            {ownedModuleIds.has(listing.moduleId) ? (
                                                <Badge variant="soft" tone="success">Owned</Badge>
                                            ) : null}
                                            <Badge
                                                variant="soft"
                                                tone={listing.priceCents === 0 ? "success" : "neutral"}
                                            >
                                                {listing.priceCents === 0
                                                    ? "Free"
                                                    : `$${(listing.priceCents / 100).toFixed(2)}`}
                                            </Badge>
                                        </Box>
                                    </Box>

                                    <Text className="text-sm text-gray-500 mb-4 line-clamp-2">
                                        {listing.description ?? "Learn something new with this comprehensive module."}
                                    </Text>

                                    <Box className="flex items-center justify-between">
                                        <Box className="flex items-center gap-2">
                                            <Badge variant="outline" tone="neutral" className="text-xs">
                                                {listing.visibility}
                                            </Badge>
                                        </Box>
                                        {ownedModuleIds.has(listing.moduleId) ? (
                                            <Button
                                                size="sm"
                                                onClick={() => navigate(`/modules/${listing.moduleSlug ?? listing.moduleId}`)}
                                            >
                                                Open Module
                                            </Button>
                                        ) : (
                                            <Button
                                                size="sm"
                                                disabled={checkoutMutation.isPending}
                                                onClick={() => checkoutMutation.mutate(listing)}
                                            >
                                                {checkoutMutation.isPending ? "Starting..." : listing.priceCents === 0 ? "Get Free" : "Buy Now"}
                                            </Button>
                                        )}
                                    </Box>
                                </Box>
                            </Card>
                        ))}
                    </Box>
                )}
            </Box>
        </Box>
    );
}

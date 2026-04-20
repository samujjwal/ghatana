/**
 * Marketplace Screen
 *
 * Browse and purchase learning modules.
 *
 * @doc.type component
 * @doc.purpose Module marketplace
 * @doc.layer product
 * @doc.pattern Screen
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  SafeAreaView,
  Alert,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { ExploreStackParamList } from '../navigation/types';
import { useQuery, useMutation } from '@tanstack/react-query';
import { createSessionHeaders } from '../storage/NativeSessionStorage';

type Props = NativeStackScreenProps<ExploreStackParamList, 'Marketplace'>;

interface MarketplaceListing {
  id: string;
  moduleId: string;
  moduleTitle: string;
  description: string;
  priceCents: number;
  sellerName: string;
  rating: number;
  reviewCount: number;
}

async function fetchMarketplaceListings(): Promise<MarketplaceListing[]> {
  const response = await fetch('/api/v1/integration/marketplace/listings', {
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
  });

  if (!response.ok) {
    throw new Error('Failed to fetch marketplace');
  }

  const data = await response.json();
  return data.items || [];
}

async function createCheckoutSession(listingId: string): Promise<{ paymentUrl: string }> {
  const response = await fetch('/api/v1/integration/billing/checkout', {
    method: 'POST',
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ listingId }),
  });

  if (!response.ok) {
    throw new Error('Failed to create checkout');
  }

  return response.json();
}

export function MarketplaceScreen({ navigation }: Props): React.ReactElement {
  const { data: listings, isLoading } = useQuery({
    queryKey: ['marketplace'],
    queryFn: fetchMarketplaceListings,
  });

  const checkoutMutation = useMutation({
    mutationFn: createCheckoutSession,
    onSuccess: (data) => {
      // In a real app, open the payment URL in a WebView or browser
      Alert.alert(
        'Checkout Ready',
        'Payment URL: ' + data.paymentUrl,
        [{ text: 'OK' }]
      );
    },
  });

  const renderListing = ({ item }: { item: MarketplaceListing }) => (
    <View style={styles.listingCard}>
      <View style={styles.listingHeader}>
        <Text style={styles.listingTitle}>{item.moduleTitle}</Text>
        <Text style={styles.price}>${(item.priceCents / 100).toFixed(2)}</Text>
      </View>
      
      <Text style={styles.description}>{item.description}</Text>
      
      <View style={styles.metaContainer}>
        <Text style={styles.sellerText}>by {item.sellerName}</Text>
        <View style={styles.ratingContainer}>
          <Text style={styles.ratingStar}>⭐</Text>
          <Text style={styles.ratingText}>{item.rating} ({item.reviewCount})</Text>
        </View>
      </View>

      <TouchableOpacity
        style={styles.buyButton}
        onPress={() => checkoutMutation.mutate(item.id)}
        disabled={checkoutMutation.isPending}
      >
        <Text style={styles.buyButtonText}>
          {checkoutMutation.isPending ? 'Processing...' : 'Purchase'}
        </Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <SafeAreaView style={styles.container}>
      <FlatList
        data={listings}
        renderItem={renderListing}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>🏪</Text>
            <Text style={styles.emptyTitle}>Marketplace</Text>
            <Text style={styles.emptyText}>No listings available at the moment.</Text>
          </View>
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  listContent: {
    padding: 16,
  },
  listingCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  listingHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  listingTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    flex: 1,
    marginRight: 8,
  },
  price: {
    fontSize: 20,
    fontWeight: '700',
    color: '#4F46E5',
  },
  description: {
    fontSize: 14,
    color: '#6B7280',
    marginBottom: 12,
    lineHeight: 20,
  },
  metaContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sellerText: {
    fontSize: 13,
    color: '#9CA3AF',
  },
  ratingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  ratingStar: {
    fontSize: 14,
    marginRight: 4,
  },
  ratingText: {
    fontSize: 13,
    color: '#6B7280',
  },
  buyButton: {
    backgroundColor: '#4F46E5',
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  buyButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  emptyContainer: {
    alignItems: 'center',
    padding: 40,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
  },
});

/**
 * Billing Screen for Flashit Mobile
 * Subscription management and usage overview
 *
 * @doc.type screen
 * @doc.purpose Display billing, subscription tier, and usage limits
 * @doc.layer product
 * @doc.pattern MobileScreen
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Linking,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import { mobileAtoms } from '../state/localAtoms';

interface TierInfo {
  name: string;
  icon: keyof typeof Ionicons.glyphMap;
  color: string;
  features: string[];
}

interface UsageInfo {
  label: string;
  used: number;
  limit: number;
  unit: string;
}

const TIERS: Record<string, TierInfo> = {
  FREE: {
    name: 'Free',
    icon: 'leaf-outline',
    color: '#64748b',
    features: ['5 Spheres', '100 moments/month', 'Basic search', 'Text capture'],
  },
  PERSONAL: {
    name: 'Personal',
    icon: 'person-outline',
    color: '#0ea5e9',
    features: ['20 Spheres', '1,000 moments/month', 'AI search', 'All capture modes', 'Language insights'],
  },
  PRO: {
    name: 'Pro',
    icon: 'rocket-outline',
    color: '#6366f1',
    features: ['Unlimited Spheres', 'Unlimited moments', 'Advanced AI', 'All capture modes', 'Reflection & analytics', 'Memory expansion'],
  },
  TEAMS: {
    name: 'Teams',
    icon: 'people-outline',
    color: '#f59e0b',
    features: ['Everything in Pro', 'Collaboration', 'API keys', 'SSO/SAML (Coming Soon)', 'Priority support', 'Admin dashboard'],
  },
};

export default function BillingScreen() {
  const currentUser = useAtomValue(mobileAtoms.currentUserAtom);

  const { data: billingData, isLoading } = useQuery({
    queryKey: ['billing'],
    queryFn: async () => {
      // Derive from user data (the gateway provides tier info on auth/me)
      const tier = (currentUser as any)?.tier || 'FREE';
      return {
        tier,
        periodEnd: (currentUser as any)?.subscriptionPeriodEnd || null,
        usage: computeUsage(tier),
      };
    },
    enabled: !!currentUser,
  });

  const tier = billingData?.tier || 'FREE';
  const tierInfo = TIERS[tier] || TIERS.FREE;

  const handleUpgrade = () => {
    Alert.alert(
      'Upgrade Subscription',
      'You will be redirected to the web to manage your subscription.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Open Web',
          onPress: () => {
            Linking.openURL('https://flashit.app/settings?tab=billing');
          },
        },
      ]
    );
  };

  const handleManage = () => {
    Linking.openURL('https://flashit.app/settings?tab=billing');
  };

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" color="#0ea5e9" />
      </View>
    );
  }

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      {/* Current Plan */}
      <View style={[styles.planCard, { borderColor: tierInfo.color }]}>
        <View style={styles.planHeader}>
          <Ionicons name={tierInfo.icon} size={32} color={tierInfo.color} />
          <View style={styles.planInfo}>
            <Text style={styles.planLabel}>Current Plan</Text>
            <Text style={[styles.planName, { color: tierInfo.color }]}>{tierInfo.name}</Text>
          </View>
        </View>
        {billingData?.periodEnd && (
          <Text style={styles.periodText}>
            Renews {new Date(billingData.periodEnd).toLocaleDateString()}
          </Text>
        )}
        <View style={styles.featuresContainer}>
          {tierInfo.features.map((feature) => (
            <View key={feature} style={styles.featureRow}>
              <Ionicons name="checkmark-circle" size={16} color={tierInfo.color} />
              <Text style={styles.featureText}>{feature}</Text>
            </View>
          ))}
        </View>
      </View>

      {/* Usage */}
      <Text style={styles.sectionTitle}>Usage This Month</Text>
      {(billingData?.usage || []).map((u: UsageInfo) => (
        <View key={u.label} style={styles.usageCard}>
          <View style={styles.usageHeader}>
            <Text style={styles.usageLabel}>{u.label}</Text>
            <Text style={styles.usageValue}>
              {u.used} / {u.limit === -1 ? '∞' : u.limit} {u.unit}
            </Text>
          </View>
          <View style={styles.usageBarBg}>
            <View
              style={[
                styles.usageBarFill,
                {
                  width: u.limit === -1 ? '10%' : `${Math.min((u.used / u.limit) * 100, 100)}%`,
                  backgroundColor: getUsageColor(u.used, u.limit),
                },
              ]}
            />
          </View>
        </View>
      ))}

      {/* Actions */}
      <View style={styles.actions}>
        {tier !== 'TEAMS' && (
          <TouchableOpacity
            style={styles.upgradeButton}
            onPress={handleUpgrade}
            accessibilityRole="button"
            accessibilityLabel="Upgrade subscription"
          >
            <Ionicons name="arrow-up-circle-outline" size={20} color="#ffffff" />
            <Text style={styles.upgradeButtonText}>Upgrade Plan</Text>
          </TouchableOpacity>
        )}
        {tier !== 'FREE' && (
          <TouchableOpacity
            style={styles.manageButton}
            onPress={handleManage}
            accessibilityRole="button"
            accessibilityLabel="Manage subscription"
          >
            <Text style={styles.manageButtonText}>Manage Subscription</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Tier Comparison */}
      <Text style={styles.sectionTitle}>Compare Plans</Text>
      {Object.entries(TIERS)
        .filter(([key]) => key !== tier)
        .map(([key, info]) => (
          <View key={key} style={styles.comparePlanCard}>
            <View style={styles.comparePlanHeader}>
              <Ionicons name={info.icon} size={24} color={info.color} />
              <Text style={[styles.comparePlanName, { color: info.color }]}>{info.name}</Text>
            </View>
            <View style={styles.compareFeatures}>
              {info.features.slice(0, 3).map((f) => (
                <Text key={f} style={styles.compareFeatureText}>• {f}</Text>
              ))}
            </View>
          </View>
        ))}
    </ScrollView>
  );
}

function computeUsage(tier: string): UsageInfo[] {
  // Placeholder usage data — real data comes from gateway /api/billing/usage
  const limits: Record<string, { moments: number; spheres: number; transcription: number }> = {
    FREE: { moments: 100, spheres: 5, transcription: 30 },
    PERSONAL: { moments: 1000, spheres: 20, transcription: 300 },
    PRO: { moments: -1, spheres: -1, transcription: -1 },
    TEAMS: { moments: -1, spheres: -1, transcription: -1 },
  };
  const l = limits[tier] || limits.FREE;
  return [
    { label: 'Moments', used: 0, limit: l.moments, unit: 'moments' },
    { label: 'Spheres', used: 0, limit: l.spheres, unit: 'spheres' },
    { label: 'Transcription', used: 0, limit: l.transcription, unit: 'minutes' },
  ];
}

function getUsageColor(used: number, limit: number): string {
  if (limit === -1) return '#10b981';
  const pct = used / limit;
  if (pct >= 0.9) return '#ef4444';
  if (pct >= 0.7) return '#f59e0b';
  return '#0ea5e9';
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8fafc',
    padding: 16,
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  planCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 20,
    borderWidth: 2,
    marginBottom: 24,
  },
  planHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginBottom: 8,
  },
  planInfo: {
    flex: 1,
  },
  planLabel: {
    fontSize: 12,
    color: '#94a3b8',
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  planName: {
    fontSize: 28,
    fontWeight: '800',
  },
  periodText: {
    fontSize: 13,
    color: '#64748b',
    marginBottom: 12,
  },
  featuresContainer: {
    marginTop: 8,
    gap: 8,
  },
  featureRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  featureText: {
    fontSize: 14,
    color: '#334155',
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#1e293b',
    marginBottom: 12,
  },
  usageCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  usageHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  usageLabel: {
    fontSize: 14,
    color: '#334155',
    fontWeight: '500',
  },
  usageValue: {
    fontSize: 13,
    color: '#64748b',
    fontWeight: '600',
  },
  usageBarBg: {
    height: 6,
    backgroundColor: '#f1f5f9',
    borderRadius: 3,
    overflow: 'hidden',
  },
  usageBarFill: {
    height: '100%',
    borderRadius: 3,
  },
  actions: {
    marginVertical: 20,
    gap: 12,
  },
  upgradeButton: {
    flexDirection: 'row',
    backgroundColor: '#0ea5e9',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  upgradeButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  manageButton: {
    borderRadius: 12,
    padding: 14,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  manageButtonText: {
    color: '#64748b',
    fontSize: 15,
    fontWeight: '600',
  },
  comparePlanCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  comparePlanHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 8,
  },
  comparePlanName: {
    fontSize: 16,
    fontWeight: '700',
  },
  compareFeatures: {
    gap: 4,
  },
  compareFeatureText: {
    fontSize: 13,
    color: '#64748b',
  },
});

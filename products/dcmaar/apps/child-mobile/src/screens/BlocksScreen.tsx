import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useBlockedAttempts } from '@/hooks/useBlockedAttempts';

const BlocksScreen: React.FC = () => {
    const { data: blockAlerts, isLoading } = useBlockedAttempts();

    if (isLoading) {
        return (
            <View style={styles.centered}>
                <Text style={styles.loadingText}>Loading blocks...</Text>
            </View>
        );
    }

    return (
        <ScrollView style={styles.container}>
            <View style={styles.section}>
                <Text style={styles.sectionTitle}>Blocked Activity</Text>
                {blockAlerts.map((alert) => (
                    <View key={alert.id} style={styles.card}>
                        <Text style={styles.alertTitle}>{alert.message}</Text>
                        <Text style={styles.metaText}>{alert.type}</Text>
                    </View>
                ))}
                {blockAlerts.length === 0 && (
                    <Text style={styles.metaText}>No blocks recorded yet.</Text>
                )}
            </View>
        </ScrollView>
    );
};

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#f3f4f6',
    },
    centered: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
    },
    loadingText: {
        fontSize: 18,
        color: '#6b7280',
    },
    section: {
        padding: 16,
    },
    sectionTitle: {
        fontSize: 18,
        fontWeight: '600',
        color: '#1f2937',
        marginBottom: 12,
    },
    card: {
        backgroundColor: '#fff',
        padding: 16,
        borderRadius: 8,
        marginBottom: 12,
    },
    alertTitle: {
        fontSize: 16,
        fontWeight: '600',
        color: '#1f2937',
    },
    metaText: {
        fontSize: 12,
        color: '#6b7280',
        marginTop: 4,
    },
});

export default BlocksScreen;

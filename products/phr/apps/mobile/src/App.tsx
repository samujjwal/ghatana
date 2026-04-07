import React from 'react';
import { ActivityIndicator, Pressable, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import { mobileDashboard } from './data/mockData';
import { DashboardScreen } from './screens/DashboardScreen';
import { ConsentScreen } from './screens/ConsentScreen';
import { EmergencyAccessScreen } from './screens/EmergencyAccessScreen';
import { LoginScreen } from './screens/LoginScreen';
import { NotificationsScreen } from './screens/NotificationsScreen';
import { RecordsScreen } from './screens/RecordsScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { authenticateBiometric } from './services/biometricAuth';
import { fetchMobileDashboard, syncOfflineDashboard } from './services/phrMobileApi';
import { registerForPushNotificationsAsync } from './services/pushNotifications';
import type { MobileDashboard } from './types';

type ScreenKey = 'dashboard' | 'records' | 'consents' | 'notifications' | 'emergency' | 'settings';

const tabs: Array<{ key: ScreenKey; label: string }> = [
  { key: 'dashboard', label: 'Home' },
  { key: 'records', label: 'Records' },
  { key: 'consents', label: 'Consents' },
  { key: 'notifications', label: 'Alerts' },
  { key: 'emergency', label: 'Emergency' },
  { key: 'settings', label: 'Settings' },
];

export default function App(): React.ReactElement {
  const [isAuthenticated, setIsAuthenticated] = React.useState(false);
  const [activeScreen, setActiveScreen] = React.useState<ScreenKey>('dashboard');
  const [dashboard, setDashboard] = React.useState<MobileDashboard | null>(null);
  const [syncMessage, setSyncMessage] = React.useState('Offline cache has not been refreshed in this session.');

  React.useEffect(() => {
    void fetchMobileDashboard().then(setDashboard).catch(() => setDashboard(mobileDashboard));
  }, []);

  const onEnablePush = async (): Promise<void> => {
    const token = await registerForPushNotificationsAsync();
    setSyncMessage(`Push notifications ready: ${token}`);
  };

  const onAuthenticate = async (): Promise<void> => {
    const ok = await authenticateBiometric();
    setSyncMessage(ok ? 'Biometric verification succeeded.' : 'Biometric verification unavailable on this device.');
  };

  const onSyncOffline = async (): Promise<void> => {
    setSyncMessage(await syncOfflineDashboard());
  };

  if (!dashboard) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#123c84" />
      </SafeAreaView>
    );
  }

  if (!isAuthenticated) {
    return (
      <SafeAreaView style={styles.page}>
        <LoginScreen onContinue={() => setIsAuthenticated(true)} />
      </SafeAreaView>
    );
  }

  const renderScreen = (): React.ReactElement => {
    switch (activeScreen) {
      case 'records':
        return <RecordsScreen records={dashboard.records} />;
      case 'consents':
        return <ConsentScreen consents={dashboard.consents} />;
      case 'notifications':
        return <NotificationsScreen notifications={dashboard.notifications} onEnablePush={() => void onEnablePush()} />;
      case 'emergency':
        return <EmergencyAccessScreen onAuthenticate={() => void onAuthenticate()} />;
      case 'settings':
        return <SettingsScreen onSyncOffline={() => void onSyncOffline()} syncMessage={syncMessage} />;
      case 'dashboard':
      default:
        return <DashboardScreen dashboard={dashboard} />;
    }
  };

  return (
    <SafeAreaView style={styles.page}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.header}>PHR Nepal mobile</Text>
        {renderScreen()}
      </ScrollView>
      <View style={styles.tabBar}>
        {tabs.map((tab) => (
          <Pressable key={tab.key} onPress={() => setActiveScreen(tab.key)} style={[styles.tab, activeScreen === tab.key && styles.tabActive]}>
            <Text style={[styles.tabLabel, activeScreen === tab.key && styles.tabLabelActive]}>{tab.label}</Text>
          </Pressable>
        ))}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  page: {
    flex: 1,
    backgroundColor: '#f3f8ff',
    paddingHorizontal: 18,
    paddingTop: 18,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f3f8ff',
  },
  content: {
    gap: 16,
    paddingBottom: 100,
  },
  header: {
    fontSize: 30,
    fontWeight: '700',
    color: '#0b1b35',
  },
  tabBar: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: '#d5dded',
    backgroundColor: '#f3f8ff',
  },
  tab: {
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 999,
    backgroundColor: '#dce8fb',
  },
  tabActive: {
    backgroundColor: '#123c84',
  },
  tabLabel: {
    color: '#123c84',
    fontWeight: '700',
  },
  tabLabelActive: {
    color: '#fff',
  },
});
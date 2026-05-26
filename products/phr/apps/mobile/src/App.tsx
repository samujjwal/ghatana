import React, { type ErrorInfo, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import { DashboardScreen } from './screens/DashboardScreen';
import { ConsentScreen } from './screens/ConsentScreen';
import { EmergencyAccessScreen } from './screens/EmergencyAccessScreen';
import { LoginScreen } from './screens/LoginScreen';
import { NotificationsScreen } from './screens/NotificationsScreen';
import { RecordsScreen } from './screens/RecordsScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { authenticateBiometric } from './services/biometricAuth';
import { fetchMobileDashboard, loginMobile, syncOfflineDashboard } from './services/phrMobileApi';
import { registerForPushNotificationsAsync } from './services/pushNotifications';
import type { MobileDashboard, MobileSession } from './types';

type ScreenKey = 'dashboard' | 'records' | 'consents' | 'notifications' | 'emergency' | 'settings';
const APP_DIAGNOSTIC_EVENT = 'phr-mobile:diagnostic';

const tabs: Array<{ key: ScreenKey; label: string }> = [
  { key: 'dashboard', label: 'Home' },
  { key: 'records', label: 'Records' },
  { key: 'consents', label: 'Consents' },
  { key: 'notifications', label: 'Alerts' },
  { key: 'emergency', label: 'Emergency' },
  { key: 'settings', label: 'Settings' },
];

class AppErrorBoundary extends React.Component<{ children: ReactNode }, { hasError: boolean }> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): { hasError: boolean } {
    return { hasError: true };
  }

  override componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    if (typeof globalThis.dispatchEvent === 'function') {
      globalThis.dispatchEvent(new CustomEvent(APP_DIAGNOSTIC_EVENT, {
        detail: {
          code: 'PHR_MOBILE_BOUNDARY_ERROR',
          message: error.message,
          componentStack: errorInfo.componentStack,
        },
      }));
    }
  }

  override render(): ReactNode {
    if (this.state.hasError) {
      return (
        <SafeAreaView style={styles.loadingContainer}>
          <Text style={styles.header}>PHR Nepal mobile</Text>
          <Text style={styles.errorText}>Something went wrong. Restart the app to continue.</Text>
        </SafeAreaView>
      );
    }

    return this.props.children;
  }
}

export default function App(): React.ReactElement {
  const [session, setSession] = React.useState<MobileSession | null>(null);
  const isAuthenticated = session !== null;
  const [activeScreen, setActiveScreen] = React.useState<ScreenKey>('dashboard');
  const [dashboard, setDashboard] = React.useState<MobileDashboard | null>(null);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [syncMessage, setSyncMessage] = React.useState('Offline cache has not been refreshed in this session.');

  const loadDashboard = React.useCallback(() => {
    setLoadError(null);
    void fetchMobileDashboard()
      .then((nextDashboard) => {
        setDashboard(nextDashboard);
        setLoadError(null);
      })
      .catch((error: unknown) => {
        setDashboard(null);
        setLoadError(error instanceof Error ? error.message : 'Unable to load mobile dashboard.');
      });
  }, []);

  // Dashboard is only fetched AFTER successful authentication.
  React.useEffect(() => {
    if (isAuthenticated) {
      loadDashboard();
    }
  }, [isAuthenticated, loadDashboard]);

  const onEnablePush = async (): Promise<void> => {
    const token = await registerForPushNotificationsAsync();
    setSyncMessage(`Push notifications ready: ${token}`);
  };

  const onAuthenticate = async (): Promise<void> => {
    const ok = await authenticateBiometric();
    setSyncMessage(ok ? 'Biometric verification succeeded.' : 'Biometric verification unavailable on this device.');
  };

  const onSyncOffline = async (): Promise<void> => {
    try {
      setSyncMessage(await syncOfflineDashboard());
      loadDashboard();
    } catch (error) {
      setSyncMessage(error instanceof Error ? error.message : 'Offline cache refresh failed.');
    }
  };

  if (!isAuthenticated) {
    return (
      <SafeAreaView style={styles.page}>
        <LoginScreen
          onSuccess={setSession}
          onLoginError={() => { /* error is already displayed inside LoginScreen */ }}
          loginFn={loginMobile}
        />
      </SafeAreaView>
    );
  }

  if (!dashboard) {
    if (loadError) {
      return (
        <SafeAreaView style={styles.loadingContainer}>
          <Text style={styles.header}>PHR Nepal mobile</Text>
          <Text style={styles.errorText}>{loadError}</Text>
          <Pressable onPress={loadDashboard} style={styles.retryButton}>
            <Text style={styles.retryButtonText}>Retry</Text>
          </Pressable>
        </SafeAreaView>
      );
    }

    return (
      <SafeAreaView style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#123c84" />
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
    <AppErrorBoundary>
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
    </AppErrorBoundary>
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
  errorText: {
    marginTop: 16,
    fontSize: 16,
    color: '#0b1b35',
    textAlign: 'center',
    paddingHorizontal: 24,
  },
  retryButton: {
    marginTop: 16,
    paddingHorizontal: 18,
    paddingVertical: 10,
    borderRadius: 999,
    backgroundColor: '#123c84',
  },
  retryButtonText: {
    color: '#fff',
    fontWeight: '700',
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

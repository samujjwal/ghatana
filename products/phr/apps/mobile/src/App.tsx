import React, { type ErrorInfo, type ReactNode } from 'react';
import { ActivityIndicator, Pressable, SafeAreaView, ScrollView, StyleSheet, Text, View } from 'react-native';
import NetInfo from '@react-native-community/netinfo';
import { DashboardScreen } from './screens/DashboardScreen';
import { ConsentScreen } from './screens/ConsentScreen';
import { EmergencyAccessScreen } from './screens/EmergencyAccessScreen';
import { LoginScreen } from './screens/LoginScreen';
import { NotificationsScreen } from './screens/NotificationsScreen';
import { RecordsScreen } from './screens/RecordsScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import { authenticateBiometric } from './services/biometricAuth';
import { fetchMobileDashboard, loginMobile, logoutMobile, syncOfflineDashboard } from './services/phrMobileApi';
import { clearMobileSession, saveMobileSession } from './services/mobileSessionStore';
import { registerForPushNotificationsAsync } from './services/pushNotifications';
import { t } from './i18n/phrMobileI18n';
import type { MobileDashboard, MobileSession } from './types';

type ScreenKey = 'dashboard' | 'records' | 'consents' | 'notifications' | 'emergency' | 'settings';
const APP_DIAGNOSTIC_EVENT = 'phr-mobile:diagnostic';

const TAB_KEYS: readonly ScreenKey[] = ['dashboard', 'records', 'consents', 'notifications', 'emergency', 'settings'];

function getTabLabel(key: ScreenKey): string {
  const keyMap: Record<ScreenKey, string> = {
    dashboard: t('tabs.home'),
    records: t('tabs.records'),
    consents: t('tabs.consents'),
    notifications: t('tabs.alerts'),
    emergency: t('tabs.emergency'),
    settings: t('tabs.settings'),
  };
  return keyMap[key];
}

class AppErrorBoundary extends React.Component<{ children: ReactNode }, { hasError: boolean }> {
  constructor(props: { children: ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): { hasError: boolean } {
    return { hasError: true };
  }

  override componentDidCatch(_error: Error, _errorInfo: ErrorInfo): void {
    if (typeof globalThis.dispatchEvent === 'function') {
      globalThis.dispatchEvent(new CustomEvent(APP_DIAGNOSTIC_EVENT, {
        detail: {
          code: 'PHR_MOBILE_BOUNDARY_ERROR',
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
  const [isConnected, setIsConnected] = React.useState<boolean>(true);
  const [consents, setConsents] = React.useState(dashboard?.consents ?? []);

  React.useEffect(() => {
    const unsubscribe = NetInfo.addEventListener((state: { isConnected: boolean | null }) => {
      setIsConnected(state.isConnected ?? true);
    });
    return unsubscribe;
  }, []);

  React.useEffect(() => {
    setConsents(dashboard?.consents ?? []);
  }, [dashboard]);

  const loadDashboard = React.useCallback(() => {
    if (!session) return;
    setLoadError(null);
    void fetchMobileDashboard(session)
      .then((nextDashboard) => {
        setDashboard(nextDashboard);
        setLoadError(null);
      })
      .catch((error: unknown) => {
        setDashboard(null);
        setLoadError(error instanceof Error ? error.message : 'Unable to load mobile dashboard.');
      });
  }, [session]);

  React.useEffect(() => {
    if (isAuthenticated) {
      loadDashboard();
    }
  }, [isAuthenticated, loadDashboard]);

  const onEnablePush = async (): Promise<void> => {
    const token = await registerForPushNotificationsAsync();
    setSyncMessage(`Push notifications ready: ${token}`);
  };

  const onAuthenticate = async (): Promise<boolean> => {
    const ok = await authenticateBiometric();
    setSyncMessage(ok ? 'Biometric verification succeeded.' : 'Biometric verification unavailable on this device.');
    return ok;
  };

  const onSyncOffline = async (): Promise<void> => {
    if (!session) return;
    try {
      setSyncMessage(await syncOfflineDashboard(session));
      loadDashboard();
    } catch (error) {
      setSyncMessage(error instanceof Error ? error.message : 'Offline cache refresh failed.');
    }
  };

  const handleLogout = async (): Promise<void> => {
    if (session) {
      await logoutMobile(session);
    }
    await clearMobileSession();
    setSession(null);
    setDashboard(null);
    setConsents([]);
    setLoadError(null);
  };

  const handleLoginSuccess = async (newSession: MobileSession): Promise<void> => {
    await saveMobileSession(newSession);
    setSession(newSession);
  };

  const handleConsentRevoked = React.useCallback((grantId: string): void => {
    setConsents((prev) => prev.filter((c) => c.id !== grantId));
  }, []);

  if (!isAuthenticated) {
    return (
      <SafeAreaView style={styles.page}>
        <LoginScreen
          onSuccess={(newSession) => { void handleLoginSuccess(newSession); }}
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
            <Text style={styles.retryButtonText}>{t('common.retry')}</Text>
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
        return (
          <ConsentScreen
            consents={consents}
            session={session}
            onConsentRevoked={handleConsentRevoked}
          />
        );
      case 'notifications':
        return <NotificationsScreen notifications={dashboard.notifications} onEnablePush={() => void onEnablePush()} />;
      case 'emergency':
        return <EmergencyAccessScreen onAuthenticate={onAuthenticate} />;
      case 'settings':
        return <SettingsScreen onSyncOffline={() => void onSyncOffline()} onLogout={() => void handleLogout()} syncMessage={syncMessage} />;
      case 'dashboard':
      default:
        return <DashboardScreen dashboard={dashboard} />;
    }
  };

  return (
    <AppErrorBoundary>
      <SafeAreaView style={styles.page}>
        {!isConnected && (
          <View style={styles.offlineBanner} accessibilityLiveRegion="polite" accessibilityRole="alert">
            <Text style={styles.offlineBannerText}>{t('offline.banner')}</Text>
          </View>
        )}
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.header}>PHR Nepal mobile</Text>
          {renderScreen()}
        </ScrollView>
        <View style={styles.tabBar}>
          {TAB_KEYS.map((key) => (
            <Pressable
              key={key}
              onPress={() => setActiveScreen(key)}
              style={[styles.tab, activeScreen === key && styles.tabActive]}
              accessibilityRole="tab"
              accessibilityState={{ selected: activeScreen === key }}
            >
              <Text style={[styles.tabLabel, activeScreen === key && styles.tabLabelActive]}>
                {getTabLabel(key)}
              </Text>
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
  offlineBanner: {
    backgroundColor: '#f59e0b',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 6,
    marginBottom: 8,
  },
  offlineBannerText: {
    color: '#1c1917',
    fontWeight: '700',
    fontSize: 13,
    textAlign: 'center',
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

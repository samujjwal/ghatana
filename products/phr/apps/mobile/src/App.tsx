import React, { type ErrorInfo, type ReactNode } from 'react';
import {
  ActivityIndicator,
  AppState,
  type AppStateStatus,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
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
import { clearDashboardOffline } from './services/offlineStore';
import { clearMobileSession, loadMobileSession, saveMobileSession } from './services/mobileSessionStore';
import { registerForPushNotificationsAsync } from './services/pushNotifications';
import { initializeLocale, t } from './i18n/phrMobileI18n';
import type { MobileDashboard, MobileSession } from './types';

type ScreenKey = 'dashboard' | 'records' | 'consents' | 'notifications' | 'emergency' | 'settings';
type SubscriptionCleanup = (() => void) | { remove: () => void };
const APP_DIAGNOSTIC_EVENT = 'phr-mobile:diagnostic';

const TAB_KEYS: readonly ScreenKey[] = ['dashboard', 'records', 'consents', 'notifications', 'emergency', 'settings'];

interface TabItem {
  key: ScreenKey;
  label: string;
  hint: string;
  icon: string;
}

function getTabItems(): TabItem[] {
  return TAB_KEYS.map((key) => ({
    key,
    label: getTabLabel(key),
    hint: getTabHint(key),
    icon: getTabLetter(key),
  }));
}

function cleanupSubscription(subscription: SubscriptionCleanup): void {
  if (typeof subscription === 'function') {
    subscription();
    return;
  }
  subscription.remove();
}

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

function getTabHint(key: ScreenKey): string {
  const keyMap: Record<ScreenKey, string> = {
    dashboard: t('tabs.homeHint'),
    records: t('tabs.recordsHint'),
    consents: t('tabs.consentsHint'),
    notifications: t('tabs.alertsHint'),
    emergency: t('tabs.emergencyHint'),
    settings: t('tabs.settingsHint'),
  };
  return keyMap[key];
}

function getTabLetter(key: ScreenKey): string {
  const letterMap: Record<ScreenKey, string> = {
    dashboard: 'H',
    records: 'R',
    consents: 'C',
    notifications: 'N',
    emergency: 'E',
    settings: 'S',
  };
  return letterMap[key];
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
          <Text style={styles.header}>{t('app.errorBoundaryTitle')}</Text>
          <Text style={styles.errorText}>{t('app.errorBoundaryMessage')}</Text>
        </SafeAreaView>
      );
    }

    return this.props.children;
  }
}

function OfflineBanner({ isConnected }: { isConnected: boolean }): React.ReactElement | null {
  if (isConnected) {
    return null;
  }

  return (
    <View style={styles.offlineBanner} accessibilityLiveRegion="polite" accessibilityRole="alert">
      <Text style={styles.offlineBannerText}>{t('offline.banner')}</Text>
    </View>
  );
}

export default function App(): React.ReactElement {
  const [session, setSession] = React.useState<MobileSession | null>(null);
  const [isRestoringSession, setIsRestoringSession] = React.useState(true);
  const isAuthenticated = session !== null;
  const [activeScreen, setActiveScreen] = React.useState<ScreenKey>('dashboard');
  const [dashboard, setDashboard] = React.useState<MobileDashboard | null>(null);
  const [loadError, setLoadError] = React.useState<string | null>(null);
  const [syncMessage, setSyncMessage] = React.useState(t('app.initialSyncMessage'));
  const [isConnected, setIsConnected] = React.useState<boolean>(true);
  const [consents, setConsents] = React.useState(dashboard?.consents ?? []);

  // Restore session on app launch
  React.useEffect(() => {
    const restoreSession = async (): Promise<void> => {
      try {
        await initializeLocale();
        
        const restoredSession = await loadMobileSession();
        if (restoredSession) {
          setSession(restoredSession);
        }
      } catch {
        dispatchAppDiagnostic('PHR_MOBILE_SESSION_RESTORE_FAILED');
        // Session restore failed - user will need to log in again
      } finally {
        setIsRestoringSession(false);
      }
    };
    void restoreSession();
  }, []);

  // Validate session expiry on app foreground/resume
  React.useEffect(() => {
    const handleAppStateChange = async (nextAppState: AppStateStatus): Promise<void> => {
      if (nextAppState === 'active' && session) {
        // App came to foreground - validate session is still valid and detect role/persona changes
        const currentSession = await loadMobileSession(session);
        if (!currentSession) {
          // Session was cleared (expired or revoked)
          await handleLogout();
        } else if (currentSession.role !== session.role || currentSession.principalId !== session.principalId) {
          // Role or principal changed - clear offline cache and update session
          await clearDashboardOffline();
          setSession(currentSession);
          setDashboard(null);
          setConsents([]);
        }
      }
    };

    const subscription = AppState.addEventListener('change', handleAppStateChange);
    return () => {
      subscription?.remove();
    };
  }, [session]);

  React.useEffect(() => {
    const subscription: SubscriptionCleanup = NetInfo.addEventListener((state: { isConnected: boolean | null }) => {
      setIsConnected(state.isConnected ?? true);
    });
    return () => {
      cleanupSubscription(subscription);
    };
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
        setLoadError(error instanceof Error ? error.message : t('app.dashboardLoadError'));
      });
  }, [session]);

  React.useEffect(() => {
    if (isAuthenticated) {
      loadDashboard();
    }
  }, [isAuthenticated, loadDashboard]);

  const onEnablePush = async (): Promise<void> => {
    const token = await registerForPushNotificationsAsync();
    setSyncMessage(t('app.pushNotificationsReady', { token }));
  };

  const onAuthenticate = async (): Promise<boolean> => {
    const ok = await authenticateBiometric();
    setSyncMessage(ok ? t('app.biometricSuccess') : t('app.biometricUnavailable'));
    return ok;
  };

  const onSyncOffline = async (): Promise<void> => {
    if (!session) return;
    try {
      setSyncMessage(await syncOfflineDashboard(session));
      loadDashboard();
    } catch (error) {
      setSyncMessage(error instanceof Error ? error.message : t('app.offlineCacheRefreshFailed'));
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

  if (isRestoringSession) {
    return (
      <SafeAreaView style={styles.loadingContainer}>
        <OfflineBanner isConnected={isConnected} />
        <ActivityIndicator size="large" color="#123c84" />
        <Text style={styles.loadingText}>{t('app.restoringSession')}</Text>
      </SafeAreaView>
    );
  }

  if (!isAuthenticated) {
    return (
      <SafeAreaView style={styles.page}>
        <OfflineBanner isConnected={isConnected} />
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
          <OfflineBanner isConnected={isConnected} />
          <Text style={styles.header}>{t('app.errorBoundaryTitle')}</Text>
          <Text accessibilityRole="alert" style={styles.errorText}>{loadError}</Text>
          <Pressable
            onPress={loadDashboard}
            style={styles.retryButton}
            accessibilityRole="button"
            accessibilityLabel={t('common.retry')}
          >
            <Text style={styles.retryButtonText}>{t('common.retry')}</Text>
          </Pressable>
        </SafeAreaView>
      );
    }

    return (
      <SafeAreaView style={styles.loadingContainer}>
        <OfflineBanner isConnected={isConnected} />
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
        return <EmergencyAccessScreen onAuthenticate={onAuthenticate} session={session} />;
      case 'settings':
        return <SettingsScreen onSyncOffline={() => void onSyncOffline()} onLogout={() => void handleLogout()} syncMessage={syncMessage} session={session} />;
      case 'dashboard':
      default:
        return <DashboardScreen dashboard={dashboard} />;
    }
  };

  return (
    <AppErrorBoundary>
      <SafeAreaView style={styles.page}>
        <OfflineBanner isConnected={isConnected} />
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.header}>{t('app.title')}</Text>
          {renderScreen()}
        </ScrollView>
        <View style={styles.tabBar} accessibilityRole="tabbar">
          {getTabItems().map((tab) => (
            <Pressable
              key={tab.key}
              onPress={() => setActiveScreen(tab.key)}
              style={[styles.tabItem, activeScreen === tab.key && styles.tabItemActive]}
              accessibilityRole="tab"
              accessibilityState={{ selected: activeScreen === tab.key }}
              accessibilityLabel={tab.label}
              accessibilityHint={tab.hint}
            >
              <Text style={styles.tabIcon}>{tab.icon}</Text>
              <Text style={[styles.tabLabel, activeScreen === tab.key && styles.tabLabelActive]}>
                {tab.label}
              </Text>
            </Pressable>
          ))}
        </View>
      </SafeAreaView>
    </AppErrorBoundary>
  );
}

function dispatchAppDiagnostic(code: string): void {
  if (typeof globalThis.dispatchEvent === 'function') {
    globalThis.dispatchEvent(new CustomEvent(APP_DIAGNOSTIC_EVENT, { detail: { code } }));
  }
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
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#4b5c77',
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
    justifyContent: 'space-around',
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: '#d5dded',
    backgroundColor: '#f3f8ff',
    paddingBottom: 8,
  },
  tabItem: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 4,
    borderRadius: 12,
  },
  tabItemActive: {
    backgroundColor: '#dce8fb',
  },
  tabIcon: {
    fontSize: 20,
    marginBottom: 4,
  },
  tabLabel: {
    color: '#123c84',
    fontWeight: '600',
    fontSize: 12,
  },
  tabLabelActive: {
    color: '#0b1b35',
    fontWeight: '700',
  },
});

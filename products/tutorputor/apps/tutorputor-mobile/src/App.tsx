/**
 * TutorPutor Mobile App
 *
 * Main application component with navigation and providers.
 *
 * @doc.type component
 * @doc.purpose Root mobile app component
 * @doc.layer product
 * @doc.pattern App Root
 */

import React, { useEffect, useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { database } from './storage/SQLiteStorage';
import { syncService } from './services/BackgroundSyncService';
import { useNetworkStatus } from './hooks/useOffline';

import { HomeScreen } from './screens/HomeScreen';
import { ModulesScreen } from './screens/ModulesScreen';
import { ModuleDetailScreen } from './screens/ModuleDetailScreen';
import { LessonScreen } from './screens/LessonScreen';
import { QuizScreen } from './screens/QuizScreen';
import { ProfileScreen } from './screens/ProfileScreen';
import { DownloadsScreen } from './screens/DownloadsScreen';
import { OfflineBanner } from './components/OfflineBanner';
import { SyncStatusBar } from './components/SyncStatusBar';

import { RootStackParamList } from './navigation/types';

const Stack = createNativeStackNavigator<RootStackParamList>();

// Create React Query client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      staleTime: 5 * 60 * 1000, // 5 minutes
      gcTime: 10 * 60 * 1000, // 10 minutes
    },
  },
});

/**
 * Main App Component
 */
export function App(): JSX.Element {
  const [isReady, setIsReady] = useState(false);
  const { isConnected } = useNetworkStatus();

  // Initialize app
  useEffect(() => {
    async function initialize() {
      try {
        // Initialize SQLite database
        await database.init();
        
        // Initialize sync service
        const apiBaseUrl = process.env.API_BASE_URL || 'https://api.tutorputor.com';
        await syncService.init({
          apiBaseUrl,
          getAuthToken: async () => {
            // TODO: Get from secure storage
            return null;
          },
          maxConcurrent: 3,
          retryBaseDelayMs: 1000,
          onSyncStart: () => {
            console.log('[Sync] Started');
          },
          onSyncComplete: (result) => {
            console.log('[Sync] Completed:', result);
          },
          onNetworkChange: (connected) => {
            console.log('[Network] Status changed:', connected);
          },
        });
        
        setIsReady(true);
      } catch (error) {
        console.error('[App] Initialization failed:', error);
        // Still set ready to show error screen
        setIsReady(true);
      }
    }

    initialize();

    // Cleanup
    return () => {
      syncService.stop();
      database.close();
    };
  }, []);

  if (!isReady) {
    // Could show splash screen here
    return <></>;
  }

  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <NavigationContainer>
          <OfflineBanner isOffline={!isConnected} />
          <SyncStatusBar />
          
          <Stack.Navigator
            initialRouteName="Home"
            screenOptions={{
              headerStyle: {
                backgroundColor: '#4F46E5',
              },
              headerTintColor: '#fff',
              headerTitleStyle: {
                fontWeight: '600',
              },
            }}
          >
            <Stack.Screen
              name="Home"
              component={HomeScreen}
              options={{ title: 'TutorPutor' }}
            />
            <Stack.Screen
              name="Modules"
              component={ModulesScreen}
              options={{ title: 'Learning Modules' }}
            />
            <Stack.Screen
              name="ModuleDetail"
              component={ModuleDetailScreen}
              options={{ title: 'Module' }}
            />
            <Stack.Screen
              name="Lesson"
              component={LessonScreen}
              options={{ title: 'Lesson' }}
            />
            <Stack.Screen
              name="Quiz"
              component={QuizScreen}
              options={{ title: 'Quiz' }}
            />
            <Stack.Screen
              name="Profile"
              component={ProfileScreen}
              options={{ title: 'My Profile' }}
            />
            <Stack.Screen
              name="Downloads"
              component={DownloadsScreen}
              options={{ title: 'Downloads' }}
            />
          </Stack.Navigator>
        </NavigationContainer>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
}

export default App;

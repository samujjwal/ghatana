/**
 * Guardian Agent App
 */

import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { QueryClient, QueryClientProvider } from 'react-query';
import { Provider as JotaiProvider, useAtomValue } from 'jotai';
import { authAtom } from './stores';
import { useIsAuthenticated, useSelectedDevice } from './hooks/useStores';
import {
  initCommandSync,
  startCommandSync,
  stopCommandSync,
  onSyncSnapshot,
  initCommandExecution,
  executeCommands,
  initTelemetry,
  startTelemetry,
  stopTelemetry,
  sendCommandEvent,
} from './services';
import { DeviceAdminModule } from './native/DeviceAdminModule';

// Screens
import Dashboard from './screens/Dashboard';
import PolicyEditor from './screens/PolicyEditor';
import DeviceSettings from './screens/DeviceSettings';

const Stack = createStackNavigator();
const queryClient = new QueryClient();

function AgentConnectorBootstrap() {
  const auth = useAtomValue(authAtom);
  const isAuthenticated = useIsAuthenticated();
  const selectedDevice = useSelectedDevice();

  useEffect(() => {
    if (!isAuthenticated || !auth.token || !selectedDevice) {
      return;
    }

    const apiBaseUrl = process.env.API_BASE_URL ?? 'http://localhost:3000';

    initTelemetry({
      apiBaseUrl,
      deviceId: selectedDevice.id,
      childId: undefined,
      getAuthToken: () => auth.token,
    });

    startTelemetry();

    initCommandExecution(
      {
        apiBaseUrl,
        deviceId: selectedDevice.id,
        getAuthToken: () => auth.token,
        autoAcknowledge: true,
      },
      {
        onPolicyUpdate: async () => { },
        onImmediateAction: async (command) => {
          if (command.action === 'lock_device') {
            await DeviceAdminModule.applyPolicy('lock_device', {});
          } else if (command.action === 'unlock_device') {
            await DeviceAdminModule.removePolicy('lock_device');
          }
        },
        onSessionRequest: async () => { },
      },
    );

    initCommandSync({
      apiBaseUrl,
      deviceId: selectedDevice.id,
      getAuthToken: () => auth.token,
    });

    startCommandSync();

    const unsubscribe = onSyncSnapshot(async (snapshot) => {
      const commands = snapshot.commands.items;
      if (!commands.length) {
        return;
      }

      const results = await executeCommands(commands);
      for (const result of results) {
        await sendCommandEvent(
          result.command_id,
          result.status === 'processed' ? 'completed' : 'failed',
          result.error_reason,
        );
      }
    });

    return () => {
      unsubscribe();
      stopCommandSync();
      void stopTelemetry();
    };
  }, [auth.token, isAuthenticated, selectedDevice?.id]);

  return null;
}

export default function App() {
  return (
    <JotaiProvider>
      <AgentConnectorBootstrap />
      <QueryClientProvider client={queryClient}>
        <NavigationContainer>
          <Stack.Navigator
            initialRouteName="Dashboard"
            screenOptions={{
              headerStyle: {
                backgroundColor: '#6366f1',
              },
              headerTintColor: '#fff',
              headerTitleStyle: {
                fontWeight: 'bold',
              },
            }}
          >
            <Stack.Screen
              name="Dashboard"
              component={Dashboard}
              options={{ title: 'Guardian Agent' }}
            />
            <Stack.Screen
              name="PolicyEditor"
              component={PolicyEditor}
              options={{ title: 'Policies' }}
            />
            <Stack.Screen
              name="DeviceSettings"
              component={DeviceSettings}
              options={{ title: 'Device Settings' }}
            />
          </Stack.Navigator>
        </NavigationContainer>
      </QueryClientProvider>
    </JotaiProvider>
  );
}

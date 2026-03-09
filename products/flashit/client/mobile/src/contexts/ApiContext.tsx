/**
 * API Context for Flashit Mobile
 * Provides FlashitApiClient instance to all screens
 */

import React, { createContext, useContext, useMemo } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import { FlashitApiClient } from '@ghatana/flashit-shared';
import { useSetAtom } from 'jotai';
import { mobileAtoms } from '../state/localAtoms';
import { useNavigation } from '@react-navigation/native';

interface ApiContextValue {
  apiClient: FlashitApiClient;
}

const ApiContext = createContext<ApiContextValue | null>(null);

export function ApiProvider({ children }: { children: React.ReactNode }) {
  const setToken = useSetAtom(mobileAtoms.authTokenAtom);
  const navigation = useNavigation();

  const apiClient = useMemo(() => {
    const baseURL = Constants.expoConfig?.extra?.apiUrl || 'http://localhost:3002';

    return new FlashitApiClient({
      baseURL,
      getToken: async () => {
        try {
          return await AsyncStorage.getItem('flashit_token');
        } catch (error) {
          console.error('Error getting token:', error);
          return null;
        }
      },
      onTokenChange: async (token) => {
        try {
          if (token) {
            await AsyncStorage.setItem('flashit_token', token);
            setToken(token);
          } else {
            await AsyncStorage.removeItem('flashit_token');
            setToken(null);
          }
        } catch (error) {
          console.error('Error saving token:', error);
        }
      },
      onUnauthorized: () => {
        // Navigate to login screen
        navigation.navigate('Login' as never);
      },
    });
  }, [setToken, navigation]);

  return (
    <ApiContext.Provider value={{ apiClient }}>
      {children}
    </ApiContext.Provider>
  );
}

export function useApi() {
  const context = useContext(ApiContext);
  if (!context) {
    throw new Error('useApi must be used within ApiProvider');
  }
  return context;
}


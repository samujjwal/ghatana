/**
 * Login Screen
 *
 * Authenticates the user with email + password credentials.
 * On success the access and refresh tokens are persisted via the secure
 * NativeSessionStorage, and `onLoginSuccess` is called so the app root
 * can transition to the authenticated navigator.
 *
 * @doc.type component
 * @doc.purpose Email/password login screen
 * @doc.layer product
 * @doc.pattern Screen
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Alert,
} from 'react-native';
import { setSecureToken, setSessionValue } from '../storage/NativeSessionStorage';

interface LoginScreenProps {
  onLoginSuccess: () => void;
}

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tenantId: string;
  user: {
    id: string;
    email: string;
    displayName: string;
    role: string;
  };
}

const API_BASE_URL = 'https://api.tutorputor.com';

async function loginWithCredentials(
  email: string,
  password: string,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as { message?: string };
    throw new Error(body.message ?? `Login failed (HTTP ${response.status})`);
  }

  return response.json() as Promise<LoginResponse>;
}

export function LoginScreen({ onLoginSuccess }: LoginScreenProps): React.ReactElement {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  async function handleLogin(): Promise<void> {
    const trimmedEmail = email.trim();
    if (!trimmedEmail || !password) {
      Alert.alert('Validation', 'Email and password are required.');
      return;
    }

    setIsLoading(true);
    try {
      const result = await loginWithCredentials(trimmedEmail, password);

      // Persist tokens in the encrypted MMKV store
      setSecureToken('access', result.accessToken);
      setSecureToken('refresh', result.refreshToken);
      setSessionValue('tenant_id', result.tenantId);
      setSessionValue('user_id', result.user.id);
      setSessionValue('user_role', result.user.role);

      onLoginSuccess();
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'An unexpected error occurred.';
      Alert.alert('Login Failed', message);
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <View style={styles.card}>
        <Text style={styles.title}>TutorPutor</Text>
        <Text style={styles.subtitle}>Sign in to continue</Text>

        <TextInput
          style={styles.input}
          placeholder="Email"
          placeholderTextColor="#9ca3af"
          keyboardType="email-address"
          autoCapitalize="none"
          autoComplete="email"
          value={email}
          onChangeText={setEmail}
          editable={!isLoading}
          accessibilityLabel="Email address"
        />

        <TextInput
          style={styles.input}
          placeholder="Password"
          placeholderTextColor="#9ca3af"
          secureTextEntry
          autoComplete="current-password"
          value={password}
          onChangeText={setPassword}
          editable={!isLoading}
          onSubmitEditing={() => void handleLogin()}
          returnKeyType="go"
          accessibilityLabel="Password"
        />

        <TouchableOpacity
          style={[styles.button, isLoading && styles.buttonDisabled]}
          onPress={() => void handleLogin()}
          disabled={isLoading}
          accessibilityRole="button"
          accessibilityLabel="Sign in"
        >
          {isLoading ? (
            <ActivityIndicator color="#ffffff" />
          ) : (
            <Text style={styles.buttonText}>Sign In</Text>
          )}
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f3f4f6',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 400,
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 4,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#111827',
    textAlign: 'center',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#6b7280',
    textAlign: 'center',
    marginBottom: 24,
  },
  input: {
    borderWidth: 1,
    borderColor: '#d1d5db',
    borderRadius: 8,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#111827',
    marginBottom: 12,
  },
  button: {
    backgroundColor: '#4f46e5',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
});

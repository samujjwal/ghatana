/**
 * Login Screen for Flashit Mobile
 * User authentication with email and password
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../navigation';
import { useApi } from '../contexts/ApiContext';
import { useSetAtom } from 'jotai';
import { mobileAtoms } from '../state/localAtoms';

type Props = NativeStackScreenProps<RootStackParamList, 'Login'>;

export default function LoginScreen({ navigation }: Props) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const { apiClient } = useApi();
  const setToken = useSetAtom(mobileAtoms.authTokenAtom);
  const setCurrentUser = useSetAtom(mobileAtoms.currentUserAtom);
  const setIsAuthenticated = useSetAtom(mobileAtoms.isAuthenticatedAtom);

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert('Error', 'Please enter email and password');
      return;
    }

    setLoading(true);
    try {
      const response = await apiClient.login({ email, password });
      apiClient.setToken(response.token);
      setToken(response.token);
      setCurrentUser(response.user);
      setIsAuthenticated(true);
      // Navigation handled automatically by auth state change
    } catch (error: any) {
      console.error('Login error:', error);
      Alert.alert(
        'Login Failed',
        error.response?.data?.message || 'Invalid email or password'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      accessible={false}
    >
      <View style={styles.content}>
        <Text 
          style={styles.title}
          accessibilityRole="header"
          accessibilityLabel="Flashit"
        >
          Flashit
        </Text>
        <Text 
          style={styles.subtitle}
          accessibilityLabel="Capture your moments"
        >
          Capture your moments
        </Text>

        <View style={styles.form} accessible={false}>
          <TextInput
            style={styles.input}
            placeholder="Email"
            value={email}
            onChangeText={setEmail}
            autoCapitalize="none"
            keyboardType="email-address"
            editable={!loading}
            accessible={true}
            accessibilityLabel="Email address"
            accessibilityHint="Enter your email address to sign in"
            accessibilityValue={{ text: email || 'empty' }}
          />

          <TextInput
            style={styles.input}
            placeholder="Password"
            value={password}
            onChangeText={setPassword}
            secureTextEntry
            editable={!loading}
            accessible={true}
            accessibilityLabel="Password"
            accessibilityHint="Enter your password to sign in"
            accessibilityValue={{ text: password ? 'entered' : 'empty' }}
          />

          <TouchableOpacity
            style={[styles.button, loading && styles.buttonDisabled]}
            onPress={handleLogin}
            disabled={loading}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel={loading ? 'Signing in' : 'Sign in'}
            accessibilityHint="Double tap to sign in with your credentials"
            accessibilityState={{ disabled: loading, busy: loading }}
          >
            {loading ? (
              <ActivityIndicator 
                color="#fff"
                accessibilityLabel="Loading, please wait"
              />
            ) : (
              <Text style={styles.buttonText}>Sign In</Text>
            )}
          </TouchableOpacity>

          <TouchableOpacity
            onPress={() => navigation.navigate('Register')}
            disabled={loading}
            accessible={true}
            accessibilityRole="link"
            accessibilityLabel="Don't have an account? Sign up"
            accessibilityHint="Double tap to go to registration screen"
            accessibilityState={{ disabled: loading }}
          >
            <Text style={styles.linkText}>
              Don't have an account? <Text style={styles.linkBold}>Sign up</Text>
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f0f9ff',
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    padding: 20,
  },
  title: {
    fontSize: 48,
    fontWeight: 'bold',
    color: '#0ea5e9',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#64748b',
    textAlign: 'center',
    marginBottom: 40,
  },
  form: {
    gap: 16,
  },
  input: {
    backgroundColor: '#fff',
    borderRadius: 8,
    padding: 16,
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  button: {
    backgroundColor: '#0ea5e9',
    borderRadius: 8,
    padding: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  linkText: {
    textAlign: 'center',
    color: '#64748b',
    fontSize: 14,
    marginTop: 8,
  },
  linkBold: {
    color: '#0ea5e9',
    fontWeight: '600',
  },
});


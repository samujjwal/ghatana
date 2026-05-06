/**
 * Register Screen for Flashit Mobile
 * New user registration
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
  ScrollView,
} from 'react-native';
import { NativeStackScreenProps } from '@react-navigation/native-stack';
import { RootStackParamList } from '../navigation';
import { useApi } from '../contexts/ApiContext';
import { useSetAtom } from 'jotai';
import { mobileAtoms } from '../state/localAtoms';
import { flashitMobileTheme } from '../theme/kernelTheme';

type Props = NativeStackScreenProps<RootStackParamList, 'Register'>;

export default function RegisterScreen({ navigation }: Props) {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const { apiClient } = useApi();
  const setToken = useSetAtom(mobileAtoms.authTokenAtom);
  const setCurrentUser = useSetAtom(mobileAtoms.currentUserAtom);

  const handleRegister = async () => {
    if (!email || !password) {
      Alert.alert('Error', 'Please enter email and password');
      return;
    }

    if (password.length < 8) {
      Alert.alert('Error', 'Password must be at least 8 characters');
      return;
    }

    setLoading(true);
    try {
      const response = await apiClient.register({
        email,
        password,
        displayName: displayName || undefined,
      });
      await apiClient.setToken(response.accessToken);
      setToken(response.accessToken);
      setCurrentUser(response.user);
      // Navigation handled automatically by auth state change
    } catch (error: any) {
      console.error('Registration error:', error);
      Alert.alert(
        'Registration Failed',
        error.response?.data?.message || 'An error occurred during registration'
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
      <ScrollView contentContainerStyle={styles.scrollContent}>
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
            accessibilityLabel="Start capturing your moments"
          >
            Start capturing your moments
          </Text>

          <View style={styles.form} accessible={false}>
            <TextInput
              style={styles.input}
              placeholder="Display Name (Optional)"
              placeholderTextColor={flashitMobileTheme.text.secondary}
              value={displayName}
              onChangeText={setDisplayName}
              editable={!loading}
              accessible={true}
              accessibilityLabel="Display name"
              accessibilityHint="Enter your display name (optional)"
              accessibilityValue={{ text: displayName || 'empty' }}
            />

            <TextInput
              style={styles.input}
              placeholder="Email"
              placeholderTextColor={flashitMobileTheme.text.secondary}
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              editable={!loading}
              accessible={true}
              accessibilityLabel="Email address"
              accessibilityHint="Enter your email address"
              accessibilityValue={{ text: email || 'empty' }}
            />

            <TextInput
              style={styles.input}
              placeholder="Password (min. 8 characters)"
              placeholderTextColor={flashitMobileTheme.text.secondary}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              editable={!loading}
              accessible={true}
              accessibilityLabel="Password"
              accessibilityHint="Enter a password with at least 8 characters"
              accessibilityValue={{ text: password ? 'entered' : 'empty' }}
            />

            <TouchableOpacity
              style={[styles.button, loading && styles.buttonDisabled]}
              onPress={handleRegister}
              disabled={loading}
              accessible={true}
              accessibilityRole="button"
              accessibilityLabel={loading ? 'Creating account' : 'Create account'}
              accessibilityHint="Double tap to create your Flashit account"
              accessibilityState={{ disabled: loading, busy: loading }}
            >
              {loading ? (
                <ActivityIndicator
                  color={flashitMobileTheme.text.inverse}
                  accessibilityLabel="Creating account, please wait"
                />
              ) : (
                <Text style={styles.buttonText}>Create Account</Text>
              )}
            </TouchableOpacity>

            <TouchableOpacity
              onPress={() => navigation.navigate('Login')}
              disabled={loading}
              accessible={true}
              accessibilityRole="link"
              accessibilityLabel="Already have an account? Sign in"
              accessibilityHint="Double tap to go to sign in screen"
              accessibilityState={{ disabled: loading }}
            >
              <Text style={styles.linkText}>
                Already have an account? <Text style={styles.linkBold}>Sign in</Text>
              </Text>
            </TouchableOpacity>
          </View>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: flashitMobileTheme.background.canvas,
  },
  scrollContent: {
    flexGrow: 1,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    padding: 20,
  },
  title: {
    fontSize: 48,
    fontWeight: 'bold',
    color: flashitMobileTheme.brand.primary,
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: flashitMobileTheme.text.secondary,
    textAlign: 'center',
    marginBottom: 40,
  },
  form: {
    gap: 16,
  },
  input: {
    backgroundColor: flashitMobileTheme.background.surface,
    borderRadius: 8,
    padding: 16,
    fontSize: 16,
    borderWidth: 1,
    borderColor: flashitMobileTheme.border,
    color: flashitMobileTheme.text.primary,
  },
  button: {
    backgroundColor: flashitMobileTheme.brand.primary,
    borderRadius: 8,
    padding: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: flashitMobileTheme.text.inverse,
    fontSize: 16,
    fontWeight: '600',
  },
  linkText: {
    textAlign: 'center',
    color: flashitMobileTheme.text.secondary,
    fontSize: 14,
    marginTop: 8,
  },
  linkBold: {
    color: flashitMobileTheme.brand.primary,
    fontWeight: '600',
  },
});

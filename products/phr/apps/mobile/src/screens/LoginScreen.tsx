import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import type { MobileSession } from '../types';
import { t } from '../i18n/phrMobileI18n';

interface LoginScreenProps {
  onSuccess: (session: MobileSession) => void;
  onLoginError: (message: string) => void;
  loginFn: (nationalId: string, password: string) => Promise<MobileSession>;
}

export function LoginScreen({ onSuccess, onLoginError, loginFn }: LoginScreenProps): React.ReactElement {
  const [nationalId, setNationalId] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = React.useCallback(async (): Promise<void> => {
    if (!nationalId.trim()) {
      setError(t('login.nationalIdRequired'));
      return;
    }
    if (!password) {
      setError(t('login.passwordRequired'));
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const session = await loginFn(nationalId.trim(), password);
      onSuccess(session);
    } catch (err) {
      const message = err instanceof Error ? err.message : t('login.failed');
      setError(message);
      onLoginError(message);
    } finally {
      setLoading(false);
    }
  }, [nationalId, password, loginFn, onSuccess, onLoginError]);

  return (
    <View style={styles.container}>
      <Text style={styles.eyebrow}>{t('login.title')}</Text>
      <Text style={styles.title}>{t('login.subtitle')}</Text>
      <TextInput
        accessibilityLabel={t('login.nationalIdLabel')}
        placeholder={t('login.nationalIdPlaceholder')}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="default"
        style={styles.input}
        value={nationalId}
        onChangeText={setNationalId}
        editable={!loading}
      />
      <TextInput
        accessibilityLabel={t('login.passwordLabel')}
        placeholder={t('login.passwordPlaceholder')}
        secureTextEntry
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        editable={!loading}
      />
      {error !== null && (
        <Text accessibilityRole="alert" style={styles.errorText}>{error}</Text>
      )}
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={t('login.signIn')}
        onPress={() => void handleSubmit()}
        style={[styles.button, loading && styles.buttonDisabled]}
        disabled={loading}
      >
        {loading
          ? <ActivityIndicator color="#fff" />
          : <Text style={styles.buttonText}>{t('login.signIn')}</Text>
        }
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 14 },
  eyebrow: { textTransform: 'uppercase', color: '#3156a1', fontWeight: '700', letterSpacing: 2 },
  title: { fontSize: 28, fontWeight: '700', color: '#0b1b35' },
  input: { backgroundColor: '#fff', borderRadius: 14, padding: 14, borderWidth: 1, borderColor: '#d5dded' },
  button: { backgroundColor: '#123c84', padding: 16, borderRadius: 16, alignItems: 'center' },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontWeight: '700' },
  errorText: { color: '#c0392b', fontSize: 14 },
});

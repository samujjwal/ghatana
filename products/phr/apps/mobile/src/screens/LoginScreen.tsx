import React from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

interface LoginScreenProps {
  onContinue: () => void;
}

export function LoginScreen({ onContinue }: LoginScreenProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <Text style={styles.eyebrow}>PHR Nepal</Text>
      <Text style={styles.title}>Secure mobile record access</Text>
      <TextInput accessibilityLabel="National ID" placeholder="National ID" style={styles.input} />
      <TextInput accessibilityLabel="Password" placeholder="Password" secureTextEntry style={styles.input} />
      <Pressable onPress={onContinue} style={styles.button}>
        <Text style={styles.buttonText}>Continue with demo account</Text>
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
  buttonText: { color: '#fff', fontWeight: '700' },
});
/**
 * AI Help Button
 *
 * Floating button for omnipresent AI tutor access.
 *
 * @doc.type component
 * @doc.purpose Floating AI tutor access button
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import {
  TouchableOpacity,
  Text,
  StyleSheet,
  View,
} from 'react-native';

interface AIHelpButtonProps {
  onPress: () => void;
  context?: string;
}

export function AIHelpButton({ onPress, context }: AIHelpButtonProps): React.ReactElement {
  return (
    <View style={styles.container}>
      <TouchableOpacity 
        style={styles.button} 
        onPress={onPress}
        activeOpacity={0.8}
      >
        <Text style={styles.icon}>🤖</Text>
        <Text style={styles.label}>Ask AI</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    bottom: 80,
    right: 16,
    zIndex: 100,
  },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#4F46E5',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderRadius: 24,
    shadowColor: '#4F46E5',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
  },
  icon: {
    fontSize: 20,
    marginRight: 8,
  },
  label: {
    color: '#FFFFFF',
    fontWeight: '600',
    fontSize: 14,
  },
});

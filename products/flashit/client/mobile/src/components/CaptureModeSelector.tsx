import React from 'react';
import { View, StyleSheet, TouchableOpacity, Text, Animated } from 'react-native';
import { CaptureMode } from '../screens/UnifiedCaptureScreen';

interface CaptureModeSelectionProps {
  selectedMode: CaptureMode;
  onSelectMode: (mode: CaptureMode) => void;
}

const MODES: { id: CaptureMode; icon: string; label: string }[] = [
  { id: 'text', icon: '📝', label: 'Text' },
  { id: 'voice', icon: '🎙️', label: 'Voice' },
  { id: 'image', icon: '📷', label: 'Photo' },
  { id: 'video', icon: '🎥', label: 'Video' },
];

/**
 * Capture Mode Selector Component
 * 
 * @doc.type component
 * @doc.purpose Tab selector for capture modes
 * @doc.layer product
 * @doc.pattern Component
 */
export const CaptureModeSelector: React.FC<CaptureModeSelectionProps> = ({
  selectedMode,
  onSelectMode,
}) => {
  return (
    <View style={styles.container}>
      {MODES.map((mode) => {
        const isSelected = selectedMode === mode.id;
        return (
          <TouchableOpacity
            key={mode.id}
            style={[styles.modeButton, isSelected && styles.modeButtonSelected]}
            onPress={() => onSelectMode(mode.id)}
            accessible={true}
            accessibilityRole="tab"
            accessibilityLabel={`Switch to ${mode.label} mode`}
            accessibilityState={{ selected: isSelected }}
          >
            <Text style={styles.modeIcon}>{mode.icon}</Text>
            <Text style={[styles.modeLabel, isSelected && styles.modeLabelSelected]}>
              {mode.label}
            </Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    backgroundColor: '#f5f5f5',
    borderRadius: 16,
    padding: 8,
  },
  modeButton: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 16,
    borderRadius: 12,
    backgroundColor: 'transparent',
  },
  modeButtonSelected: {
    backgroundColor: '#fff',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  modeIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  modeLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#666',
  },
  modeLabelSelected: {
    color: '#007aff',
  },
});

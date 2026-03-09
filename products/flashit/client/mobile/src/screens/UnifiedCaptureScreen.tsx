import React, { useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { CaptureModeSelector } from '../components/CaptureModeSelector';
import { QuickCaptureButton } from '../components/QuickCaptureButton';

export type CaptureMode = 'text' | 'voice' | 'image' | 'video';

/**
 * Unified Capture Screen - Single entry point for all capture types
 * 
 * @doc.type screen
 * @doc.purpose Unified interface for text, voice, image, and video capture
 * @doc.layer product
 * @doc.pattern Screen
 */
export const UnifiedCaptureScreen: React.FC = () => {
  const navigation = useNavigation();

  const handleGoBack = () => {
    try {
      if (navigation && typeof navigation.goBack === 'function') {
        navigation.goBack();
      }
    } catch (error) {
      console.error('Navigation error:', error);
    }
  };
  const [selectedMode, setSelectedMode] = useState<CaptureMode>('text');
  const [selectedSphere, setSelectedSphere] = useState<string | null>(null);

  const handleModeSelect = (mode: CaptureMode) => {
    setSelectedMode(mode);
    navigateToCapture(mode);
  };

  const navigateToCapture = (mode: CaptureMode) => {
    if (!navigation) {
      console.error('Navigation not available');
      return;
    }

    switch (mode) {
      case 'voice':
        // @ts-ignore
        navigation.navigate('VoiceRecorder');
        break;
      case 'image':
        // @ts-ignore
        navigation.navigate('ImageCapture');
        break;
      case 'video':
        // @ts-ignore
        navigation.navigate('VideoRecorder');
        break;
      case 'text':
      default:
        // @ts-ignore
        navigation.navigate('TextCapture');
        break;
    }
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity 
          onPress={handleGoBack}
          accessible={true}
          accessibilityRole="button"
          accessibilityLabel="Cancel capture"
        >
          <Text style={styles.headerButton}>Cancel</Text>
        </TouchableOpacity>
        <Text 
          style={styles.headerTitle}
          accessibilityRole="header"
        >
          Capture Moment
        </Text>
        <View style={styles.headerButton} />
      </View>

      <ScrollView style={styles.content} contentContainerStyle={styles.contentContainer}>
        {/* Mode Selector */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Choose Capture Type</Text>
          <CaptureModeSelector
            selectedMode={selectedMode}
            onSelectMode={handleModeSelect}
          />
        </View>

        {/* Quick Capture Hint */}
        <View style={styles.hintContainer}>
          <Text style={styles.hintText}>💡 Tip: Long press the button below for quick voice recording</Text>
        </View>

        {/* Quick Capture Button */}
        <View style={styles.quickCaptureContainer}>
          <QuickCaptureButton
            onTap={() => handleModeSelect(selectedMode)}
            onLongPress={() => handleModeSelect('voice')}
          />
        </View>

        {/* Recent Captures */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Recent Captures</Text>
          <Text style={styles.placeholderText}>Your recent moments will appear here</Text>
        </View>
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: Platform.OS === 'ios' ? 50 : 20,
    paddingHorizontal: 20,
    paddingBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e5e5e5',
  },
  headerButton: {
    width: 60,
    color: '#007aff',
    fontSize: 16,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#000',
  },
  content: {
    flex: 1,
  },
  contentContainer: {
    padding: 20,
  },
  section: {
    marginBottom: 32,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#000',
    marginBottom: 16,
  },
  hintContainer: {
    backgroundColor: '#f0f8ff',
    padding: 16,
    borderRadius: 12,
    marginBottom: 24,
  },
  hintText: {
    fontSize: 14,
    color: '#007aff',
    textAlign: 'center',
  },
  quickCaptureContainer: {
    alignItems: 'center',
    marginBottom: 32,
  },
  placeholderText: {
    fontSize: 14,
    color: '#888',
    textAlign: 'center',
    padding: 32,
  },
});

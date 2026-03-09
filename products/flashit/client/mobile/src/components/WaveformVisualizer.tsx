import React, { useEffect, useRef } from 'react';
import { View, StyleSheet, Animated, Dimensions } from 'react-native';

const { width } = Dimensions.get('window');
const BAR_WIDTH = 4;
const BAR_SPACING = 2;
const MAX_BARS = Math.floor((width - 40) / (BAR_WIDTH + BAR_SPACING));

interface WaveformVisualizerProps {
  audioLevels: number[];
  isRecording: boolean;
}

/**
 * Waveform Visualizer Component
 * 
 * @doc.type component
 * @doc.purpose Real-time audio waveform visualization during recording
 * @doc.layer product
 * @doc.pattern Component
 */
export const WaveformVisualizer: React.FC<WaveformVisualizerProps> = ({
  audioLevels,
  isRecording,
}) => {
  const pulseAnim = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    if (isRecording) {
      // Pulse animation for recording indicator
      Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, {
            toValue: 1.2,
            duration: 500,
            useNativeDriver: true,
          }),
          Animated.timing(pulseAnim, {
            toValue: 1,
            duration: 500,
            useNativeDriver: true,
          }),
        ])
      ).start();
    } else {
      pulseAnim.setValue(1);
    }
  }, [isRecording]);

  const renderBars = () => {
    const displayLevels = audioLevels.slice(-MAX_BARS);
    const bars = [];

    // Fill with empty bars if not enough data
    const emptyBars = MAX_BARS - displayLevels.length;
    for (let i = 0; i < emptyBars; i++) {
      bars.push(
        <View
          key={`empty-${i}`}
          style={[styles.bar, { height: 4, opacity: 0.3 }]}
        />
      );
    }

    // Render actual audio level bars
    displayLevels.forEach((level, index) => {
      // Map level (0-100) to height (4-100)
      const height = Math.max(4, (level / 100) * 100);
      const opacity = isRecording ? 1 : 0.6;

      bars.push(
        <View
          key={`bar-${index}`}
          style={[
            styles.bar,
            {
              height,
              opacity,
              backgroundColor: getBarColor(level),
            },
          ]}
        />
      );
    });

    return bars;
  };

  const getBarColor = (level: number): string => {
    if (level > 80) return '#ff3b30'; // Red for high levels
    if (level > 50) return '#ff9500'; // Orange for medium levels
    return '#34c759'; // Green for low levels
  };

  return (
    <View style={styles.container}>
      {isRecording && (
        <Animated.View
          style={[
            styles.recordingIndicator,
            {
              transform: [{ scale: pulseAnim }],
            },
          ]}
        />
      )}
      <View style={styles.waveformContainer}>{renderBars()}</View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    width: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  recordingIndicator: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#ff3b30',
    marginBottom: 20,
  },
  waveformContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 120,
    gap: BAR_SPACING,
  },
  bar: {
    width: BAR_WIDTH,
    borderRadius: BAR_WIDTH / 2,
    backgroundColor: '#34c759',
  },
});

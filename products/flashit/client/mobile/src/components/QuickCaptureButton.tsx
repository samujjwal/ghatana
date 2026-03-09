import React, { useRef } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  Animated,
  GestureResponderEvent,
} from 'react-native';

interface QuickCaptureButtonProps {
  onTap: () => void;
  onLongPress: () => void;
}

/**
 * Quick Capture Button Component
 * 
 * @doc.type component
 * @doc.purpose Large button with tap and long-press gestures
 * @doc.layer product
 * @doc.pattern Component
 */
export const QuickCaptureButton: React.FC<QuickCaptureButtonProps> = ({
  onTap,
  onLongPress,
}) => {
  const scaleAnim = useRef(new Animated.Value(1)).current;

  const handlePressIn = () => {
    Animated.spring(scaleAnim, {
      toValue: 0.95,
      useNativeDriver: true,
    }).start();
  };

  const handlePressOut = () => {
    Animated.spring(scaleAnim, {
      toValue: 1,
      friction: 3,
      tension: 40,
      useNativeDriver: true,
    }).start();
  };

  return (
    <Animated.View
      style={[
        styles.container,
        {
          transform: [{ scale: scaleAnim }],
        },
      ]}
    >
      <TouchableOpacity
        style={styles.button}
        onPress={onTap}
        onLongPress={onLongPress}
        onPressIn={handlePressIn}
        onPressOut={handlePressOut}
        delayLongPress={500}
        activeOpacity={0.9}
        accessible={true}
        accessibilityRole="button"
        accessibilityLabel="Quick capture"
        accessibilityHint="Tap to capture, long press for voice recording"
      >
        <View style={styles.buttonInner}>
          <View style={styles.iconContainer}>
            <View style={styles.plusVertical} />
            <View style={styles.plusHorizontal} />
          </View>
        </View>
      </TouchableOpacity>
    </Animated.View>
  );
};

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  button: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: '#007aff',
    shadowColor: '#007aff',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  buttonInner: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  iconContainer: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
  },
  plusVertical: {
    position: 'absolute',
    width: 4,
    height: 40,
    backgroundColor: '#fff',
    borderRadius: 2,
  },
  plusHorizontal: {
    position: 'absolute',
    width: 40,
    height: 4,
    backgroundColor: '#fff',
    borderRadius: 2,
  },
});

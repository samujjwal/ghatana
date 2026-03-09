/**
 * Interactive Onboarding Flow
 * 
 * 3-minute guided onboarding for new users with accessibility compliance
 * 
 * @doc.type component
 * @doc.purpose Interactive user onboarding flow
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Dimensions,
  Animated,
  SafeAreaView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { StackNavigationProp } from '@react-navigation/stack';
import { RootStackParamList } from '../navigation';
import { useToastNotifications } from '../components/Toast';
import { useHaptics } from '../services/haptics';

const { width: screenWidth } = Dimensions.get('window');

interface OnboardingStep {
  id: string;
  title: string;
  description: string;
  icon: keyof typeof Ionicons.glyphMap;
  content: React.ReactNode;
  action?: {
    label: string;
    onPress: () => void;
  };
  skipAllowed?: boolean;
}

type OnboardingNavigationProp = StackNavigationProp<RootStackParamList, 'Main'>;

interface OnboardingFlowProps {
  onComplete: () => void;
  onSkip: () => void;
}

/**
 * Main Onboarding Flow Component
 */
export const OnboardingFlow: React.FC<OnboardingFlowProps> = ({
  onComplete,
  onSkip,
}) => {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [progress, setProgress] = useState(0);
  const fadeAnim = new Animated.Value(1);
  const navigation = useNavigation<OnboardingNavigationProp>();
  const { success } = useToastNotifications();
  const { light, medium, success: hapticSuccess } = useHaptics();

  const steps: OnboardingStep[] = [
    {
      id: 'welcome',
      title: 'Welcome to Flashit',
      description: 'Your personal context capture platform',
      icon: 'sparkles',
      content: (
        <View style={styles.welcomeContent}>
          <View style={styles.iconContainer}>
            <Ionicons name="sparkles" size={80} color="#007aff" />
          </View>
          <Text style={styles.welcomeText}>
            Capture your thoughts, moments, and ideas across multiple formats
          </Text>
          <View style={styles.featureList}>
            <View style={styles.featureItem}>
              <Ionicons name="text" size={24} color="#34c759" />
              <Text style={styles.featureText}>Text notes</Text>
            </View>
            <View style={styles.featureItem}>
              <Ionicons name="mic" size={24} color="#ff9500" />
              <Text style={styles.featureText}>Voice recordings</Text>
            </View>
            <View style={styles.featureItem}>
              <Ionicons name="camera" size={24} color="#ff3b30" />
              <Text style={styles.featureText}>Photos & videos</Text>
            </View>
          </View>
        </View>
      ),
      skipAllowed: false,
    },
    {
      id: 'spheres',
      title: 'Organize with Spheres',
      description: 'Contextual categories for your moments',
      icon: 'grid',
      content: (
        <View style={styles.spheresContent}>
          <Text style={styles.stepDescription}>
            Spheres help you organize moments by context
          </Text>
          <View style={styles.spheresGrid}>
            {[
              { name: 'Personal', icon: 'person', color: '#007aff' },
              { name: 'Work', icon: 'briefcase', color: '#34c759' },
              { name: 'Health', icon: 'heart', color: '#ff3b30' },
              { name: 'Learning', icon: 'book', color: '#ff9500' },
              { name: 'Social', icon: 'people', color: '#5856d6' },
              { name: 'Creative', icon: 'color-palette', color: '#ff2d55' },
            ].map((sphere) => (
              <View key={sphere.name} style={styles.sphereCard}>
                <Ionicons 
                  name={sphere.icon} 
                  size={32} 
                  color={sphere.color} 
                />
                <Text style={styles.sphereName}>{sphere.name}</Text>
              </View>
            ))}
          </View>
          <Text style={styles.aiNote}>
            💡 AI automatically suggests the right sphere for each moment
          </Text>
        </View>
      ),
      skipAllowed: true,
    },
    {
      id: 'capture',
      title: 'Capture Your First Moment',
      description: 'Try it now - it only takes a moment',
      icon: 'add-circle',
      content: (
        <View style={styles.captureContent}>
          <Text style={styles.stepDescription}>
            Let's capture your first moment together
          </Text>
          <TouchableOpacity
            style={styles.captureButton}
            onPress={() => {
              medium();
              navigation.navigate('Capture');
            }}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Capture your first moment"
            accessibilityHint="Opens the capture screen to create your first moment"
          >
            <Ionicons name="add" size={32} color="#fff" />
            <Text style={styles.captureButtonText}>Capture Moment</Text>
          </TouchableOpacity>
          <Text style={styles.captureNote}>
            You can capture text, voice, photos, or videos
          </Text>
        </View>
      ),
      skipAllowed: true,
    },
    {
      id: 'ai',
      title: 'AI-Powered Insights',
      description: 'Get intelligent analysis of your moments',
      icon: 'bulb',
      content: (
        <View style={styles.aiContent}>
          <Text style={styles.stepDescription}>
            Our AI helps you understand patterns and gain insights
          </Text>
          <View style={styles.aiFeatures}>
            <View style={styles.aiFeature}>
              <Ionicons name="brain" size={24} color="#007aff" />
              <View style={styles.aiFeatureText}>
                <Text style={styles.aiFeatureTitle}>Smart Classification</Text>
                <Text style={styles.aiFeatureDesc}>Automatically organizes moments</Text>
              </View>
            </View>
            <View style={styles.aiFeature}>
              <Ionicons name="search" size={24} color="#34c759" />
              <View style={styles.aiFeatureText}>
                <Text style={styles.aiFeatureTitle}>Semantic Search</Text>
                <Text style={styles.aiFeatureDesc}>Find moments by meaning</Text>
              </View>
            </View>
            <View style={styles.aiFeature}>
              <Ionicons name="trending-up" size={24} color="#ff9500" />
              <View style={styles.aiFeatureText}>
                <Text style={styles.aiFeatureTitle}>Pattern Recognition</Text>
                <Text style={styles.aiFeatureDesc}>Discover trends in your life</Text>
              </View>
            </View>
          </View>
        </View>
      ),
      skipAllowed: true,
    },
    {
      id: 'ready',
      title: "You're All Set!",
      description: 'Start capturing and organizing your moments',
      icon: 'checkmark-circle',
      content: (
        <View style={styles.readyContent}>
          <View style={styles.successIcon}>
            <Ionicons name="checkmark-circle" size={80} color="#34c759" />
          </View>
          <Text style={styles.readyTitle}>Welcome to Flashit!</Text>
          <Text style={styles.readyDescription}>
            You're ready to start capturing your personal context
          </Text>
          <View style={styles.readyTips}>
            <Text style={styles.readyTipsTitle}>Quick Tips:</Text>
            <Text style={styles.readyTip}>• Use the + button to capture moments</Text>
            <Text style={styles.readyTip}>• AI will help organize everything</Text>
            <Text style={styles.readyTip}>• Search by meaning, not just keywords</Text>
          </View>
        </View>
      ),
      action: {
        label: 'Start Using Flashit',
        onPress: () => {
          hapticSuccess();
          success('Welcome to Flashit! 🎉');
          onComplete();
        },
      },
      skipAllowed: false,
    },
  ];

  useEffect(() => {
    const newProgress = ((currentStepIndex + 1) / steps.length) * 100;
    setProgress(newProgress);
  }, [currentStepIndex, steps.length]);

  const nextStep = () => {
    light();
    if (currentStepIndex < steps.length - 1) {
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 150,
        useNativeDriver: true,
      }).start(() => {
        setCurrentStepIndex(currentStepIndex + 1);
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 150,
          useNativeDriver: true,
        }).start();
      });
    }
  };

  const previousStep = () => {
    light();
    if (currentStepIndex > 0) {
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 150,
        useNativeDriver: true,
      }).start(() => {
        setCurrentStepIndex(currentStepIndex - 1);
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 150,
          useNativeDriver: true,
        }).start();
      });
    }
  };

  const skipOnboarding = () => {
    medium();
    onSkip();
  };

  const currentStep = steps[currentStepIndex];

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <View style={styles.progressContainer}>
          <View style={styles.progressBar}>
            <Animated.View 
              style={[
                styles.progressFill,
                { width: `${progress}%` }
              ]} 
            />
          </View>
          <Text style={styles.progressText}>
            Step {currentStepIndex + 1} of {steps.length}
          </Text>
        </View>
        
        {currentStep.skipAllowed && (
          <TouchableOpacity
            style={styles.skipButton}
            onPress={skipOnboarding}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Skip onboarding"
            accessibilityHint="Skip the onboarding process and go directly to the app"
          >
            <Text style={styles.skipButtonText}>Skip</Text>
          </TouchableOpacity>
        )}
      </View>

      <ScrollView 
        style={styles.content}
        contentContainerStyle={styles.contentContainer}
        showsVerticalScrollIndicator={false}
      >
        <Animated.View style={{ opacity: fadeAnim }}>
          <View style={styles.stepHeader}>
            <View style={styles.stepIcon}>
              <Ionicons 
                name={currentStep.icon} 
                size={48} 
                color="#007aff" 
              />
            </View>
            <Text style={styles.stepTitle}>{currentStep.title}</Text>
            <Text style={styles.stepDescription}>
              {currentStep.description}
            </Text>
          </View>

          {currentStep.content}
        </Animated.View>
      </ScrollView>

      <View style={styles.footer}>
        {currentStepIndex > 0 && (
          <TouchableOpacity
            style={styles.backButton}
            onPress={previousStep}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Previous step"
          >
            <Ionicons name="chevron-back" size={20} color="#007aff" />
            <Text style={styles.backButtonText}>Back</Text>
          </TouchableOpacity>
        )}

        {currentStep.action ? (
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={currentStep.action.onPress}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel={currentStep.action.label}
          >
            <Text style={styles.primaryButtonText}>{currentStep.action.label}</Text>
            <Ionicons name="arrow-forward" size={20} color="#fff" />
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={nextStep}
            accessible={true}
            accessibilityRole="button"
            accessibilityLabel="Next step"
          >
            <Text style={styles.primaryButtonText}>Next</Text>
            <Ionicons name="arrow-forward" size={20} color="#fff" />
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f8f9fa',
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 16,
  },
  progressContainer: {
    marginBottom: 16,
  },
  progressBar: {
    height: 4,
    backgroundColor: '#e5e5e5',
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#007aff',
  },
  progressText: {
    fontSize: 12,
    color: '#8e8e93',
    marginTop: 8,
    textAlign: 'center',
  },
  skipButton: {
    alignSelf: 'flex-end',
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  skipButtonText: {
    fontSize: 16,
    color: '#8e8e93',
    fontWeight: '500',
  },
  content: {
    flex: 1,
  },
  contentContainer: {
    paddingHorizontal: 20,
    paddingVertical: 32,
  },
  stepHeader: {
    alignItems: 'center',
    marginBottom: 32,
  },
  stepIcon: {
    width: 96,
    height: 96,
    borderRadius: 48,
    backgroundColor: '#f0f8ff',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  stepTitle: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1d1d1f',
    textAlign: 'center',
    marginBottom: 8,
  },
  stepDescription: {
    fontSize: 16,
    color: '#8e8e93',
    textAlign: 'center',
    lineHeight: 24,
  },
  welcomeContent: {
    alignItems: 'center',
  },
  iconContainer: {
    marginBottom: 32,
  },
  welcomeText: {
    fontSize: 18,
    color: '#1d1d1f',
    textAlign: 'center',
    lineHeight: 26,
    marginBottom: 32,
  },
  featureList: {
    width: '100%',
    gap: 16,
  },
  featureItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  featureText: {
    fontSize: 16,
    color: '#1d1d1f',
    marginLeft: 12,
    fontWeight: '500',
  },
  spheresContent: {
    alignItems: 'center',
  },
  spheresGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 16,
    marginBottom: 24,
  },
  sphereCard: {
    alignItems: 'center',
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    width: screenWidth * 0.25,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  sphereName: {
    fontSize: 12,
    color: '#1d1d1f',
    marginTop: 8,
    fontWeight: '500',
  },
  aiNote: {
    fontSize: 14,
    color: '#8e8e93',
    textAlign: 'center',
    fontStyle: 'italic',
  },
  captureContent: {
    alignItems: 'center',
  },
  captureButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#007aff',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 28,
    marginVertical: 24,
    shadowColor: '#007aff',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  captureButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '600',
    marginLeft: 8,
  },
  captureNote: {
    fontSize: 14,
    color: '#8e8e93',
    textAlign: 'center',
  },
  aiContent: {
    alignItems: 'center',
  },
  aiFeatures: {
    width: '100%',
    gap: 16,
  },
  aiFeature: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  aiFeatureText: {
    marginLeft: 16,
    flex: 1,
  },
  aiFeatureTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1d1d1f',
    marginBottom: 4,
  },
  aiFeatureDesc: {
    fontSize: 14,
    color: '#8e8e93',
  },
  readyContent: {
    alignItems: 'center',
  },
  successIcon: {
    marginBottom: 24,
  },
  readyTitle: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1d1d1f',
    marginBottom: 16,
  },
  readyDescription: {
    fontSize: 16,
    color: '#8e8e93',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 32,
  },
  readyTips: {
    backgroundColor: '#fff',
    padding: 20,
    borderRadius: 12,
    width: '100%',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  readyTipsTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1d1d1f',
    marginBottom: 12,
  },
  readyTip: {
    fontSize: 14,
    color: '#8e8e93',
    marginBottom: 4,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingBottom: 32,
    paddingTop: 16,
    backgroundColor: '#f8f9fa',
  },
  backButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  backButtonText: {
    fontSize: 16,
    color: '#007aff',
    marginLeft: 4,
    fontWeight: '500',
  },
  primaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#007aff',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    shadowColor: '#007aff',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
    elevation: 4,
  },
  primaryButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
    marginRight: 8,
  },
});

export default OnboardingFlow;

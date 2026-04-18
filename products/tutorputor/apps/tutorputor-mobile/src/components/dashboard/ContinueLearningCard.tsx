/**
 * Continue Learning Card
 *
 * Primary CTA showing the user's current enrollment with progress.
 *
 * @doc.type component
 * @doc.purpose Primary continue learning CTA
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';

interface Enrollment {
  id: string;
  moduleId: string;
  status: string;
  progress: number;
  progressPercent: number;
  lastAccessedAt?: string;
  timeSpentSeconds: number;
  moduleTitle?: string;
  moduleDescription?: string;
}

interface ContinueLearningCardProps {
  enrollment: Enrollment;
  onPress: () => void;
  onSeeAll: () => void;
}

export function ContinueLearningCard({
  enrollment,
  onPress,
  onSeeAll,
}: ContinueLearningCardProps): React.ReactElement {
  const progress = enrollment.progressPercent || enrollment.progress || 0;

  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>Continue Learning</Text>
      
      <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.8}>
        <View style={styles.cardContent}>
          <Text style={styles.moduleTitle} numberOfLines={2}>
            {enrollment.moduleTitle || 'Untitled Module'}
          </Text>
          
          <Text style={styles.moduleDescription} numberOfLines={2}>
            {enrollment.moduleDescription || 'Continue where you left off'}
          </Text>

          <View style={styles.progressContainer}>
            <View style={styles.progressBar}>
              <View style={[styles.progressFill, { width: `${progress}%` }]} />
            </View>
            <Text style={styles.progressText}>{Math.round(progress)}% complete</Text>
          </View>

          <View style={styles.button}>
            <Text style={styles.buttonText}>Resume Learning →</Text>
          </View>
        </View>
      </TouchableOpacity>

      {progress < 100 && (
        <TouchableOpacity onPress={onSeeAll} style={styles.seeAllLink}>
          <Text style={styles.seeAllText}>See all my enrollments</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  card: {
    backgroundColor: '#4F46E5',
    borderRadius: 16,
    shadowColor: '#4F46E5',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 5,
  },
  cardContent: {
    padding: 20,
  },
  moduleTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 8,
  },
  moduleDescription: {
    fontSize: 14,
    color: '#E0E7FF',
    marginBottom: 16,
  },
  progressContainer: {
    marginBottom: 16,
  },
  progressBar: {
    height: 6,
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 3,
    marginBottom: 8,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#FFFFFF',
    borderRadius: 3,
  },
  progressText: {
    fontSize: 12,
    color: '#E0E7FF',
    fontWeight: '500',
  },
  button: {
    backgroundColor: '#FFFFFF',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 8,
    alignSelf: 'flex-start',
  },
  buttonText: {
    color: '#4F46E5',
    fontWeight: '600',
    fontSize: 14,
  },
  seeAllLink: {
    marginTop: 12,
    alignSelf: 'center',
  },
  seeAllText: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '500',
  },
});

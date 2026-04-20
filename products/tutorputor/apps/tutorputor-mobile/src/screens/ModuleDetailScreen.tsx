/**
 * Module Detail Screen
 *
 * Shows detailed information about a learning module.
 *
 * @doc.type component
 * @doc.purpose Module detail view with enrollment option
 * @doc.layer product
 * @doc.pattern Screen
 */

import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  SafeAreaView,
  ActivityIndicator,
} from 'react-native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import type { LearnStackParamList, ExploreStackParamList } from '../navigation/types';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { createSessionHeaders } from '../storage/NativeSessionStorage';

type LearnProps = NativeStackScreenProps<LearnStackParamList, 'ModuleDetail'>;
type ExploreProps = NativeStackScreenProps<ExploreStackParamList, 'ModuleDetail'>;
type Props = LearnProps | ExploreProps;

interface ModuleDetail {
  id: string;
  title: string;
  description: string;
  domain: string;
  difficulty: string;
  estimatedTimeMinutes: number;
  learningObjectives: string[];
  contentBlocks: Array<{
    id: string;
    blockType: string;
    title?: string;
  }>;
  tags: string[];
  isEnrolled: boolean;
  progress?: number;
}

async function fetchModuleDetail(moduleId: string): Promise<ModuleDetail> {
  const response = await fetch(`/api/v1/modules/${moduleId}`, {
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
  });

  if (!response.ok) {
    throw new Error('Failed to fetch module details');
  }

  const data = await response.json();
  return data.module;
}

async function enrollInModule(moduleId: string): Promise<void> {
  const response = await fetch('/api/v1/enrollments', {
    method: 'POST',
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ moduleId }),
  });

  if (!response.ok) {
    throw new Error('Failed to enroll');
  }
}

export function ModuleDetailScreen({ route, navigation }: Props): React.ReactElement {
  const { moduleId } = route.params;
  const queryClient = useQueryClient();

  const { data: module, isLoading, error } = useQuery({
    queryKey: ['module', moduleId],
    queryFn: () => fetchModuleDetail(moduleId),
  });

  const enrollMutation = useMutation({
    mutationFn: () => enrollInModule(moduleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['module', moduleId] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#4F46E5" />
          <Text style={styles.loadingText}>Loading module...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (error || !module) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Failed to load module</Text>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.backText}>Go Back</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const canContinue = module.isEnrolled && module.contentBlocks.length > 0;

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        {/* Header */}
        <View style={styles.header}>
          <View style={[styles.domainBadge, { backgroundColor: getDomainColor(module.domain) }]}>
            <Text style={styles.domainText}>{module.domain}</Text>
          </View>
          <View style={[styles.difficultyBadge, { backgroundColor: getDifficultyColor(module.difficulty) }]}>
            <Text style={styles.difficultyText}>{module.difficulty}</Text>
          </View>
        </View>

        <Text style={styles.title}>{module.title}</Text>
        <Text style={styles.description}>{module.description}</Text>

        {/* Meta Info */}
        <View style={styles.metaContainer}>
          <View style={styles.metaItem}>
            <Text style={styles.metaIcon}>⏱️</Text>
            <Text style={styles.metaText}>{module.estimatedTimeMinutes} minutes</Text>
          </View>
          <View style={styles.metaItem}>
            <Text style={styles.metaIcon}>📚</Text>
            <Text style={styles.metaText}>{module.contentBlocks.length} lessons</Text>
          </View>
        </View>

        {/* Progress (if enrolled) */}
        {module.isEnrolled && module.progress !== undefined && (
          <View style={styles.progressSection}>
            <Text style={styles.sectionTitle}>Your Progress</Text>
            <View style={styles.progressBar}>
              <View style={[styles.progressFill, { width: `${module.progress}%` }]} />
            </View>
            <Text style={styles.progressText}>{Math.round(module.progress)}% complete</Text>
          </View>
        )}

        {/* Learning Objectives */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Learning Objectives</Text>
          {module.learningObjectives.map((objective, index) => (
            <View key={index} style={styles.objectiveItem}>
              <Text style={styles.objectiveBullet}>✓</Text>
              <Text style={styles.objectiveText}>{objective}</Text>
            </View>
          ))}
        </View>

        {/* Content Preview */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Content Preview</Text>
          {module.contentBlocks.slice(0, 3).map((block, index) => (
            <View key={block.id} style={styles.contentItem}>
              <Text style={styles.contentNumber}>{index + 1}</Text>
              <Text style={styles.contentTitle}>{block.title || `${block.blockType} ${index + 1}`}</Text>
            </View>
          ))}
          {module.contentBlocks.length > 3 && (
            <Text style={styles.moreText}>+ {module.contentBlocks.length - 3} more sections</Text>
          )}
        </View>

        {/* Tags */}
        <View style={styles.tagsContainer}>
          {module.tags.map((tag, index) => (
            <View key={index} style={styles.tag}>
              <Text style={styles.tagText}>{tag}</Text>
            </View>
          ))}
        </View>
      </ScrollView>

      {/* Action Button */}
      <View style={styles.footer}>
        {canContinue ? (
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={() => {
              // Navigate to first lesson
              const firstBlock = module.contentBlocks[0];
              if (firstBlock) {
                // Navigate to lesson screen with proper type casting
                (navigation as LearnProps['navigation']).navigate('Lesson', {
                  moduleId: module.id,
                  lessonId: firstBlock.id,
                });
              }
            }}
          >
            <Text style={styles.primaryButtonText}>
              {module.progress && module.progress > 0 ? 'Continue Learning →' : 'Start Learning →'}
            </Text>
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={() => enrollMutation.mutate()}
            disabled={enrollMutation.isPending}
          >
            {enrollMutation.isPending ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.primaryButtonText}>Enroll Now</Text>
            )}
          </TouchableOpacity>
        )}
      </View>
    </SafeAreaView>
  );
}

function getDomainColor(domain: string): string {
  const colors: Record<string, string> = {
    physics: '#EEF2FF',
    chemistry: '#ECFDF5',
    biology: '#FEF3C7',
    mathematics: '#FCE7F3',
    engineering: '#DBEAFE',
  };
  return colors[domain.toLowerCase()] || '#F3F4F6';
}

function getDifficultyColor(difficulty: string): string {
  const colors: Record<string, string> = {
    beginner: '#D1FAE5',
    intermediate: '#FEF3C7',
    advanced: '#FEE2E2',
  };
  return colors[difficulty.toLowerCase()] || '#F3F4F6';
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F9FAFB',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 14,
    color: '#6B7280',
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  errorText: {
    fontSize: 16,
    color: '#DC2626',
    marginBottom: 12,
  },
  backText: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '600',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    padding: 20,
    paddingBottom: 100,
  },
  header: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  domainBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
  },
  domainText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#374151',
    textTransform: 'uppercase',
  },
  difficultyBadge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 4,
  },
  difficultyText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#374151',
    textTransform: 'capitalize',
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 12,
  },
  description: {
    fontSize: 16,
    color: '#6B7280',
    lineHeight: 24,
    marginBottom: 20,
  },
  metaContainer: {
    flexDirection: 'row',
    gap: 24,
    marginBottom: 24,
  },
  metaItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  metaIcon: {
    fontSize: 16,
  },
  metaText: {
    fontSize: 14,
    color: '#4B5563',
  },
  progressSection: {
    backgroundColor: '#FFFFFF',
    padding: 16,
    borderRadius: 12,
    marginBottom: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  progressBar: {
    height: 8,
    backgroundColor: '#E5E7EB',
    borderRadius: 4,
    marginVertical: 8,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#4F46E5',
    borderRadius: 4,
  },
  progressText: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'right',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  objectiveItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  objectiveBullet: {
    color: '#4F46E5',
    fontWeight: '700',
    marginRight: 8,
    fontSize: 16,
  },
  objectiveText: {
    fontSize: 15,
    color: '#4B5563',
    flex: 1,
    lineHeight: 22,
  },
  contentItem: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  contentNumber: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#4F46E5',
    color: '#FFFFFF',
    textAlign: 'center',
    lineHeight: 28,
    fontWeight: '600',
    marginRight: 12,
  },
  contentTitle: {
    fontSize: 15,
    color: '#1F2937',
  },
  moreText: {
    fontSize: 14,
    color: '#6B7280',
    textAlign: 'center',
    marginTop: 8,
  },
  tagsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  tag: {
    backgroundColor: '#F3F4F6',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  tagText: {
    fontSize: 13,
    color: '#4B5563',
  },
  footer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#FFFFFF',
    padding: 20,
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
  },
  primaryButton: {
    backgroundColor: '#4F46E5',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
});

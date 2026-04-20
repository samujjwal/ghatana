/**
 * Lesson Screen
 *
 * Displays lesson content with navigation between sections.
 *
 * @doc.type component
 * @doc.purpose Lesson content viewer
 * @doc.layer product
 * @doc.pattern Screen
 */

import React, { useState } from 'react';
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
import type { LearnStackParamList } from '../navigation/types';
import { useQuery, useMutation } from '@tanstack/react-query';
import { createSessionHeaders } from '../storage/NativeSessionStorage';

type Props = NativeStackScreenProps<LearnStackParamList, 'Lesson'>;

interface Lesson {
  id: string;
  title: string;
  content: string;
  blockType: 'text' | 'video' | 'quiz' | 'interactive';
  durationMinutes: number;
}

interface LessonResponse {
  lesson: Lesson;
  moduleTitle: string;
  progress: {
    currentSectionIndex: number;
    totalSections: number;
    completedSections: string[];
  };
}

async function fetchLesson(moduleId: string, lessonId: string): Promise<LessonResponse> {
  const response = await fetch(`/api/v1/modules/${moduleId}/lessons/${lessonId}`, {
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
  });

  if (!response.ok) {
    throw new Error('Failed to fetch lesson');
  }

  return response.json();
}

async function markSectionComplete(moduleId: string, sectionId: string): Promise<void> {
  await fetch(`/api/v1/enrollments/${moduleId}/progress`, {
    method: 'PATCH',
    headers: createSessionHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({
      sectionId,
      completed: true,
    }),
  });
}

export function LessonScreen({ route, navigation }: Props): React.ReactElement {
  const { moduleId, lessonId } = route.params;
  const [currentSection, setCurrentSection] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['lesson', moduleId, lessonId],
    queryFn: () => fetchLesson(moduleId, lessonId),
  });

  const completeMutation = useMutation({
    mutationFn: () => markSectionComplete(moduleId, lessonId),
  });

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#4F46E5" />
          <Text style={styles.loadingText}>Loading lesson...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (error || !data) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Failed to load lesson</Text>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.backText}>Go Back</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const { lesson, moduleTitle, progress } = data;
  const isLastSection = currentSection >= progress.totalSections - 1;
  const isCompleted = progress.completedSections.includes(lessonId);

  return (
    <SafeAreaView style={styles.container}>
      {/* Progress Bar */}
      <View style={styles.progressBar}>
        <View
          style={[
            styles.progressFill,
            { width: `${((currentSection + 1) / progress.totalSections) * 100}%` },
          ]}
        />
      </View>

      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        {/* Breadcrumb */}
        <Text style={styles.breadcrumb}>{moduleTitle}</Text>

        {/* Lesson Title */}
        <Text style={styles.title}>{lesson.title}</Text>

        {/* Content */}
        <View style={styles.contentBlock}>
          <Text style={styles.contentText}>{lesson.content}</Text>
        </View>

        {/* Action Buttons */}
        <View style={styles.actions}>
          {!isCompleted && (
            <TouchableOpacity
              style={styles.completeButton}
              onPress={() => completeMutation.mutate()}
              disabled={completeMutation.isPending}
            >
              {completeMutation.isPending ? (
                <ActivityIndicator color="#FFFFFF" />
              ) : (
                <Text style={styles.completeButtonText}>Mark as Complete ✓</Text>
              )}
            </TouchableOpacity>
          )}

          <View style={styles.navigationButtons}>
            {currentSection > 0 && (
              <TouchableOpacity
                style={styles.navButton}
                onPress={() => setCurrentSection(currentSection - 1)}
              >
                <Text style={styles.navButtonText}>← Previous</Text>
              </TouchableOpacity>
            )}

            {isLastSection ? (
              <TouchableOpacity
                style={[styles.navButton, styles.primaryNavButton]}
                onPress={() => {
                  // Navigate to quiz or module completion
                  navigation.navigate('Quiz', { moduleId, quizId: lessonId });
                }}
              >
                <Text style={[styles.navButtonText, styles.primaryNavText]}>
                  Take Quiz →
                </Text>
              </TouchableOpacity>
            ) : (
              <TouchableOpacity
                style={[styles.navButton, styles.primaryNavButton]}
                onPress={() => setCurrentSection(currentSection + 1)}
              >
                <Text style={[styles.navButtonText, styles.primaryNavText]}>
                  Next →
                </Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
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
  progressBar: {
    height: 4,
    backgroundColor: '#E5E7EB',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#4F46E5',
  },
  scrollView: {
    flex: 1,
  },
  content: {
    padding: 24,
    paddingBottom: 40,
  },
  breadcrumb: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '500',
    marginBottom: 8,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 24,
    lineHeight: 36,
  },
  contentBlock: {
    marginBottom: 32,
  },
  contentText: {
    fontSize: 16,
    color: '#374151',
    lineHeight: 28,
  },
  actions: {
    marginTop: 24,
  },
  completeButton: {
    backgroundColor: '#059669',
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
    marginBottom: 16,
  },
  completeButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  navigationButtons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  navButton: {
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#D1D5DB',
  },
  primaryNavButton: {
    backgroundColor: '#4F46E5',
    borderColor: '#4F46E5',
  },
  navButtonText: {
    fontSize: 15,
    color: '#4B5563',
    fontWeight: '500',
  },
  primaryNavText: {
    color: '#FFFFFF',
  },
});

/**
 * Quiz Screen
 *
 * Assessment screen for quizzes and tests.
 *
 * @doc.type component
 * @doc.purpose Quiz and assessment interface
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

type Props = NativeStackScreenProps<LearnStackParamList, 'Quiz'>;

interface Question {
  id: string;
  text: string;
  options: string[];
  correctIndex: number;
}

interface Quiz {
  id: string;
  title: string;
  questions: Question[];
  timeLimitMinutes: number;
}

async function fetchQuiz(moduleId: string, quizId: string): Promise<Quiz> {
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null;
  const tenantId = typeof localStorage !== 'undefined' ? localStorage.getItem('tenant_id') : 'default';

  const response = await fetch(`/api/v1/assessments/${quizId}`, {
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'X-Tenant-ID': tenantId || 'default',
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('Failed to fetch quiz');
  }

  const data = await response.json();
  return data.assessment;
}

async function submitQuizAnswers(quizId: string, answers: Record<string, number>): Promise<{ score: number; passed: boolean }> {
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem('auth_token') : null;
  const tenantId = typeof localStorage !== 'undefined' ? localStorage.getItem('tenant_id') : 'default';

  const response = await fetch(`/api/v1/assessments/${quizId}/submit`, {
    method: 'POST',
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'X-Tenant-ID': tenantId || 'default',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ answers }),
  });

  if (!response.ok) {
    throw new Error('Failed to submit quiz');
  }

  return response.json();
}

export function QuizScreen({ route, navigation }: Props): React.ReactElement {
  const { moduleId, quizId } = route.params;
  const [currentQuestion, setCurrentQuestion] = useState(0);
  const [selectedAnswers, setSelectedAnswers] = useState<Record<string, number>>({});
  const [showResults, setShowResults] = useState(false);

  const { data: quiz, isLoading, error } = useQuery({
    queryKey: ['quiz', quizId],
    queryFn: () => fetchQuiz(moduleId, quizId),
  });

  const submitMutation = useMutation({
    mutationFn: () => submitQuizAnswers(quizId, selectedAnswers),
  });

  const handleAnswerSelect = (questionId: string, optionIndex: number) => {
    setSelectedAnswers({ ...selectedAnswers, [questionId]: optionIndex });
  };

  const handleNext = () => {
    if (quiz && currentQuestion < quiz.questions.length - 1) {
      setCurrentQuestion(currentQuestion + 1);
    }
  };

  const handleSubmit = () => {
    submitMutation.mutate(undefined, {
      onSuccess: () => {
        setShowResults(true);
      },
    });
  };

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#4F46E5" />
          <Text style={styles.loadingText}>Loading quiz...</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (error || !quiz) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.errorContainer}>
          <Text style={styles.errorText}>Failed to load quiz</Text>
          <TouchableOpacity onPress={() => navigation.goBack()}>
            <Text style={styles.backText}>Go Back</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // Results View
  if (showResults && submitMutation.data) {
    const { score, passed } = submitMutation.data;
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.resultsContainer}>
          <Text style={styles.resultsIcon}>{passed ? '🎉' : '📝'}</Text>
          <Text style={styles.resultsTitle}>
            {passed ? 'Quiz Passed!' : 'Quiz Completed'}
          </Text>
          <Text style={styles.resultsScore}>
            You scored {Math.round(score * 100)}%
          </Text>
          <Text style={styles.resultsMessage}>
            {passed
              ? 'Great job! You have successfully completed this assessment.'
              : 'Review the material and try again when you\'re ready.'}
          </Text>
          <TouchableOpacity
            style={styles.returnButton}
            onPress={() => navigation.navigate('Dashboard')}
          >
            <Text style={styles.returnButtonText}>Return to Dashboard</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const question = quiz.questions[currentQuestion];
  const isLastQuestion = currentQuestion === quiz.questions.length - 1;
  const hasAnswer = selectedAnswers[question.id] !== undefined;

  return (
    <SafeAreaView style={styles.container}>
      {/* Progress */}
      <View style={styles.progressContainer}>
        <Text style={styles.progressText}>
          Question {currentQuestion + 1} of {quiz.questions.length}
        </Text>
        <View style={styles.progressBar}>
          <View
            style={[
              styles.progressFill,
              { width: `${((currentQuestion + 1) / quiz.questions.length) * 100}%` },
            ]}
          />
        </View>
      </View>

      <ScrollView style={styles.scrollView} contentContainerStyle={styles.content}>
        {/* Question */}
        <Text style={styles.questionNumber}>Question {currentQuestion + 1}</Text>
        <Text style={styles.questionText}>{question.text}</Text>

        {/* Options */}
        <View style={styles.optionsContainer}>
          {question.options.map((option, index) => {
            const isSelected = selectedAnswers[question.id] === index;
            return (
              <TouchableOpacity
                key={index}
                style={[styles.optionButton, isSelected && styles.optionSelected]}
                onPress={() => handleAnswerSelect(question.id, index)}
              >
                <View style={[styles.optionIndicator, isSelected && styles.indicatorSelected]}>
                  <Text style={[styles.optionIndicatorText, isSelected && styles.indicatorTextSelected]}>
                    {String.fromCharCode(65 + index)}
                  </Text>
                </View>
                <Text style={[styles.optionText, isSelected && styles.optionTextSelected]}>
                  {option}
                </Text>
              </TouchableOpacity>
            );
          })}
        </View>
      </ScrollView>

      {/* Navigation */}
      <View style={styles.footer}>
        {currentQuestion > 0 && (
          <TouchableOpacity
            style={styles.backButton}
            onPress={() => setCurrentQuestion(currentQuestion - 1)}
          >
            <Text style={styles.backButtonText}>← Back</Text>
          </TouchableOpacity>
        )}

        {isLastQuestion ? (
          <TouchableOpacity
            style={[styles.nextButton, !hasAnswer && styles.nextButtonDisabled]}
            onPress={handleSubmit}
            disabled={!hasAnswer || submitMutation.isPending}
          >
            {submitMutation.isPending ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.nextButtonText}>Submit Quiz ✓</Text>
            )}
          </TouchableOpacity>
        ) : (
          <TouchableOpacity
            style={[styles.nextButton, !hasAnswer && styles.nextButtonDisabled]}
            onPress={handleNext}
            disabled={!hasAnswer}
          >
            <Text style={styles.nextButtonText}>Next →</Text>
          </TouchableOpacity>
        )}
      </View>
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
  progressContainer: {
    padding: 20,
    backgroundColor: '#F9FAFB',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  progressText: {
    fontSize: 14,
    color: '#6B7280',
    marginBottom: 8,
  },
  progressBar: {
    height: 6,
    backgroundColor: '#E5E7EB',
    borderRadius: 3,
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#4F46E5',
    borderRadius: 3,
  },
  scrollView: {
    flex: 1,
  },
  content: {
    padding: 24,
    paddingBottom: 40,
  },
  questionNumber: {
    fontSize: 14,
    color: '#4F46E5',
    fontWeight: '600',
    marginBottom: 8,
  },
  questionText: {
    fontSize: 22,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 24,
    lineHeight: 32,
  },
  optionsContainer: {
    gap: 12,
  },
  optionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F9FAFB',
    borderWidth: 2,
    borderColor: '#E5E7EB',
    borderRadius: 12,
    padding: 16,
  },
  optionSelected: {
    borderColor: '#4F46E5',
    backgroundColor: '#EEF2FF',
  },
  optionIndicator: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: '#E5E7EB',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  indicatorSelected: {
    backgroundColor: '#4F46E5',
  },
  optionIndicatorText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6B7280',
  },
  indicatorTextSelected: {
    color: '#FFFFFF',
  },
  optionText: {
    fontSize: 16,
    color: '#374151',
    flex: 1,
  },
  optionTextSelected: {
    color: '#4F46E5',
    fontWeight: '500',
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
  },
  backButton: {
    paddingVertical: 12,
    paddingHorizontal: 20,
  },
  backButtonText: {
    fontSize: 16,
    color: '#6B7280',
  },
  nextButton: {
    backgroundColor: '#4F46E5',
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 12,
  },
  nextButtonDisabled: {
    backgroundColor: '#D1D5DB',
  },
  nextButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  resultsContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  resultsIcon: {
    fontSize: 64,
    marginBottom: 24,
  },
  resultsTitle: {
    fontSize: 28,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 12,
  },
  resultsScore: {
    fontSize: 48,
    fontWeight: '700',
    color: '#4F46E5',
    marginBottom: 16,
  },
  resultsMessage: {
    fontSize: 16,
    color: '#6B7280',
    textAlign: 'center',
    marginBottom: 32,
    lineHeight: 24,
  },
  returnButton: {
    backgroundColor: '#4F46E5',
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 12,
  },
  returnButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
});

/**
 * Navigation Types
 *
 * Type definitions for React Navigation
 *
 * @doc.type module
 * @doc.purpose Navigation type definitions
 * @doc.layer product
 * @doc.pattern Types
 */

// Tab Navigator Param List
export type TabParamList = {
  Learn: undefined;
  Explore: undefined;
  Profile: undefined;
};

// Learn Stack Param List
export type LearnStackParamList = {
  Dashboard: undefined;
  Enrollments: undefined;
  Modules: { category?: string } | undefined;
  ModuleDetail: { moduleId: string };
  Lesson: { moduleId: string; lessonId: string };
  Quiz: { moduleId: string; quizId: string };
  AITutor: { moduleId?: string; context?: string } | undefined;
};

// Explore Stack Param List
export type ExploreStackParamList = {
  Search: undefined;
  Marketplace: undefined;
  ModuleDetail: { moduleId: string };
};

// Profile Stack Param List
export type ProfileStackParamList = {
  ProfileMain: undefined;
  Downloads: undefined;
  Settings: undefined;
  Achievements: undefined;
};

// Legacy Root Stack (for backward compatibility)
export type RootStackParamList = {
  Home: undefined;
  Modules: { category?: string } | undefined;
  ModuleDetail: { moduleId: string };
  Lesson: { moduleId: string; lessonId: string };
  Quiz: { moduleId: string; quizId: string };
  Profile: undefined;
  Downloads: undefined;
};

// Module type definition
export interface Lesson {
  id: string;
  title: string;
  content: string;
  durationMinutes: number;
}

export interface Quiz {
  id: string;
  title: string;
  questions: Question[];
}

export interface Question {
  id: string;
  text: string;
  options: string[];
  correctIndex: number;
}

export interface Module {
  id: string;
  title: string;
  description: string;
  category: string;
  grade: number;
  lessons: Lesson[];
  quizzes: Quiz[];
  totalSizeBytes: number;
  version: string;
}

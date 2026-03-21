/**
 * Assessment Service Implementation
 * Part of Execution Plan item #5: Improve Test Coverage
 */

import { PrismaClient } from '@prisma/client';

export interface Assessment {
  id: string;
  title: string;
  description?: string;
  questions: Question[];
  createdAt: Date;
  updatedAt: Date;
}

export interface Question {
  id: string;
  text: string;
  type: 'multiple_choice' | 'open_ended' | 'simulation';
  options?: string[];
  correctAnswer?: string;
  points: number;
}

export interface Submission {
  id: string;
  assessmentId: string;
  userId: string;
  answers: Answer[];
  score: number;
  submittedAt: Date;
}

export interface Answer {
  questionId: string;
  answer: string;
  isCorrect?: boolean;
}

export class AssessmentService {
  constructor(private prisma: PrismaClient) {}

  async getAssessment(id: string): Promise<Assessment | null> {
    return this.prisma.assessment.findUnique({
      where: { id },
      include: { questions: true },
    }) as Promise<Assessment | null>;
  }

  async listAssessments(options: { limit: number; offset: number }): Promise<Assessment[]> {
    return this.prisma.assessment.findMany({
      take: options.limit,
      skip: options.offset,
      orderBy: { createdAt: 'desc' },
    }) as Promise<Assessment[]>;
  }

  async submitAssessment(data: {
    assessmentId: string;
    userId: string;
    answers: Answer[];
  }): Promise<Submission> {
    const assessment = await this.getAssessment(data.assessmentId);
    if (!assessment) {
      throw new Error('Assessment not found');
    }

    let score = 0;
    const totalPoints = assessment.questions.reduce((sum, q) => sum + q.points, 0);

    const gradedAnswers = data.answers.map((answer) => {
      const question = assessment.questions.find((q) => q.id === answer.questionId);
      const isCorrect = question?.correctAnswer === answer.answer;
      if (isCorrect) {
        score += question?.points || 0;
      }
      return { ...answer, isCorrect };
    });

    const finalScore = totalPoints > 0 ? (score / totalPoints) * 100 : 0;

    return this.prisma.submission.create({
      data: {
        assessmentId: data.assessmentId,
        userId: data.userId,
        answers: gradedAnswers,
        score: finalScore,
      },
    }) as Promise<Submission>;
  }

  async getSubmission(id: string): Promise<Submission | null> {
    return this.prisma.submission.findUnique({
      where: { id },
    }) as Promise<Submission | null>;
  }
}

export default AssessmentService;

import { describe, expect, it } from 'vitest'

import type { ExamQuestion } from '@/api/exam-business'
import {
  buildAnswerCardGroups,
  buildSubmitAnswers,
  countAnsweredQuestions,
  formatRemainingTime,
  groupQuestionsByType,
  isQuestionAnswered,
  questionIndexById,
} from './exam-session'

const questions: ExamQuestion[] = [
  {
    questionId: 1,
    type: 'SINGLE_CHOICE',
    stem: 'Single',
    score: 5,
    sortOrder: 10,
    selectedLabels: [],
    answerText: null,
    attachments: [],
    options: [],
  },
  {
    questionId: 2,
    type: 'MULTIPLE_CHOICE',
    stem: 'Multiple',
    score: 5,
    sortOrder: 20,
    selectedLabels: [],
    answerText: null,
    attachments: [],
    options: [],
  },
  {
    questionId: 3,
    type: 'WRITING',
    stem: 'Writing',
    score: 15,
    sortOrder: 30,
    selectedLabels: [],
    answerText: null,
    attachments: [],
    options: [],
  },
]

describe('exam session helpers', () => {
  it('counts answered questions by question type', () => {
    expect(isQuestionAnswered(questions[0], { 1: 'A' }, {})).toBe(true)
    expect(isQuestionAnswered(questions[1], {}, { 2: ['C', 'A'] })).toBe(true)
    expect(isQuestionAnswered(questions[2], {}, {}, { 3: 'Essay answer' })).toBe(true)
    expect(countAnsweredQuestions(questions, { 1: 'A' }, { 2: [] }, { 3: 'Essay answer' })).toBe(2)
  })

  it('builds stable submit payloads', () => {
    expect(buildSubmitAnswers(questions, { 1: 'B' }, { 2: ['C', 'A'] }, { 3: 'Essay answer' })).toEqual([
      { questionId: 1, selectedLabels: ['B'] },
      { questionId: 2, selectedLabels: ['A', 'C'] },
      { questionId: 3, answerText: 'Essay answer' },
    ])
  })

  it('formats non-negative remaining time', () => {
    expect(formatRemainingTime(65)).toBe('01:05')
    expect(formatRemainingTime(-1)).toBe('00:00')
  })

  it('builds grouped navigation models', () => {
    expect(questionIndexById(questions, 2)).toBe(1)
    expect(groupQuestionsByType(questions).map((group) => [group.id, group.questions.length])).toEqual([
      ['SINGLE_CHOICE', 1],
      ['MULTIPLE_CHOICE', 1],
      ['WRITING', 1],
    ])
    expect(buildAnswerCardGroups('ANSWER_SHEET', questions)).toEqual([
      { id: 'ANSWER_SHEET', title: '答题卡', questions },
    ])
  })
})

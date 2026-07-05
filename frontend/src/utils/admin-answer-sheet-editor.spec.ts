import { describe, expect, it } from 'vitest'

import {
  answerCardValidationError,
  answerOptionEndLabel,
  answerOptionEndLabels,
  answerOptionLabelsTo,
  answerOptionRangeText,
  calculateAnswerCardQuestionCount,
  calculateAnswerCardTotalScore,
  hasMaterialFiles,
  type AnswerCardItemForm,
} from './admin-answer-sheet-editor'

describe('admin answer sheet editor rules', () => {
  it('calculates answer card totals and material availability', () => {
    expect(calculateAnswerCardTotalScore([item({ score: 5 }), item({ questionNo: 2, score: 15 })])).toBe(20)
    expect(calculateAnswerCardQuestionCount([item(), item({ questionNo: 2 })])).toBe(2)
    expect(hasMaterialFiles([{ title: '材料', description: '', sortOrder: 10, files: [] }])).toBe(false)
    expect(hasMaterialFiles([{ title: '材料', description: '', sortOrder: 10, files: [materialFile()] }])).toBe(true)
  })

  it('builds answer option labels from A to a user selected end label', () => {
    expect(answerOptionEndLabels()).toContain('Z')
    expect(answerOptionLabelsTo('H')).toEqual(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'])
    const labelsToZ = answerOptionLabelsTo('z')
    expect(labelsToZ[labelsToZ.length - 1]).toBe('Z')
    expect(answerOptionLabelsTo('invalid')).toEqual(['A', 'B', 'C', 'D'])
    expect(answerOptionEndLabel(['A', 'B', 'C', 'D', 'E'])).toBe('E')
    expect(answerOptionRangeText(['A', 'B', 'C', 'D', 'E'])).toBe('A-E')
  })

  it('validates answer card item rules by answer type', () => {
    expect(answerCardValidationError([item(), item()])).toBe('题号 1 重复')
    expect(answerCardValidationError([item({ score: 0 })])).toBe('第 1 题分值必须大于 0')
    expect(answerCardValidationError([item({ optionLabels: ['A'] })])).toBe('第 1 题至少需要两个选项')
    expect(answerCardValidationError([item({ correctLabels: ['A', 'B'] })])).toBe('第 1 题必须且只能有一个正确答案')
    expect(answerCardValidationError([item({ answerType: 'MULTIPLE_CHOICE', correctLabels: ['A'] })])).toBe('第 1 题至少需要两个正确答案')
    expect(answerCardValidationError([item({ correctLabels: ['E'] })])).toBe('第 1 题答案不在选项范围内')
    expect(answerCardValidationError([item({ answerType: 'WRITING', optionLabels: [], correctLabels: [] })])).toBeNull()
  })
})

function item(overrides: Partial<AnswerCardItemForm> = {}): AnswerCardItemForm {
  return {
    questionNo: 1,
    answerType: 'SINGLE_CHOICE',
    optionLabels: ['A', 'B', 'C', 'D'],
    correctLabels: ['A'],
    score: 5,
    sortOrder: 10,
    ...overrides,
  }
}

function materialFile() {
  return {
    sourceType: 'UPLOAD' as const,
    displayName: '试卷材料.pdf',
    description: '',
    fileName: 'paper.pdf',
    fileUrl: '/uploads/paper.pdf',
    mediaType: 'FILE' as const,
    sortOrder: 10,
  }
}

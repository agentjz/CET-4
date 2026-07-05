import type { ExamPayload } from '@/api/exam-business'

export type AnswerCardItemForm = ExamPayload['answerCardItems'][number]
export type MaterialGroupForm = ExamPayload['materialGroups'][number]

export const DEFAULT_ANSWER_OPTION_END_LABEL = 'D'
export const MAX_ANSWER_OPTION_END_LABEL = 'Z'

export function calculateAnswerCardTotalScore(items: AnswerCardItemForm[]) {
  return items.reduce((sum, item) => sum + item.score, 0)
}

export function calculateAnswerCardQuestionCount(items: AnswerCardItemForm[]) {
  return items.length
}

export function hasMaterialFiles(groups: MaterialGroupForm[]) {
  return groups.some((group) => group.files.length > 0)
}

export function answerOptionEndLabels(maxLabel = MAX_ANSWER_OPTION_END_LABEL) {
  const maxCode = normalizeAnswerOptionEndLabel(maxLabel).charCodeAt(0)
  const labels: string[] = []
  for (let code = 'B'.charCodeAt(0); code <= maxCode; code += 1) {
    labels.push(String.fromCharCode(code))
  }
  return labels
}

export function answerOptionLabelsTo(endLabel: string) {
  const normalizedEnd = normalizeAnswerOptionEndLabel(endLabel)
  const labels: string[] = []
  for (let code = 'A'.charCodeAt(0); code <= normalizedEnd.charCodeAt(0); code += 1) {
    labels.push(String.fromCharCode(code))
  }
  return labels
}

export function answerOptionRangeText(labels: string[]) {
  return `A-${answerOptionEndLabel(labels)}`
}

export function answerOptionEndLabel(labels: string[]) {
  if (labels.length < 2) {
    return DEFAULT_ANSWER_OPTION_END_LABEL
  }
  return normalizeAnswerOptionEndLabel(labels[labels.length - 1])
}

function normalizeAnswerOptionEndLabel(label: string) {
  const normalized = label.trim().toUpperCase()
  if (/^[B-Z]$/.test(normalized)) {
    return normalized
  }
  return DEFAULT_ANSWER_OPTION_END_LABEL
}

export function answerCardValidationError(items: AnswerCardItemForm[]) {
  const questionNos = new Set<number>()
  for (const item of items) {
    if (questionNos.has(item.questionNo)) {
      return `题号 ${item.questionNo} 重复`
    }
    questionNos.add(item.questionNo)
    if (item.score <= 0) {
      return `第 ${item.questionNo} 题分值必须大于 0`
    }
    if (item.answerType === 'WRITING') {
      continue
    }
    if (item.optionLabels.length < 2) {
      return `第 ${item.questionNo} 题至少需要两个选项`
    }
    if (item.answerType === 'SINGLE_CHOICE' && item.correctLabels.length !== 1) {
      return `第 ${item.questionNo} 题必须且只能有一个正确答案`
    }
    if (item.answerType === 'MULTIPLE_CHOICE' && item.correctLabels.length < 2) {
      return `第 ${item.questionNo} 题至少需要两个正确答案`
    }
    if (item.correctLabels.some((label) => !item.optionLabels.includes(label))) {
      return `第 ${item.questionNo} 题答案不在选项范围内`
    }
  }
  return null
}

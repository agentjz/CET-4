import type { ExcelImportResult, PageResult } from './admin'
import type { QuestionTypeCode } from '@/utils/question-types'

export type { ExcelImportResult, PageResult }

export interface NamedCategory {
  id: number
  name: string
  description: string | null
  sortOrder: number
}

export interface QuestionCategoryPayload {
  name: string
  description: string
  sortOrder: number
}

export interface QuestionBank {
  id: number
  categoryId: number
  categoryName: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'DISABLED'
  questionCount: number
  singleChoiceCount: number
  multipleChoiceCount: number
  writingCount: number
}

export interface QuestionBankPayload {
  categoryId: number
  name: string
  description: string
  status: QuestionBank['status']
}

export interface QuestionOptionPayload {
  label: string
  content: string
  correct: boolean
}

export interface QuestionOption {
  id: number
  label: string
  content: string
  correct: boolean
  sortOrder: number
}

export interface QuestionAttachmentPayload {
  fileName: string
  fileUrl: string
  mediaType: 'IMAGE' | 'AUDIO' | 'VIDEO' | 'FILE'
}

export interface QuestionAttachment extends QuestionAttachmentPayload {
  id: number
  sortOrder: number
}

export type ExamMaterialSourceType = 'UPLOAD' | 'EXISTING_ATTACHMENT' | 'EXTERNAL_LINK' | 'LOCAL_ASSET'

export interface ExamMaterialFilePayload {
  sourceType: ExamMaterialSourceType
  displayName: string
  description: string
  fileName: string
  fileUrl: string
  mediaType: QuestionAttachmentPayload['mediaType']
  sortOrder: number
}

export interface ExamMaterialFile extends ExamMaterialFilePayload {
  id: number
}

export interface ExamMaterialGroupPayload {
  title: string
  description: string
  sortOrder: number
  files: ExamMaterialFilePayload[]
}

export interface ExamMaterialGroup extends Omit<ExamMaterialGroupPayload, 'files'> {
  id: number
  files: ExamMaterialFile[]
}

export interface ExamAnswerCardItemPayload {
  questionNo: number
  answerType: QuestionPayload['type']
  optionLabels: string[]
  correctLabels: string[]
  score: number
  sortOrder: number
}

export interface ExamAnswerCardItem extends ExamAnswerCardItemPayload {
  id: number
}

export interface QuestionPayload {
  bankId: number
  type: QuestionTypeCode
  stem: string
  analysis: string
  difficulty: 'EASY' | 'HARD'
  status: 'ACTIVE' | 'DISABLED'
  options: QuestionOptionPayload[]
  correctLabels?: string[]
  attachments: QuestionAttachmentPayload[]
}

export interface Question {
  id: number
  bankId: number
  bankName: string
  type: QuestionPayload['type']
  stem: string
  analysis: string | null
  difficulty: QuestionPayload['difficulty']
  status: QuestionPayload['status']
  options: QuestionOption[]
  attachments: QuestionAttachment[]
}

export interface ExamPayload {
  title: string
  description: string
  qualifyScore: number
  startTime: string
  endTime: string
  durationMinutes: number
  timeLimit: boolean
  attemptLimit: number | null
  examMode: 'STRUCTURED' | 'ANSWER_SHEET'
  displayMode: 'PAGED' | 'ALL'
  questionOrderMode: 'FIXED' | 'RANDOM'
  openType: 'PUBLIC' | 'DEPARTMENT'
  departmentIds: number[]
  rules: ExamRulePayload[]
  paperQuestions: ExamPaperQuestionPayload[]
  materialGroups: ExamMaterialGroupPayload[]
  answerCardItems: ExamAnswerCardItemPayload[]
}

export interface ExamRulePayload {
  bankId: number
  singleCount: number
  singleScore: number
  multipleCount: number
  multipleScore: number
  writingCount: number
  writingScore: number
}

export interface ExamRule extends ExamRulePayload {
  id: number
  bankName: string
  sortOrder: number
}

export interface ExamPaperQuestionPayload {
  questionId: number
  score: number
  sortOrder: number
}

export interface ExamPaperQuestion extends ExamPaperQuestionPayload {
  bankId: number
  bankName: string
  type: QuestionPayload['type']
  stem: string
}

export interface Exam {
  id: number
  totalScore: number
  questionCount: number
  title: string
  description: string | null
  qualifyScore: number
  startTime: string
  endTime: string
  durationMinutes: number
  timeLimit: boolean
  attemptLimit: number | null
  examMode: ExamPayload['examMode']
  displayMode: ExamPayload['displayMode']
  questionOrderMode: ExamPayload['questionOrderMode']
  openType: ExamPayload['openType']
  departmentIds: number[]
  rules: ExamRule[]
  paperQuestions: ExamPaperQuestion[]
  materialGroups: ExamMaterialGroup[]
  answerCardItems: ExamAnswerCardItem[]
  status: 'DRAFT' | 'PUBLISHED' | 'CLOSED'
}

export interface ExamQuestionOption {
  id: number
  label: string
  content: string
  sortOrder: number
}

export interface ExamQuestion {
  questionId: number
  type: QuestionPayload['type']
  stem: string
  score: number
  sortOrder: number
  selectedLabels: string[]
  answerText: string | null
  attachments: QuestionAttachment[]
  options: ExamQuestionOption[]
}

export interface ExamSession {
  examId: number
  attemptId: number
  title: string
  durationMinutes: number
  examMode: ExamPayload['examMode']
  displayMode: ExamPayload['displayMode']
  startedAt: string
  attemptStatus: 'IN_PROGRESS' | 'SUBMITTED'
  materialGroups: ExamMaterialGroup[]
  questions: ExamQuestion[]
}

export interface ExamResult {
  id: number
  attemptId: number
  examId: number
  examTitle: string
  userId: number
  username: string | null
  userName: string | null
  departmentName: string | null
  totalScore: number
  obtainedScore: number
  objectiveScore: number
  subjectiveScore: number
  correctCount: number
  questionCount: number
  gradingStatus: 'PENDING_REVIEW' | 'FINAL'
  passed: boolean
  submittedAt: string
}

export interface ExamResultQuestion {
  questionId: number
  type: QuestionPayload['type']
  stem: string
  analysis: string | null
  score: number
  obtainedScore: number
  sortOrder: number
  selectedLabels: string[]
  answerText: string | null
  correctLabels: string[]
  correct: boolean | null
  reviewComment: string | null
  reviewerName: string | null
  reviewedAt: string | null
  attachments: QuestionAttachment[]
  options: ExamQuestionOption[]
}

export interface ExamResultDetail extends ExamResult {
  questions: ExamResultQuestion[]
}

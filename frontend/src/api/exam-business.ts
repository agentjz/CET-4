import { examBusinessAdapter } from './adapters/current'
import type { ExcelImportResult } from './admin'
import type {
  Exam,
  ExamPayload,
  ExamResult,
  ExamResultDetail,
  ExamSession,
  NamedCategory,
  PageResult,
  Question,
  QuestionAttachmentPayload,
  QuestionBank,
  QuestionBankPayload,
  QuestionCategoryPayload,
  QuestionPayload,
} from './exam-business-types'

export type {
  Exam,
  ExamPaperQuestion,
  ExamPaperQuestionPayload,
  ExamMaterialFile,
  ExamMaterialFilePayload,
  ExamMaterialGroup,
  ExamMaterialGroupPayload,
  ExamMaterialSourceType,
  ExamAnswerCardItem,
  ExamAnswerCardItemPayload,
  ExamPayload,
  ExamQuestion,
  ExamQuestionOption,
  ExamResult,
  ExamResultDetail,
  ExamResultQuestion,
  ExamRule,
  ExamRulePayload,
  ExamSession,
  NamedCategory,
  Question,
  QuestionAttachment,
  QuestionAttachmentPayload,
  QuestionBank,
  QuestionBankPayload,
  QuestionCategoryPayload,
  QuestionOption,
  QuestionOptionPayload,
  QuestionPayload,
} from './exam-business-types'

export function fetchQuestionCategories(): Promise<NamedCategory[]> {
  return examBusinessAdapter.fetchQuestionCategories()
}

export function createQuestionCategory(payload: QuestionCategoryPayload): Promise<NamedCategory> {
  return examBusinessAdapter.createQuestionCategory(payload)
}

export function updateQuestionCategory(id: number, payload: QuestionCategoryPayload): Promise<NamedCategory> {
  return examBusinessAdapter.updateQuestionCategory(id, payload)
}

export function deleteQuestionCategory(id: number): Promise<void> {
  return examBusinessAdapter.deleteQuestionCategory(id)
}

export function fetchQuestionBanks(params: { page: number; size: number; keyword?: string }): Promise<PageResult<QuestionBank>> {
  return examBusinessAdapter.fetchQuestionBanks(params)
}

export function createQuestionBank(payload: QuestionBankPayload): Promise<QuestionBank> {
  return examBusinessAdapter.createQuestionBank(payload)
}

export function updateQuestionBank(id: number, payload: QuestionBankPayload): Promise<QuestionBank> {
  return examBusinessAdapter.updateQuestionBank(id, payload)
}

export function fetchQuestions(params: { page: number; size: number; keyword?: string; bankId?: number }): Promise<PageResult<Question>> {
  return examBusinessAdapter.fetchQuestions(params)
}

export function fetchQuestionDetail(id: number): Promise<Question> {
  return examBusinessAdapter.fetchQuestionDetail(id)
}

export function createQuestion(payload: QuestionPayload): Promise<Question> {
  return examBusinessAdapter.createQuestion(payload)
}

export function updateQuestion(id: number, payload: QuestionPayload): Promise<Question> {
  return examBusinessAdapter.updateQuestion(id, payload)
}

export function downloadQuestionImportTemplate(): Promise<Blob> {
  return examBusinessAdapter.downloadQuestionImportTemplate()
}

export function importQuestions(file: File): Promise<ExcelImportResult> {
  return examBusinessAdapter.importQuestions(file)
}

export function downloadQuestionExport(): Promise<Blob> {
  return examBusinessAdapter.downloadQuestionExport()
}

export function uploadFile(file: File): Promise<QuestionAttachmentPayload> {
  return examBusinessAdapter.uploadFile(file)
}

export function fetchAdminExams(params: { page: number; size: number; keyword?: string }): Promise<PageResult<Exam>> {
  return examBusinessAdapter.fetchAdminExams(params)
}

export function fetchAdminExamDetail(id: number): Promise<Exam> {
  return examBusinessAdapter.fetchAdminExamDetail(id)
}

export function createExam(payload: ExamPayload): Promise<Exam> {
  return examBusinessAdapter.createExam(payload)
}

export function updateExam(id: number, payload: ExamPayload): Promise<Exam> {
  return examBusinessAdapter.updateExam(id, payload)
}

export function publishExam(id: number): Promise<Exam> {
  return examBusinessAdapter.publishExam(id)
}

export function copyExam(id: number): Promise<Exam> {
  return examBusinessAdapter.copyExam(id)
}

export function downloadExamPaper(id: number): Promise<Blob> {
  return examBusinessAdapter.downloadExamPaper(id)
}

export function revokeExam(id: number): Promise<Exam> {
  return examBusinessAdapter.revokeExam(id)
}

export function closeExam(id: number): Promise<Exam> {
  return examBusinessAdapter.closeExam(id)
}

export function deleteExam(id: number): Promise<void> {
  return examBusinessAdapter.deleteExam(id)
}

export function fetchAdminResults(params?: { examId?: number }): Promise<ExamResult[]> {
  return examBusinessAdapter.fetchAdminResults(params)
}

export function fetchAdminResultDetail(resultId: number): Promise<ExamResultDetail> {
  return examBusinessAdapter.fetchAdminResultDetail(resultId)
}

export function fetchExamTasks(): Promise<Exam[]> {
  return examBusinessAdapter.fetchExamTasks()
}

export function startExam(examId: number): Promise<ExamSession> {
  return examBusinessAdapter.startExam(examId)
}

export function saveExamAnswers(examId: number, answers: Array<{ questionId: number; selectedLabels?: string[]; answerText?: string }>): Promise<ExamSession> {
  return examBusinessAdapter.saveExamAnswers(examId, answers)
}

export function submitExam(examId: number, answers: Array<{ questionId: number; selectedLabels?: string[]; answerText?: string }>): Promise<ExamResult> {
  return examBusinessAdapter.submitExam(examId, answers)
}

export function reviewWritingQuestion(resultId: number, questionId: number, payload: { score: number; comment: string }): Promise<ExamResultDetail> {
  return examBusinessAdapter.reviewWritingQuestion(resultId, questionId, payload)
}

export function completeResultReview(resultId: number): Promise<ExamResultDetail> {
  return examBusinessAdapter.completeResultReview(resultId)
}

export function fetchMyExamResults(): Promise<ExamResult[]> {
  return examBusinessAdapter.fetchMyExamResults()
}

export function fetchMyExamResultDetail(resultId: number): Promise<ExamResultDetail> {
  return examBusinessAdapter.fetchMyExamResultDetail(resultId)
}

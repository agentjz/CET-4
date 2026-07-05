import { describe, expect, it, vi } from 'vitest'

import { apiClient } from './client'
import {
  createExam,
  createQuestion,
  createQuestionBank,
  createQuestionCategory,
  deleteExam,
  closeExam,
  completeResultReview,
  copyExam,
  deleteQuestionCategory,
  downloadExamPaper,
  downloadQuestionExport,
  downloadQuestionImportTemplate,
  fetchAdminExamDetail,
  fetchAdminResultDetail,
  fetchAdminResults,
  fetchExamTasks,
  fetchMyExamResultDetail,
  fetchMyExamResults,
  fetchQuestionBanks,
  fetchQuestionCategories,
  fetchQuestionDetail,
  importQuestions,
  publishExam,
  reviewWritingQuestion,
  revokeExam,
  saveExamAnswers,
  startExam,
  submitExam,
  updateQuestionCategory,
  uploadFile,
} from './exam-business'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const ok = <T>(data: T) => ({ data: { code: 0, message: 'OK', data, timestamp: '2026-07-01T00:00:00Z' } })

describe('exam business api', () => {
  it('loads paged management resources', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({ records: [], total: 0, page: 1, size: 20 }))

    await fetchQuestionBanks({ page: 1, size: 20, keyword: 'english' })

    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/question-banks', {
      params: { page: 1, size: 20, keyword: 'english' },
    })
  })

  it('writes question paper and exam payloads', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({}))
    vi.mocked(apiClient.post).mockResolvedValue(ok({}))
    vi.mocked(apiClient.put).mockResolvedValue(ok({}))
    vi.mocked(apiClient.delete).mockResolvedValue(ok({}))

    await createQuestionBank({ categoryId: 1, name: 'bank', description: '', status: 'ACTIVE' })
    await fetchQuestionCategories()
    await createQuestionCategory({ name: 'category', description: '', sortOrder: 10 })
    await updateQuestionCategory(3, { name: 'category updated', description: 'desc', sortOrder: 20 })
    await deleteQuestionCategory(4)
    await createQuestion({
      bankId: 1,
      type: 'SINGLE_CHOICE',
      stem: 'stem',
      analysis: '',
      difficulty: 'EASY',
      status: 'ACTIVE',
      options: [
        { label: 'A', content: 'a', correct: true },
        { label: 'B', content: 'b', correct: false },
      ],
      attachments: [{ fileName: 'chart.png', fileUrl: 'https://example.com/chart.png', mediaType: 'IMAGE' }],
    })
    await createExam({
      title: 'exam',
      description: '',
      qualifyScore: 3,
      startTime: '2026-01-01T00:00:00',
      endTime: '2026-12-31T23:59:59',
      durationMinutes: 30,
      timeLimit: true,
      attemptLimit: null,
      examMode: 'STRUCTURED',
      displayMode: 'PAGED',
      questionOrderMode: 'FIXED',
      openType: 'PUBLIC',
      departmentIds: [],
      rules: [{ bankId: 1, singleCount: 1, singleScore: 5, multipleCount: 0, multipleScore: 0, writingCount: 1, writingScore: 15 }],
      paperQuestions: [{ questionId: 2, score: 5, sortOrder: 10 }],
      materialGroups: [],
      answerCardItems: [],
    })
    await fetchAdminExamDetail(1)
    await publishExam(1)
    await copyExam(1)
    await downloadExamPaper(1)
    await revokeExam(1)
    await closeExam(1)
    await deleteExam(1)

    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/question-banks', {
      categoryId: 1,
      name: 'bank',
      description: '',
      status: 'ACTIVE',
    })
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/question-banks/categories')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/question-banks/categories', {
      name: 'category',
      description: '',
      sortOrder: 10,
    })
    expect(apiClient.put).toHaveBeenCalledWith('/api/admin/question-banks/categories/3', {
      name: 'category updated',
      description: 'desc',
      sortOrder: 20,
    })
    expect(apiClient.delete).toHaveBeenCalledWith('/api/admin/question-banks/categories/4')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/questions', {
      bankId: 1,
      type: 'SINGLE_CHOICE',
      stem: 'stem',
      analysis: '',
      difficulty: 'EASY',
      status: 'ACTIVE',
      options: [
        { label: 'A', content: 'a', correct: true },
        { label: 'B', content: 'b', correct: false },
      ],
      attachments: [{ fileName: 'chart.png', fileUrl: 'https://example.com/chart.png', mediaType: 'IMAGE' }],
    })
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/exams', {
      title: 'exam',
      description: '',
      qualifyScore: 3,
      startTime: '2026-01-01T00:00:00',
      endTime: '2026-12-31T23:59:59',
      durationMinutes: 30,
      timeLimit: true,
      attemptLimit: null,
      examMode: 'STRUCTURED',
      displayMode: 'PAGED',
      questionOrderMode: 'FIXED',
      openType: 'PUBLIC',
      departmentIds: [],
      rules: [{ bankId: 1, singleCount: 1, singleScore: 5, multipleCount: 0, multipleScore: 0, writingCount: 1, writingScore: 15 }],
      paperQuestions: [{ questionId: 2, score: 5, sortOrder: 10 }],
      materialGroups: [],
      answerCardItems: [],
    })
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/exams/1')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/exams/1/publish')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/exams/1/copy')
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/exams/1/download', { responseType: 'blob' })
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/exams/1/revoke')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/exams/1/close')
    expect(apiClient.delete).toHaveBeenCalledWith('/api/admin/exams/1')
  })

  it('uses exam portal endpoints for task start submit and results', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok([]))
    vi.mocked(apiClient.post).mockResolvedValue(ok({}))

    await fetchExamTasks()
    await startExam(1)
    await saveExamAnswers(1, [{ questionId: 2, selectedLabels: ['A'] }])
    await submitExam(1, [{ questionId: 2, selectedLabels: ['A'] }])
    await fetchAdminResults({ examId: 1 })
    await fetchAdminResultDetail(9)
    await reviewWritingQuestion(9, 3, { score: 13, comment: '结构清晰' })
    await completeResultReview(9)
    await fetchMyExamResults()
    await fetchMyExamResultDetail(9)

    expect(apiClient.get).toHaveBeenCalledWith('/api/exam/tasks')
    expect(apiClient.post).toHaveBeenCalledWith('/api/exam/1/start')
    expect(apiClient.post).toHaveBeenCalledWith('/api/exam/1/answers', { answers: [{ questionId: 2, selectedLabels: ['A'] }] })
    expect(apiClient.post).toHaveBeenCalledWith('/api/exam/1/submit', {
      answers: [{ questionId: 2, selectedLabels: ['A'] }],
    })
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/results', { params: { examId: 1 } })
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/results/9')
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/results/9/questions/3/review', { score: 13, comment: '结构清晰' })
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/results/9/complete-review')
    expect(apiClient.get).toHaveBeenCalledWith('/api/exam/results')
    expect(apiClient.get).toHaveBeenCalledWith('/api/exam/results/9')
  })

  it('uses question detail template and import endpoints', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(ok({}))
    vi.mocked(apiClient.post).mockResolvedValue(ok({}))

    await fetchQuestionDetail(8)
    await downloadQuestionImportTemplate()
    await downloadQuestionExport()
    await importQuestions(new File(['xlsx'], 'questions.xlsx'))
    await uploadFile(new File(['audio'], 'listening.mp3'))

    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/questions/8')
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/questions/import-template', { responseType: 'blob' })
    expect(apiClient.get).toHaveBeenCalledWith('/api/admin/questions/export', { responseType: 'blob' })
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/questions/import', expect.any(FormData))
    expect(apiClient.post).toHaveBeenCalledWith('/api/admin/files', expect.any(FormData))
  })
})

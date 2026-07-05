import type { PageResult } from '../../admin'
import type { ExamBusinessAdapter } from '../exam-business-adapter'
import type {
  Exam,
  ExamPayload,
  ExamQuestion,
  ExamResultDetail,
  Question,
  QuestionAttachment,
  QuestionAttachmentPayload,
  QuestionBank,
  QuestionOption,
} from '../../exam-business-types'
import {
  buildSessionQuestions,
  clone,
  currentDemoState,
  gradeSubmission,
  nextId,
  nowIso,
  refreshBankCounts,
  updateResultTotals,
} from './demo-store'
import { isManualReviewType } from '@/utils/question-types'

function paginate<T>(records: T[], page: number, size: number): PageResult<T> {
  return { records: clone(records.slice((page - 1) * size, page * size)), total: records.length, page, size }
}

function currentUser() {
  const state = currentDemoState()
  return state.users.find((user) => user.id === state.currentUserId) || state.users[0]
}

function bankName(bankId: number) {
  return currentDemoState().banks.find((bank) => bank.id === bankId)?.name || ''
}

function categoryName(categoryId: number) {
  return currentDemoState().categories.find((category) => category.id === categoryId)?.name || ''
}

function makeTextBlob(name: string, content: string) {
  return new File([content], name, { type: 'text/plain;charset=utf-8' })
}

function mediaTypeFromName(name: string): QuestionAttachmentPayload['mediaType'] {
  const lower = name.toLowerCase()
  if (/\.(png|jpg|jpeg|gif|webp)$/.test(lower)) {
    return 'IMAGE'
  }
  if (/\.(mp3|wav|ogg)$/.test(lower)) {
    return 'AUDIO'
  }
  if (/\.(mp4|webm)$/.test(lower)) {
    return 'VIDEO'
  }
  return 'FILE'
}

function createQuestionOption(option: { label: string; content: string; correct: boolean }, index: number): QuestionOption {
  return { id: nextId(currentDemoState()), label: option.label, content: option.content, correct: option.correct, sortOrder: (index + 1) * 10 }
}

function createAttachment(payload: QuestionAttachmentPayload, index: number): QuestionAttachment {
  return { id: nextId(currentDemoState()), ...payload, sortOrder: (index + 1) * 10 }
}

function toExamFromPayload(id: number, payload: ExamPayload, status: Exam['status']): Exam {
  const state = currentDemoState()
  const paperQuestions = payload.examMode === 'ANSWER_SHEET'
    ? payload.answerCardItems
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((item) => ({
          questionId: -item.questionNo,
          bankId: 0,
          bankName: '答题卡',
          type: item.answerType,
          stem: `第 ${item.questionNo} 题`,
          score: item.score,
          sortOrder: item.sortOrder,
        }))
    : payload.paperQuestions
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((paperQuestion) => {
      const source = state.questions.find((question) => question.id === paperQuestion.questionId)
      if (!source) {
        throw new Error('试题不存在')
      }
      return {
        questionId: source.id,
        bankId: source.bankId,
        bankName: source.bankName,
        type: source.type,
        stem: source.stem,
        score: paperQuestion.score,
        sortOrder: paperQuestion.sortOrder,
      }
    })
  const rules = payload.rules.map((rule, index) => ({ ...rule, id: nextId(state), bankName: bankName(rule.bankId), sortOrder: (index + 1) * 10 }))
  return {
    id,
    title: payload.title,
    description: payload.description,
    qualifyScore: payload.qualifyScore,
    startTime: payload.startTime,
    endTime: payload.endTime,
    durationMinutes: payload.durationMinutes,
    timeLimit: payload.timeLimit,
    attemptLimit: payload.attemptLimit,
    examMode: payload.examMode,
    displayMode: payload.displayMode,
    questionOrderMode: payload.questionOrderMode,
    openType: payload.openType,
    departmentIds: [...payload.departmentIds],
    rules,
    paperQuestions,
    materialGroups: payload.materialGroups.map((group) => ({
      id: nextId(state),
      title: group.title,
      description: group.description,
      sortOrder: group.sortOrder,
      files: group.files.map((file) => ({ id: nextId(state), ...file })),
    })),
    answerCardItems: payload.answerCardItems.map((item) => ({ id: nextId(state), ...item })),
    status,
    totalScore: paperQuestions.reduce((sum, question) => sum + question.score, 0),
    questionCount: paperQuestions.length,
  }
}

function updateAttemptAnswers(examId: number, answers: Array<{ questionId: number; selectedLabels?: string[]; answerText?: string }>) {
  const state = currentDemoState()
  const attempt = state.attempts.find((item) => item.examId === examId && item.userId === currentUser().id && item.status === 'IN_PROGRESS')
  if (!attempt) {
    throw new Error('作答不存在')
  }
  const answerMap = new Map(answers.map((answer) => [answer.questionId, answer]))
  attempt.questions = attempt.questions.map((question) => {
    const answer = answerMap.get(question.questionId)
    return answer ? { ...question, selectedLabels: answer.selectedLabels || [], answerText: answer.answerText || null } : question
  })
  return attempt
}

function toSession(attempt: { id: number; examId: number; startedAt: string; status: 'IN_PROGRESS' | 'SUBMITTED'; questions: ExamQuestion[] }) {
  const exam = currentDemoState().exams.find((item) => item.id === attempt.examId)
  if (!exam) {
    throw new Error('考试不存在')
  }
  return {
    examId: exam.id,
    attemptId: attempt.id,
    title: exam.title,
    durationMinutes: exam.durationMinutes,
    examMode: exam.examMode,
    displayMode: exam.displayMode,
    startedAt: attempt.startedAt,
    attemptStatus: attempt.status,
    materialGroups: clone(exam.materialGroups),
    questions: clone(attempt.questions),
  }
}

function toResultSummary(result: ExamResultDetail) {
  return clone({
    id: result.id,
    attemptId: result.attemptId,
    examId: result.examId,
    examTitle: result.examTitle,
    userId: result.userId,
    username: result.username,
    userName: result.userName,
    departmentName: result.departmentName,
    totalScore: result.totalScore,
    obtainedScore: result.obtainedScore,
    objectiveScore: result.objectiveScore,
    subjectiveScore: result.subjectiveScore,
    correctCount: result.correctCount,
    questionCount: result.questionCount,
    gradingStatus: result.gradingStatus,
    passed: result.passed,
    submittedAt: result.submittedAt,
  })
}

function questionFromPayload(id: number, payload: Parameters<ExamBusinessAdapter['createQuestion']>[0]): Question {
  return {
    id,
    bankId: payload.bankId,
    bankName: bankName(payload.bankId),
    type: payload.type,
    stem: payload.stem,
    analysis: payload.analysis,
    difficulty: payload.difficulty,
    status: payload.status,
    options: payload.options.map(createQuestionOption),
    attachments: payload.attachments.map(createAttachment),
  }
}

function seedAnswerCardCorrectLabels(state: ReturnType<typeof currentDemoState>, payload: ExamPayload) {
  if (payload.examMode !== 'ANSWER_SHEET') {
    return
  }
  for (const item of payload.answerCardItems) {
    state.correctLabelsByQuestionId[-item.questionNo] = [...item.correctLabels]
  }
}

export const demoExamBusinessAdapter: ExamBusinessAdapter = {
  async fetchQuestionCategories() {
    return clone(currentDemoState().categories.map(({ id, name, description, sortOrder }) => ({ id, name, description, sortOrder })))
  },
  async createQuestionCategory(payload) {
    const state = currentDemoState()
    const category = { id: nextId(state), code: `category-${state.nextId}`, ...payload }
    state.categories.push(category)
    return clone({ id: category.id, name: category.name, description: category.description, sortOrder: category.sortOrder })
  },
  async updateQuestionCategory(id, payload) {
    const category = currentDemoState().categories.find((item) => item.id === id)
    if (!category) {
      throw new Error('分类不存在')
    }
    Object.assign(category, payload)
    for (const bank of currentDemoState().banks.filter((item) => item.categoryId === id)) {
      bank.categoryName = payload.name
    }
    return clone({ id: category.id, name: category.name, description: category.description, sortOrder: category.sortOrder })
  },
  async deleteQuestionCategory(id) {
    if (currentDemoState().banks.some((bank) => bank.categoryId === id)) {
      throw new Error('分类下存在题库，不能删除')
    }
    currentDemoState().categories = currentDemoState().categories.filter((category) => category.id !== id)
  },
  async fetchQuestionBanks(params) {
    const keyword = params.keyword?.trim().toLowerCase()
    const records = currentDemoState().banks.filter((bank) => !keyword || bank.name.toLowerCase().includes(keyword) || bank.categoryName.toLowerCase().includes(keyword))
    return paginate(records, params.page, params.size)
  },
  async createQuestionBank(payload) {
    const state = currentDemoState()
    const bank: QuestionBank = {
      id: nextId(state),
      categoryId: payload.categoryId,
      categoryName: categoryName(payload.categoryId),
      name: payload.name,
      description: payload.description,
      status: payload.status,
      questionCount: 0,
      singleChoiceCount: 0,
      multipleChoiceCount: 0,
      writingCount: 0,
    }
    state.banks.push(bank)
    return clone(bank)
  },
  async updateQuestionBank(id, payload) {
    const bank = currentDemoState().banks.find((item) => item.id === id)
    if (!bank) {
      throw new Error('题库不存在')
    }
    Object.assign(bank, { ...payload, categoryName: categoryName(payload.categoryId) })
    return clone(bank)
  },
  async fetchQuestions(params) {
    const keyword = params.keyword?.trim().toLowerCase()
    const records = currentDemoState().questions.filter((question) => {
      const bankMatched = !params.bankId || question.bankId === params.bankId
      const keywordMatched = !keyword || question.stem.toLowerCase().includes(keyword) || question.bankName.toLowerCase().includes(keyword)
      return bankMatched && keywordMatched
    })
    return paginate(records, params.page, params.size)
  },
  async fetchQuestionDetail(id) {
    const question = currentDemoState().questions.find((item) => item.id === id)
    if (!question) {
      throw new Error('试题不存在')
    }
    return clone(question)
  },
  async createQuestion(payload) {
    const state = currentDemoState()
    const question = questionFromPayload(nextId(state), payload)
    state.questions.push(question)
    state.correctLabelsByQuestionId[question.id] = payload.options.filter((option) => option.correct).map((option) => option.label)
    refreshBankCounts(state)
    return clone(question)
  },
  async updateQuestion(id, payload) {
    const state = currentDemoState()
    const index = state.questions.findIndex((question) => question.id === id)
    if (index < 0) {
      throw new Error('试题不存在')
    }
    const updated = questionFromPayload(id, payload)
    state.questions[index] = updated
    state.correctLabelsByQuestionId[id] = payload.options.filter((option) => option.correct).map((option) => option.label)
    refreshBankCounts(state)
    return clone(updated)
  },
  async downloadQuestionImportTemplate() {
    return makeTextBlob('试题导入模板.txt', '题库,题型,题干,选项,答案,解析')
  },
  async importQuestions() {
    return { successCount: 0, failureCount: 0, errors: ['演示环境不解析 Excel 文件，请使用网页新建试题体验。'] }
  },
  async downloadQuestionExport() {
    return makeTextBlob('试题导出.txt', currentDemoState().questions.map((question) => `${question.bankName},${question.stem}`).join('\n'))
  },
  async uploadFile(file) {
    return { fileName: file.name, fileUrl: URL.createObjectURL(file), mediaType: mediaTypeFromName(file.name) }
  },
  async fetchAdminExams(params) {
    const keyword = params.keyword?.trim().toLowerCase()
    const records = currentDemoState().exams.filter((exam) => !keyword || exam.title.toLowerCase().includes(keyword))
    return paginate(records, params.page, params.size)
  },
  async fetchAdminExamDetail(id) {
    const exam = currentDemoState().exams.find((item) => item.id === id)
    if (!exam) {
      throw new Error('考试不存在')
    }
    return clone(exam)
  },
  async createExam(payload) {
    const state = currentDemoState()
    const exam = toExamFromPayload(nextId(state), payload, 'DRAFT')
    seedAnswerCardCorrectLabels(state, payload)
    state.exams.push(exam)
    return clone(exam)
  },
  async updateExam(id, payload) {
    const state = currentDemoState()
    const index = state.exams.findIndex((exam) => exam.id === id)
    if (index < 0) {
      throw new Error('考试不存在')
    }
    const status = state.exams[index].status === 'CLOSED' ? 'CLOSED' : 'DRAFT'
    const exam = toExamFromPayload(id, payload, status)
    seedAnswerCardCorrectLabels(state, payload)
    state.exams[index] = exam
    return clone(exam)
  },
  async publishExam(id) {
    const exam = currentDemoState().exams.find((item) => item.id === id)
    if (!exam) {
      throw new Error('考试不存在')
    }
    exam.status = 'PUBLISHED'
    return clone(exam)
  },
  async copyExam(id) {
    const state = currentDemoState()
    const source = state.exams.find((exam) => exam.id === id)
    if (!source) {
      throw new Error('考试不存在')
    }
    const copied = { ...clone(source), id: nextId(state), title: `${source.title} 副本`, status: 'DRAFT' as const }
    state.exams.push(copied)
    return clone(copied)
  },
  async downloadExamPaper(id) {
    const exam = currentDemoState().exams.find((item) => item.id === id)
    return makeTextBlob(`${exam?.title || '试卷'}.txt`, exam?.paperQuestions.map((question) => question.stem).join('\n') || '')
  },
  async revokeExam(id) {
    const exam = currentDemoState().exams.find((item) => item.id === id)
    if (!exam) {
      throw new Error('考试不存在')
    }
    exam.status = 'DRAFT'
    return clone(exam)
  },
  async closeExam(id) {
    const exam = currentDemoState().exams.find((item) => item.id === id)
    if (!exam) {
      throw new Error('考试不存在')
    }
    exam.status = 'CLOSED'
    return clone(exam)
  },
  async deleteExam(id) {
    currentDemoState().exams = currentDemoState().exams.filter((exam) => exam.id !== id)
  },
  async fetchAdminResults(params) {
    const records = currentDemoState().results.filter((result) => !params?.examId || result.examId === params.examId)
    return records.map(toResultSummary)
  },
  async fetchAdminResultDetail(resultId) {
    const result = currentDemoState().results.find((item) => item.id === resultId)
    if (!result) {
      throw new Error('成绩不存在')
    }
    return clone(result)
  },
  async fetchExamTasks() {
    return clone(currentDemoState().exams.filter((exam) => exam.status === 'PUBLISHED'))
  },
  async startExam(examId) {
    const state = currentDemoState()
    const user = currentUser()
    const existing = state.attempts.find((attempt) => attempt.examId === examId && attempt.userId === user.id && attempt.status === 'IN_PROGRESS')
    if (existing) {
      return toSession(existing)
    }
    const exam = state.exams.find((item) => item.id === examId)
    if (!exam) {
      throw new Error('考试不存在')
    }
    const attempt = { id: nextId(state), examId, userId: user.id, status: 'IN_PROGRESS' as const, startedAt: nowIso(), questions: buildSessionQuestions(state, exam) }
    state.attempts.push(attempt)
    return toSession(attempt)
  },
  async saveExamAnswers(examId, answers) {
    return toSession(updateAttemptAnswers(examId, answers))
  },
  async submitExam(examId, answers) {
    const state = currentDemoState()
    const user = currentUser()
    const exam = state.exams.find((item) => item.id === examId)
    if (!exam) {
      throw new Error('考试不存在')
    }
    const attempt = updateAttemptAnswers(examId, answers)
    attempt.status = 'SUBMITTED'
    const result = gradeSubmission(state, exam, user, attempt.questions, answers)
    state.results.push(result)
    return toResultSummary(result)
  },
  async reviewWritingQuestion(resultId, questionId, payload) {
    const result = currentDemoState().results.find((item) => item.id === resultId)
    if (!result) {
      throw new Error('成绩不存在')
    }
    const question = result.questions.find((item) => item.questionId === questionId)
    if (!question || !isManualReviewType(question.type)) {
      throw new Error('主观题不存在')
    }
    question.obtainedScore = Math.min(payload.score, question.score)
    question.reviewComment = payload.comment
    question.reviewerName = currentUser().displayName
    question.reviewedAt = nowIso()
    updateResultTotals(result, currentDemoState().exams.find((exam) => exam.id === result.examId)?.qualifyScore || 0)
    return clone(result)
  },
  async completeResultReview(resultId) {
    const result = currentDemoState().results.find((item) => item.id === resultId)
    if (!result) {
      throw new Error('成绩不存在')
    }
    if (result.questions.some((question) => isManualReviewType(question.type) && !question.reviewedAt)) {
      throw new Error('仍有主观题未评分')
    }
    result.gradingStatus = 'FINAL'
    updateResultTotals(result, currentDemoState().exams.find((exam) => exam.id === result.examId)?.qualifyScore || 0)
    return clone(result)
  },
  async fetchMyExamResults() {
    const user = currentUser()
    return currentDemoState().results.filter((result) => result.userId === user.id).map(toResultSummary)
  },
  async fetchMyExamResultDetail(resultId) {
    const user = currentUser()
    const result = currentDemoState().results.find((item) => item.id === resultId && item.userId === user.id)
    if (!result) {
      throw new Error('成绩不存在')
    }
    return clone(result)
  },
}

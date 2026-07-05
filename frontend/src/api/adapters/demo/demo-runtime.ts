import type {
  Exam,
  ExamQuestion,
  ExamResultDetail,
  ExamResultQuestion,
} from '../../exam-business-types'
import { isManualReviewType } from '@/utils/question-types'
import { clone, nextId, nowIso, type DemoState, type DemoUser } from './demo-model'

export function buildSessionQuestions(state: DemoState, exam: Exam): ExamQuestion[] {
  return exam.paperQuestions
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((paperQuestion) => {
      const source = state.questions.find((question) => question.id === paperQuestion.questionId)
      const answerCardItem = exam.answerCardItems.find((item) => item.questionNo === Math.abs(paperQuestion.questionId))
      return {
        questionId: paperQuestion.questionId,
        type: paperQuestion.type,
        stem: paperQuestion.stem,
        score: paperQuestion.score,
        sortOrder: paperQuestion.sortOrder,
        selectedLabels: [],
        answerText: null,
        attachments: source?.attachments ? clone(source.attachments) : [],
        options: source?.options.map(({ id, label, content, sortOrder }) => ({ id, label, content, sortOrder }))
          || answerCardItem?.optionLabels.map((label, index) => ({ id: paperQuestion.questionId * 100 - index, label, content: label, sortOrder: (index + 1) * 10 }))
          || [],
      }
    })
}

export function gradeSubmission(
  state: DemoState,
  exam: Exam,
  user: DemoUser,
  questions: ExamQuestion[],
  answers: Array<{ questionId: number; selectedLabels?: string[]; answerText?: string | null }>,
): ExamResultDetail {
  const answerMap = new Map(answers.map((answer) => [answer.questionId, answer]))
  let objectiveScore = 0
  let correctCount = 0
  const resultQuestions: ExamResultQuestion[] = questions.map((question) => {
    const answer = answerMap.get(question.questionId)
    const selectedLabels = answer?.selectedLabels || []
    const answerText = answer?.answerText || null
    const correctLabels = state.correctLabelsByQuestionId[question.questionId] || []
    const correct = isManualReviewType(question.type) ? null : sameLabels(selectedLabels, correctLabels)
    const obtainedScore = correct ? question.score : 0
    if (correct) {
      objectiveScore += obtainedScore
      correctCount += 1
    }
    return {
      ...question,
      analysis: state.questions.find((item) => item.id === question.questionId)?.analysis || null,
      obtainedScore,
      selectedLabels,
      answerText,
      correctLabels,
      correct,
      reviewComment: null,
      reviewerName: null,
      reviewedAt: null,
    }
  })
  const hasManualReview = resultQuestions.some((question) => isManualReviewType(question.type))
  return {
    id: nextId(state),
    attemptId: nextId(state),
    examId: exam.id,
    examTitle: exam.title,
    userId: user.id,
    username: user.username,
    userName: user.displayName,
    departmentName: user.departmentName,
    totalScore: exam.totalScore,
    obtainedScore: objectiveScore,
    objectiveScore,
    subjectiveScore: 0,
    correctCount,
    questionCount: questions.length,
    gradingStatus: hasManualReview ? 'PENDING_REVIEW' : 'FINAL',
    passed: !hasManualReview && objectiveScore >= exam.qualifyScore,
    submittedAt: nowIso(),
    questions: resultQuestions,
  }
}

export function updateResultTotals(result: ExamResultDetail, qualifyScore: number) {
  const objectiveScore = result.questions.filter((question) => !isManualReviewType(question.type)).reduce((sum, question) => sum + question.obtainedScore, 0)
  const subjectiveScore = result.questions.filter((question) => isManualReviewType(question.type)).reduce((sum, question) => sum + question.obtainedScore, 0)
  result.objectiveScore = objectiveScore
  result.subjectiveScore = subjectiveScore
  result.obtainedScore = objectiveScore + subjectiveScore
  result.correctCount = result.questions.filter((question) => question.correct).length
  result.passed = result.gradingStatus === 'FINAL' && result.obtainedScore >= qualifyScore
}

function sameLabels(left: string[], right: string[]) {
  if (left.length !== right.length) {
    return false
  }
  const leftSorted = [...left].sort()
  const rightSorted = [...right].sort()
  return leftSorted.every((label, index) => label === rightSorted[index])
}

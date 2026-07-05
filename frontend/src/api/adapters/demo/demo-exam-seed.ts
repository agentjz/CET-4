import type {
  ExamMaterialGroup,
  Question,
  QuestionAttachment,
  QuestionOption,
} from '../../exam-business-types'
import { isManualReviewType } from '@/utils/question-types'
import { nextId, refreshBankCounts, type DemoState } from './demo-model'
import { buildSessionQuestions, gradeSubmission } from './demo-runtime'

export function seedExamBusiness(state: DemoState) {
  buildQuestionBank(state)
  buildExam(state)
  seedSubmittedResult(state)
}

function buildQuestionBank(state: DemoState) {
  const bankId = nextId(state)
  state.banks.push({
    id: bankId,
    categoryId: state.categories[0].id,
    categoryName: state.categories[0].name,
    name: '四级样例题库',
    description: '用于演示单选、多选和写作题维护流程',
    status: 'ACTIVE',
    questionCount: 0,
    singleChoiceCount: 0,
    multipleChoiceCount: 0,
    writingCount: 0,
  })

  const questions: Question[] = [
    choiceQuestion(state, bankId, 'SINGLE_CHOICE', 'The lecture mainly discusses the importance of regular practice.', ['True', 'False', 'Not given', 'Unknown'], ['A'], '听力材料中的核心观点是持续练习。'),
    choiceQuestion(state, bankId, 'SINGLE_CHOICE', 'Which word is closest in meaning to "essential"?', ['necessary', 'optional', 'ordinary', 'temporary'], ['A'], 'essential 表示必要的。'),
    choiceQuestion(state, bankId, 'MULTIPLE_CHOICE', 'Which of the following are effective study habits?', ['Reviewing notes', 'Planning study time', 'Ignoring feedback', 'Practicing with past papers'], ['A', 'B', 'D'], '有效学习习惯包括复习、规划和练习。'),
    writingQuestion(state, bankId, 'For this part, you are allowed 30 minutes to write a short essay on online learning.'),
  ]
  state.questions.push(...questions)
  for (const question of questions) {
    state.correctLabelsByQuestionId[question.id] = question.options.filter((option) => option.correct).map((option) => option.label)
  }
  refreshBankCounts(state)
}

function choiceQuestion(
  state: DemoState,
  bankId: number,
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE',
  stem: string,
  labels: string[],
  correctLabels: string[],
  analysis: string,
): Question {
  return {
    id: nextId(state),
    bankId,
    bankName: '四级样例题库',
    type,
    stem,
    analysis,
    difficulty: 'EASY',
    status: 'ACTIVE',
    options: labels.map((content, index) => option(state, index, content, correctLabels.includes(String.fromCharCode(65 + index)))),
    attachments: type === 'SINGLE_CHOICE' ? [listeningAttachment(state)] : [],
  }
}

function writingQuestion(state: DemoState, bankId: number, stem: string): Question {
  return {
    id: nextId(state),
    bankId,
    bankName: '四级样例题库',
    type: 'WRITING',
    stem,
    analysis: '写作题需要人工阅卷。',
    difficulty: 'HARD',
    status: 'ACTIVE',
    options: [],
    attachments: [],
  }
}

function option(state: DemoState, index: number, content: string, correct: boolean): QuestionOption {
  return { id: nextId(state), label: String.fromCharCode(65 + index), content, correct, sortOrder: (index + 1) * 10 }
}

function listeningAttachment(state: DemoState): QuestionAttachment {
  return {
    id: nextId(state),
    fileName: '2023-03-cet4-listening.mp3',
    fileUrl: '/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3',
    mediaType: 'AUDIO',
    sortOrder: 10,
  }
}

function buildExam(state: DemoState) {
  const questions = state.questions
  const paperQuestions = questions.map((question, index) => ({
    questionId: question.id,
    bankId: question.bankId,
    bankName: question.bankName,
    type: question.type,
    stem: question.stem,
    score: question.type === 'WRITING' ? 30 : 5,
    sortOrder: (index + 1) * 10,
  }))
  state.exams.push({
    id: nextId(state),
    title: 'CET-4 四级考试平台演示',
    description: '以四级真题场景作为样例，演示题库、考试、作答、评分和阅卷流程。',
    qualifyScore: 36,
    startTime: '2025-01-01T00:00:00',
    endTime: '2099-12-31T23:59:59',
    durationMinutes: 45,
    timeLimit: true,
    attemptLimit: null,
    examMode: 'STRUCTURED',
    displayMode: 'ALL',
    questionOrderMode: 'FIXED',
    openType: 'PUBLIC',
    departmentIds: [],
    rules: [],
    paperQuestions,
    materialGroups: [
      {
        id: nextId(state),
        title: '听力材料',
        description: '四级听力演示材料',
        sortOrder: 10,
        files: [
          {
            id: nextId(state),
            sourceType: 'LOCAL_ASSET',
            displayName: '四级听力音频',
            description: '',
            fileName: '2023-03-cet4-listening.mp3',
            fileUrl: '/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3',
            mediaType: 'AUDIO',
            sortOrder: 10,
          },
        ],
      },
    ],
    answerCardItems: [],
    status: 'PUBLISHED',
    questionCount: paperQuestions.length,
    totalScore: paperQuestions.reduce((sum, question) => sum + question.score, 0),
  })

  const answerSheetItems = [
    { id: nextId(state), questionNo: 1, answerType: 'SINGLE_CHOICE' as const, optionLabels: ['A', 'B', 'C', 'D'], correctLabels: ['A'], score: 5, sortOrder: 10 },
    { id: nextId(state), questionNo: 2, answerType: 'MULTIPLE_CHOICE' as const, optionLabels: ['A', 'B', 'C', 'D'], correctLabels: ['A', 'C'], score: 5, sortOrder: 20 },
    { id: nextId(state), questionNo: 3, answerType: 'WRITING' as const, optionLabels: [], correctLabels: [], score: 10, sortOrder: 30 },
  ]
  const answerSheetQuestions = answerSheetItems.map((item) => ({
    questionId: -item.questionNo,
    bankId: 0,
    bankName: '答题卡',
    type: item.answerType,
    stem: `第 ${item.questionNo} 题`,
    score: item.score,
    sortOrder: item.sortOrder,
  }))
  for (const item of answerSheetItems) {
    state.correctLabelsByQuestionId[-item.questionNo] = [...item.correctLabels]
  }
  state.exams.push({
    id: nextId(state),
    title: '答题卡试卷演示',
    description: '试卷材料和答题卡分离的演示考试。',
    qualifyScore: 10,
    startTime: '2025-01-01T00:00:00',
    endTime: '2099-12-31T23:59:59',
    durationMinutes: 45,
    timeLimit: true,
    attemptLimit: null,
    examMode: 'ANSWER_SHEET',
    displayMode: 'ALL',
    questionOrderMode: 'FIXED',
    openType: 'PUBLIC',
    departmentIds: [],
    rules: [],
    paperQuestions: answerSheetQuestions,
    materialGroups: answerSheetMaterialGroups(state),
    answerCardItems: answerSheetItems,
    status: 'PUBLISHED',
    questionCount: answerSheetQuestions.length,
    totalScore: answerSheetQuestions.reduce((sum, question) => sum + question.score, 0),
  })
}

function answerSheetMaterialGroups(state: DemoState): ExamMaterialGroup[] {
  return [
    {
      id: nextId(state),
      title: '听力材料',
      description: '播放音频后填写答题卡。',
      sortOrder: 10,
      files: [
        {
          id: nextId(state),
          sourceType: 'LOCAL_ASSET',
          displayName: '四级听力音频',
          description: '',
          fileName: '2023-03-cet4-listening.mp3',
          fileUrl: '/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3',
          mediaType: 'AUDIO',
          sortOrder: 10,
        },
      ],
    },
    {
      id: nextId(state),
      title: '阅读材料',
      description: '演示外部文件材料。',
      sortOrder: 20,
      files: [
        {
          id: nextId(state),
          sourceType: 'EXTERNAL_LINK',
          displayName: '阅读材料链接',
          description: '',
          fileName: 'reading-material.html',
          fileUrl: 'https://example.com/reading-material.html',
          mediaType: 'FILE',
          sortOrder: 10,
        },
      ],
    },
  ]
}

function seedSubmittedResult(state: DemoState) {
  const exam = state.exams[0]
  const user = state.users.find((item) => item.username === 'zhangsan')
  if (!exam || !user) {
    return
  }
  const sessionQuestions = buildSessionQuestions(state, exam)
  const answers = sessionQuestions.map((question) => ({
    questionId: question.questionId,
    selectedLabels: isManualReviewType(question.type) ? [] : state.correctLabelsByQuestionId[question.questionId] || [],
    answerText: isManualReviewType(question.type) ? 'Online learning gives students more flexible access to resources and requires stronger self-discipline.' : null,
  }))
  const result = gradeSubmission(state, exam, user, sessionQuestions, answers)
  state.results.push({ ...result, gradingStatus: 'PENDING_REVIEW' })
}

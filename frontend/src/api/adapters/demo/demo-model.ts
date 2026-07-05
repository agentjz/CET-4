import type {
  AdminMenu,
  AdminPermission,
  AdminRole,
  AdminUser,
  Department,
} from '../../admin'
import type {
  Exam,
  ExamQuestion,
  ExamResultDetail,
  Question,
  QuestionBank,
} from '../../exam-business-types'
import type { CurrentUser } from '../../types'
import { isManualReviewType } from '@/utils/question-types'

export interface DemoUser extends AdminUser {
  password: string
  roleIds: number[]
}

export interface DemoAttempt {
  id: number
  examId: number
  userId: number
  status: 'IN_PROGRESS' | 'SUBMITTED'
  startedAt: string
  questions: ExamQuestion[]
}

export interface DemoState {
  currentUserId: number | null
  departments: Department[]
  users: DemoUser[]
  roles: AdminRole[]
  permissions: AdminPermission[]
  menus: AdminMenu[]
  categories: Array<{ id: number; code: string; name: string; description: string | null; sortOrder: number }>
  banks: QuestionBank[]
  questions: Question[]
  correctLabelsByQuestionId: Record<number, string[]>
  exams: Exam[]
  attempts: DemoAttempt[]
  results: ExamResultDetail[]
  nextId: number
}

export function clone<T>(value: T): T {
  return structuredClone(value)
}

export function nowIso() {
  return new Date().toISOString()
}

export function nextId(state: Pick<DemoState, 'nextId'>) {
  state.nextId += 1
  return state.nextId
}

export function buildCurrentUser(state: DemoState, user: DemoUser): CurrentUser {
  const permissions = state.roles
    .filter((role) => user.roleIds.includes(role.id))
    .flatMap((role) => role.permissions.map((permission) => permission.code))
  return {
    id: user.id,
    username: user.username,
    displayName: user.displayName,
    mustChangePassword: false,
    roles: user.roles,
    permissions: Array.from(new Set(permissions)),
  }
}

export function refreshBankCounts(state: DemoState) {
  for (const bank of state.banks) {
    const questions = state.questions.filter((question) => question.bankId === bank.id)
    bank.questionCount = questions.length
    bank.singleChoiceCount = questions.filter((question) => question.type === 'SINGLE_CHOICE').length
    bank.multipleChoiceCount = questions.filter((question) => question.type === 'MULTIPLE_CHOICE').length
    bank.writingCount = questions.filter((question) => isManualReviewType(question.type)).length
  }
}

import { seedState } from './demo-seed'

export {
  buildCurrentUser,
  clone,
  nextId,
  nowIso,
  refreshBankCounts,
  type DemoAttempt,
  type DemoState,
  type DemoUser,
} from './demo-model'
export {
  buildSessionQuestions,
  gradeSubmission,
  updateResultTotals,
} from './demo-runtime'

const demoState = seedState()

export function currentDemoState() {
  return demoState
}

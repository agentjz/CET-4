<template>
  <section class="exam-session" v-loading="loading">
    <template v-if="session">
      <header class="exam-status">
        <div>
          <h1>{{ session.title }}</h1>
          <p>{{ answeredCount }} / {{ session.questions.length }} 已作答 · {{ saveStatusText }}</p>
        </div>
        <div class="exam-actions">
          <span class="countdown" :class="{ 'countdown--danger': remainingSeconds <= 300 }">{{ remainingText }}</span>
          <el-button type="primary" :loading="submitting" :disabled="submitted" @click="confirmSubmit">提交试卷</el-button>
        </div>
      </header>

      <section class="exam-workspace">
        <main class="question-stack">
          <template v-if="session.examMode === 'ANSWER_SHEET'">
            <ExamMaterialViewer :material-groups="session.materialGroups" />

            <ExamAnswerSheetPanel
              :questions="session.questions"
              :single-answers="singleAnswers"
              :multiple-answers="multipleAnswers"
              :text-answers="textAnswers"
              :disabled="submitting || submitted"
              @schedule-save="scheduleSave"
              @save-immediately="saveImmediately"
            />

            <div class="question-submit-row">
              <el-button type="primary" :loading="submitting" :disabled="submitted" @click="confirmSubmit">提交试卷</el-button>
            </div>
          </template>

          <template v-else-if="session.displayMode === 'PAGED' && currentQuestion">
            <ExamQuestionPanel
              :key="currentQuestion.questionId"
              :question="currentQuestion"
              :index="currentIndex"
              :single-answers="singleAnswers"
              :multiple-answers="multipleAnswers"
              :text-answers="textAnswers"
              :disabled="submitting || submitted"
              @schedule-save="scheduleSave"
              @save-immediately="saveImmediately"
            >
              <template #footer>
                <div class="question-nav">
                  <el-button :disabled="currentIndex === 0" @click="previousQuestion">上一题</el-button>
                  <el-button
                    v-if="currentIndex < session.questions.length - 1"
                    type="primary"
                    @click="nextQuestion"
                  >
                    下一题
                  </el-button>
                  <el-button v-else type="primary" :loading="submitting" :disabled="submitted" @click="confirmSubmit">
                    提交试卷
                  </el-button>
                </div>
              </template>
            </ExamQuestionPanel>
          </template>

          <template v-else-if="session.displayMode === 'ALL'">
            <section v-for="group in displayQuestionGroups" :key="group.id" class="question-display-group">
              <h2>{{ group.title }}</h2>
              <ExamQuestionPanel
                v-for="question in group.questions"
                :key="question.questionId"
                :panel-id="`question-${question.questionId}`"
                :question="question"
                :index="questionGlobalIndex(question.questionId)"
                :single-answers="singleAnswers"
                :multiple-answers="multipleAnswers"
                :text-answers="textAnswers"
                :disabled="submitting || submitted"
                @schedule-save="scheduleSave"
                @save-immediately="saveImmediately"
              />
            </section>
            <div class="question-submit-row">
              <el-button type="primary" :loading="submitting" :disabled="submitted" @click="confirmSubmit">提交试卷</el-button>
            </div>
          </template>
        </main>

        <ExamAnswerCard
          :groups="groupedQuestions"
          :questions="session.questions"
          :unanswered-count="unansweredCount"
          :current-question-id="currentQuestion?.questionId"
          :is-answered="isAnswered"
          @select-question="selectQuestion"
        />
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

import ExamAnswerCard from '@/components/exam/session/ExamAnswerCard.vue'
import ExamAnswerSheetPanel from '@/components/exam/session/ExamAnswerSheetPanel.vue'
import ExamMaterialViewer from '@/components/exam/session/ExamMaterialViewer.vue'
import ExamQuestionPanel from '@/components/exam/session/ExamQuestionPanel.vue'
import { useAnswerSnapshotSaver } from '@/composables/use-answer-snapshot-saver'
import { saveExamAnswers, startExam, submitExam, type ExamQuestion, type ExamSession } from '@/api/exam-business'
import {
  buildAnswerCardGroups,
  buildSubmitAnswers,
  countAnsweredQuestions,
  formatRemainingTime,
  groupQuestionsByType,
  isQuestionAnswered,
  questionIndexById,
  type MultipleAnswerMap,
  type SingleAnswerMap,
  type TextAnswerMap,
} from '@/utils/exam-session'
import { isManualReviewType, isMultipleAnswerType } from '@/utils/question-types'

const route = useRoute()
const router = useRouter()
const session = ref<ExamSession | null>(null)
const loading = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const remainingSeconds = ref(0)
const currentIndex = ref(0)
let countdownTimer: number | undefined

const multipleAnswers = reactive<MultipleAnswerMap>({})
const singleAnswers = reactive<SingleAnswerMap>({})
const textAnswers = reactive<TextAnswerMap>({})
const answerSaver = useAnswerSnapshotSaver({
  saveSnapshot: async () => {
    if (!session.value) {
      return
    }
    await saveExamAnswers(session.value.examId, buildSubmitAnswers(session.value.questions, singleAnswers, multipleAnswers, textAnswers))
  },
  canSave: () => Boolean(session.value && !submitted.value),
  onAutoSaveError: () => {
    ElMessage.error('答案保存失败，请检查网络后重试')
  },
})

const remainingText = computed(() => formatRemainingTime(remainingSeconds.value))
const currentQuestion = computed(() => session.value?.questions[currentIndex.value] ?? null)
const answeredCount = computed(() => countAnsweredQuestions(session.value?.questions ?? [], singleAnswers, multipleAnswers, textAnswers))
const unansweredCount = computed(() => Math.max(0, (session.value?.questions.length ?? 0) - answeredCount.value))
const hasActiveAttempt = computed(() => Boolean(session.value && !submitted.value))
const saveStatusText = answerSaver.saveStatusText
const displayQuestionGroups = computed(() => groupQuestionsByType(session.value?.questions ?? []))
const groupedQuestions = computed(() => buildAnswerCardGroups(session.value?.examMode ?? 'STRUCTURED', session.value?.questions ?? []))

onMounted(() => {
  window.addEventListener('beforeunload', preventUnload)
  void beginExam()
})

onBeforeUnmount(() => {
  stopCountdown()
  answerSaver.clearScheduledSave()
  window.removeEventListener('beforeunload', preventUnload)
})

onBeforeRouteLeave(async () => {
  if (!hasActiveAttempt.value) {
    return true
  }
  if (answerSaver.hasPendingSave.value) {
    try {
      await flushAnswerSaves()
    } catch {
      try {
        await ElMessageBox.confirm('仍有答案保存失败，离开可能丢失最近修改。', '离开考试', {
          confirmButtonText: '离开',
          cancelButtonText: '继续作答',
          type: 'warning',
        })
      } catch {
        return false
      }
    }
  }
  try {
    await ElMessageBox.confirm('离开后本次作答仍在进行，未提交答案不会被锁定。', '离开考试', {
      confirmButtonText: '离开',
      cancelButtonText: '继续作答',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
})

async function beginExam() {
  loading.value = true
  try {
    const examId = Number(route.params.examId)
    if (!Number.isFinite(examId)) {
      ElMessage.error('考试不存在')
      await router.replace({ name: 'exam-home' })
      return
    }
    session.value = await startExam(examId)
    initializeAnswers(session.value)
    startCountdown(session.value)
  } finally {
    loading.value = false
  }
}

function initializeAnswers(currentSession: ExamSession) {
  for (const question of currentSession.questions) {
    if (isMultipleAnswerType(question.type)) {
      multipleAnswers[question.questionId] = [...question.selectedLabels]
    } else if (!isManualReviewType(question.type)) {
      singleAnswers[question.questionId] = question.selectedLabels[0] || ''
    } else {
      textAnswers[question.questionId] = question.answerText || ''
    }
  }
  answerSaver.lastSavedAt.value = currentSession.questions.some((question) => question.selectedLabels.length > 0 || Boolean(question.answerText?.trim()))
    ? new Date()
    : null
}

function startCountdown(currentSession: ExamSession) {
  stopCountdown()
  const startedAt = new Date(currentSession.startedAt).getTime()
  const deadline = startedAt + currentSession.durationMinutes * 60 * 1000
  const tick = () => {
    remainingSeconds.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
    if (remainingSeconds.value <= 0 && !submitted.value && !submitting.value) {
      void submit(true)
    }
  }
  tick()
  countdownTimer = window.setInterval(tick, 1000)
}

function stopCountdown() {
  if (countdownTimer) {
    window.clearInterval(countdownTimer)
    countdownTimer = undefined
  }
}

function isAnswered(question: ExamQuestion) {
  return isQuestionAnswered(question, singleAnswers, multipleAnswers, textAnswers)
}

function questionGlobalIndex(questionId: number) {
  return questionIndexById(session.value?.questions ?? [], questionId)
}

async function selectQuestion(questionId: number) {
  const index = questionGlobalIndex(questionId)
  if (index >= 0) {
    currentIndex.value = index
    if (session.value?.examMode === 'ANSWER_SHEET') {
      await nextTick()
      document.getElementById(`answer-sheet-question-${questionId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    } else if (session.value?.displayMode === 'ALL') {
      await nextTick()
      document.getElementById(`question-${questionId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }
}

function previousQuestion() {
  currentIndex.value = Math.max(0, currentIndex.value - 1)
}

function nextQuestion() {
  if (!session.value) {
    return
  }
  currentIndex.value = Math.min(session.value.questions.length - 1, currentIndex.value + 1)
}

async function confirmSubmit() {
  const message = unansweredCount.value > 0 ? `还有 ${unansweredCount.value} 题未作答，提交后答案将锁定。` : '提交后答案将锁定。'
  await ElMessageBox.confirm(message, '确认提交', {
    confirmButtonText: '提交',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await submit(false)
}

async function submit(autoSubmit: boolean) {
  if (!session.value || submitted.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    await flushAnswerSaves()
    const payload = buildSubmitAnswers(session.value.questions, singleAnswers, multipleAnswers, textAnswers)
    const result = await submitExam(session.value.examId, payload)
    submitted.value = true
    stopCountdown()
    ElMessage.success(autoSubmit ? '考试时间已到，试卷已自动提交' : '试卷已提交')
    await router.replace({ name: 'exam-result', params: { resultId: result.id } })
  } finally {
    submitting.value = false
  }
}

function scheduleSave(question: ExamQuestion) {
  answerSaver.scheduleSave(question.questionId)
}

function saveImmediately(question: ExamQuestion) {
  answerSaver.saveImmediately(question.questionId)
}

async function flushAnswerSaves() {
  if (!session.value) {
    return
  }
  await answerSaver.flushSaves(session.value.questions.map((question) => question.questionId))
}

function preventUnload(event: BeforeUnloadEvent) {
  if (!hasActiveAttempt.value || !answerSaver.hasPendingSave.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

</script>

<style scoped>
.exam-status {
  position: sticky;
  z-index: 5;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 18px 20px;
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: rgb(255 255 255 / 96%);
  box-shadow: var(--ks-shadow);
}

.exam-status h1 {
  margin: 0;
  font-size: 20px;
}

.exam-status p {
  margin: 6px 0 0;
  color: var(--ks-text-muted);
}

.exam-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, clamp(220px, 20vw, 300px));
  gap: 16px;
  align-items: start;
}

.exam-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}

.countdown {
  min-width: 84px;
  color: var(--ks-primary);
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 20px;
  font-weight: 700;
  text-align: right;
}

.countdown--danger {
  color: var(--ks-warning);
}

.question-nav {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.question-display-group {
  display: grid;
  gap: 14px;
}

.question-display-group h2 {
  margin: 8px 0 0;
  color: var(--ks-text);
  font-size: 16px;
}

.question-submit-row {
  display: flex;
  justify-content: flex-end;
  padding: 4px 0 16px;
}

@media (max-width: 900px) {
  .exam-workspace {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .exam-status,
  .exam-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>

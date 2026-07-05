import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormRules } from 'element-plus'

import { fetchQuestions, type Exam, type ExamPayload, type Question } from '@/api/exam-business'
import {
  answerCardValidationError,
  calculateAnswerCardQuestionCount,
  calculateAnswerCardTotalScore,
  hasMaterialFiles,
} from '@/utils/admin-answer-sheet-editor'
import {
  calculateTotalQuestionCount,
  calculateTotalScore,
  createDefaultRule,
  defaultManualScore as calculateDefaultManualScore,
  examRulesToForms,
  generatePaperQuestionsFromRules,
  normalizePaperSort,
  toExamPaperQuestionPayloads,
  toExamRulePayloads,
  toGeneratedPaperQuestion,
  toPaperQuestionForm,
  type ExamPaperQuestionForm,
  type ExamRuleForm,
} from '@/utils/admin-exam-editor'

export function useExamEditor() {
  const ruleset = ref<ExamRuleForm[]>([])
  const paperQuestions = ref<ExamPaperQuestionForm[]>([])
  const materialGroups = ref<ExamPayload['materialGroups']>([])
  const answerCardItems = ref<ExamPayload['answerCardItems']>([])
  const bankQuestions = ref<Record<number, Question[]>>({})
  const pickerQuestions = ref<Question[]>([])
  const paperStale = ref(false)
  const saving = ref(false)
  const publishing = ref(false)
  const closing = ref(false)
  const pickerLoading = ref(false)
  const previewVisible = ref(false)
  const editorVisible = ref(false)
  const editingExam = ref<Exam | null>(null)
  const currentStatus = ref<Exam['status']>('DRAFT')
  const formRef = ref<{ validate: () => Promise<boolean> | undefined }>()
  const timeRange = ref<[string, string]>(['2026-01-01T00:00:00', '2026-12-31T23:59:59'])
  const attemptLimitMode = ref<'UNLIMITED' | 'LIMITED'>('UNLIMITED')
  const limitedAttemptCount = ref<number | null>(1)
  const picker = reactive({ bankId: null as number | null, keyword: '' })

  const form = reactive<ExamPayload>({
    title: '',
    description: '',
    qualifyScore: 0,
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
    rules: [],
    paperQuestions: [],
    materialGroups: [],
    answerCardItems: [],
  })

  const formRules: FormRules<ExamPayload> = {
    title: [{ required: true, message: '请输入考试名称', trigger: 'blur' }],
    qualifyScore: [{ required: true, message: '请输入及格分', trigger: 'change' }],
    durationMinutes: [{ required: true, message: '请输入考试时长', trigger: 'change' }],
    displayMode: [{ required: true, message: '请选择题目显示方式', trigger: 'change' }],
    questionOrderMode: [{ required: true, message: '请选择题目顺序', trigger: 'change' }],
    openType: [{ required: true, message: '请选择开放范围', trigger: 'change' }],
  }

  const totalScore = computed(() => {
    if (form.examMode === 'ANSWER_SHEET') {
      return calculateAnswerCardTotalScore(answerCardItems.value)
    }
    return calculateTotalScore(ruleset.value, paperQuestions.value)
  })

  const totalQuestionCount = computed(() => {
    if (form.examMode === 'ANSWER_SHEET') {
      return calculateAnswerCardQuestionCount(answerCardItems.value)
    }
    return calculateTotalQuestionCount(ruleset.value, paperQuestions.value)
  })

  async function fillEditor(exam: Exam) {
    editingExam.value = exam
    currentStatus.value = exam.status
    form.title = exam.title
    form.description = exam.description || ''
    form.qualifyScore = exam.qualifyScore
    form.startTime = exam.startTime
    form.endTime = exam.endTime
    form.durationMinutes = exam.durationMinutes
    form.timeLimit = exam.timeLimit
    form.attemptLimit = exam.attemptLimit
    form.examMode = exam.examMode
    form.displayMode = exam.displayMode
    form.questionOrderMode = exam.questionOrderMode
    form.openType = exam.openType
    form.departmentIds = [...exam.departmentIds]
    attemptLimitMode.value = exam.attemptLimit ? 'LIMITED' : 'UNLIMITED'
    limitedAttemptCount.value = exam.attemptLimit || 1
    timeRange.value = [exam.startTime, exam.endTime]
    ruleset.value = examRulesToForms(exam)
    for (const rule of ruleset.value) {
      await loadBankQuestions(rule.bankId)
    }
    paperQuestions.value = exam.paperQuestions.map(toPaperQuestionForm)
    materialGroups.value = exam.materialGroups.map(({ id, files, ...group }) => ({
      ...group,
      files: files.map(({ id: _id, ...file }) => file),
    }))
    answerCardItems.value = exam.answerCardItems.map(({ id, ...item }) => item)
    paperStale.value = false
  }

  function resetForm() {
    ruleset.value = []
    form.title = ''
    form.description = ''
    form.qualifyScore = 0
    form.startTime = '2026-01-01T00:00:00'
    form.endTime = '2026-12-31T23:59:59'
    form.durationMinutes = 30
    form.timeLimit = true
    form.attemptLimit = null
    form.examMode = 'STRUCTURED'
    form.displayMode = 'PAGED'
    form.questionOrderMode = 'FIXED'
    form.openType = 'PUBLIC'
    form.departmentIds = []
    form.rules = []
    form.paperQuestions = []
    form.materialGroups = []
    form.answerCardItems = []
    paperQuestions.value = []
    materialGroups.value = []
    answerCardItems.value = []
    pickerQuestions.value = []
    paperStale.value = false
    previewVisible.value = false
    currentStatus.value = 'DRAFT'
    attemptLimitMode.value = 'UNLIMITED'
    limitedAttemptCount.value = 1
    timeRange.value = [form.startTime, form.endTime]
  }

  function addRule() {
    ruleset.value.push(createDefaultRule(Date.now() + ruleset.value.length))
    markPaperStale()
  }

  function removeRule(index: number) {
    ruleset.value.splice(index, 1)
    markPaperStale()
  }

  async function loadBankQuestions(bankId: number | null, force = false) {
    if (!bankId || (!force && bankQuestions.value[bankId])) {
      return
    }
    const result = await fetchQuestions({ page: 1, size: 500, bankId })
    bankQuestions.value = {
      ...bankQuestions.value,
      [bankId]: result.records.filter((question) => question.status === 'ACTIVE'),
    }
  }

  async function onRuleBankChange(bankId: number | null) {
    await loadBankQuestions(bankId)
    picker.bankId = bankId || picker.bankId
    markPaperStale()
  }

  function markPaperStale() {
    paperStale.value = true
  }

  async function generatePaperQuestions() {
    for (const rule of ruleset.value) {
      await loadBankQuestions(rule.bankId, true)
    }
    paperQuestions.value = generatePaperQuestionsFromRules(ruleset.value, bankQuestions.value)
    paperStale.value = false
  }

  async function loadPickerQuestions() {
    if (!picker.bankId) {
      pickerQuestions.value = []
      return
    }
    pickerLoading.value = true
    try {
      const result = await fetchQuestions({
        page: 1,
        size: 100,
        bankId: picker.bankId,
        keyword: picker.keyword || undefined,
      })
      pickerQuestions.value = result.records.filter((question) => question.status === 'ACTIVE')
    } finally {
      pickerLoading.value = false
    }
  }

  function addManualQuestion(question: Question) {
    if (hasPaperQuestion(question.id)) {
      return
    }
    paperQuestions.value = normalizePaperSort([
      ...paperQuestions.value,
      toGeneratedPaperQuestion(question, defaultManualScore(question.type), (paperQuestions.value.length + 1) * 10),
    ])
  }

  function hasPaperQuestion(questionId: number) {
    return paperQuestions.value.some((question) => question.questionId === questionId)
  }

  function defaultManualScore(type: Question['type']) {
    return calculateDefaultManualScore(type, ruleset.value, picker.bankId)
  }

  function movePaperQuestion(index: number, offset: number) {
    const target = index + offset
    if (target < 0 || target >= paperQuestions.value.length) {
      return
    }
    const rows = [...paperQuestions.value]
    const [current] = rows.splice(index, 1)
    rows.splice(target, 0, current)
    paperQuestions.value = normalizePaperSort(rows)
  }

  function sortPaperQuestions() {
    paperQuestions.value = normalizePaperSort([...paperQuestions.value].sort((left, right) => left.sortOrder - right.sortOrder))
  }

  function removePaperQuestion(index: number) {
    const rows = [...paperQuestions.value]
    rows.splice(index, 1)
    paperQuestions.value = normalizePaperSort(rows)
  }

  async function buildPayload(): Promise<ExamPayload | null> {
    for (const rule of ruleset.value) {
      await loadBankQuestions(rule.bankId)
    }
    const payloadRules = toExamRulePayloads(ruleset.value)
    if (form.examMode === 'ANSWER_SHEET') {
      if (!hasMaterialFiles(materialGroups.value)) {
        ElMessage.error('请先添加试卷材料')
        return null
      }
      if (answerCardItems.value.length === 0) {
        ElMessage.error('请先配置答题卡')
        return null
      }
      const cardError = answerCardValidationError(answerCardItems.value)
      if (cardError) {
        ElMessage.error(cardError)
        return null
      }
    } else if (paperQuestions.value.length === 0 && payloadRules.length > 0) {
      await generatePaperQuestions()
    }
    if (form.examMode === 'STRUCTURED' && paperQuestions.value.length === 0) {
      ElMessage.error('请先按规则生成题目明细或手工加入试题')
      return null
    }
    if (form.qualifyScore > totalScore.value) {
      ElMessage.error('及格分不能超过试卷总分')
      return null
    }
    if (form.openType === 'DEPARTMENT' && form.departmentIds.length === 0) {
      ElMessage.error('部门开放必须选择部门')
      return null
    }
    if (form.timeLimit) {
      if (!timeRange.value?.[0] || !timeRange.value?.[1]) {
        ElMessage.error('请选择开放时间')
        return null
      }
      form.startTime = timeRange.value[0]
      form.endTime = timeRange.value[1]
    } else {
      form.startTime = '2026-01-01T00:00:00'
      form.endTime = '2099-12-31T23:59:59'
    }
    form.attemptLimit = attemptLimitMode.value === 'LIMITED' ? Math.max(1, Number(limitedAttemptCount.value || 1)) : null
    return {
      title: form.title,
      description: form.description,
      qualifyScore: form.qualifyScore,
      startTime: form.startTime,
      endTime: form.endTime,
      durationMinutes: form.durationMinutes,
      timeLimit: form.timeLimit,
      attemptLimit: form.attemptLimit,
      examMode: form.examMode,
      displayMode: form.displayMode,
      questionOrderMode: form.questionOrderMode,
      openType: form.openType,
      departmentIds: [...form.departmentIds],
      rules: form.examMode === 'ANSWER_SHEET' ? [] : payloadRules,
      paperQuestions: form.examMode === 'ANSWER_SHEET' ? [] : toExamPaperQuestionPayloads(paperQuestions.value),
      materialGroups: form.examMode === 'ANSWER_SHEET' ? materialGroups.value : [],
      answerCardItems: form.examMode === 'ANSWER_SHEET' ? answerCardItems.value : [],
    }
  }

  return {
    answerCardItems,
    attemptLimitMode,
    bankQuestions,
    buildPayload,
    closing,
    currentStatus,
    editorVisible,
    editingExam,
    fillEditor,
    form,
    formRef,
    formRules,
    generatePaperQuestions,
    addManualQuestion,
    addRule,
    limitedAttemptCount,
    loadPickerQuestions,
    markPaperStale,
    materialGroups,
    movePaperQuestion,
    onRuleBankChange,
    paperQuestions,
    picker,
    pickerLoading,
    pickerQuestions,
    previewVisible,
    publishing,
    removePaperQuestion,
    removeRule,
    resetForm,
    ruleset,
    saving,
    sortPaperQuestions,
    timeRange,
    totalQuestionCount,
    totalScore,
  }
}

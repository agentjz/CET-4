<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <h1>考试管理</h1>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateEditor">新建考试</el-button>
    </header>

    <div class="toolbar">
      <el-input v-model.trim="query.keyword" clearable placeholder="搜索考试名称" class="toolbar__search" @keyup.enter="loadExams" />
      <el-button :icon="Search" @click="loadExams">搜索</el-button>
    </div>

    <el-table v-loading="loading" :data="exams" class="data-table" border>
      <el-table-column label="考试名称" min-width="220">
        <template #default="{ row }: { row: Exam }">
          <span class="entity-name">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column label="组卷规模" width="140">
        <template #default="{ row }: { row: Exam }">
          {{ row.questionCount }} 题 / {{ row.totalScore }} 分
        </template>
      </el-table-column>
      <el-table-column label="及格分" width="100">
        <template #default="{ row }: { row: Exam }">
          {{ row.qualifyScore }}
        </template>
      </el-table-column>
      <el-table-column label="时间" min-width="230">
        <template #default="{ row }: { row: Exam }">
          <div class="entity-stack">
            <span>{{ row.durationMinutes }} 分钟</span>
            <span class="muted-text">{{ row.timeLimit ? `${formatDateTime(row.startTime)} 至 ${formatDateTime(row.endTime)}` : '不限考试日期' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="开放范围" width="120">
        <template #default="{ row }: { row: Exam }">
          <el-tag effect="plain" :type="row.openType === 'PUBLIC' ? 'success' : 'warning'">
            {{ row.openType === 'PUBLIC' ? '公开' : '部门' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="题目显示" width="150">
        <template #default="{ row }: { row: Exam }">
          <div class="entity-stack">
            <span>{{ displayModeText(row.displayMode) }}</span>
            <span class="muted-text">{{ questionOrderText(row.questionOrderMode) }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="可考次数" width="110">
        <template #default="{ row }: { row: Exam }">
          {{ row.attemptLimit ? `${row.attemptLimit} 次` : '无限次' }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }: { row: Exam }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column fixed="right" label="操作" width="350">
        <template #default="{ row }: { row: Exam }">
          <el-button link type="primary" @click="openEditEditor(row)">编辑</el-button>
          <el-button link type="primary" @click="openResults(row)">成绩</el-button>
          <el-button link type="primary" @click="copyCurrentExam(row)">复制</el-button>
          <el-button link type="primary" @click="downloadCurrentExam(row)">下载</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" link type="warning" @click="revokeCurrentExam(row)">撤销发布</el-button>
          <el-button link type="danger" @click="deleteCurrentExam(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-row">
      <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="loadExams" />
    </div>

    <ExamEditorDialog
      ref="formRef"
      v-model:visible="editorVisible"
      v-model:ruleset="ruleset"
      v-model:paper-questions="paperQuestions"
      v-model:material-groups="materialGroups"
      v-model:answer-card-items="answerCardItems"
      v-model:time-range="timeRange"
      v-model:attempt-limit-mode="attemptLimitMode"
      v-model:limited-attempt-count="limitedAttemptCount"
      :editing-exam="editingExam"
      :current-status="currentStatus"
      :form="form"
      :form-rules="formRules"
      :total-score="totalScore"
      :total-question-count="totalQuestionCount"
      :banks="banks"
      :bank-questions="bankQuestions"
      :picker="picker"
      :picker-questions="pickerQuestions"
      :picker-loading="pickerLoading"
      :departments="departments"
      :saving="saving"
      :publishing="publishing"
      :closing="closing"
      @add-rule="addRule"
      @remove-rule="removeRule"
      @rule-bank-change="onRuleBankChange"
      @mark-paper-stale="markPaperStale"
      @generate-paper="generatePaperQuestions"
      @preview-paper="previewVisible = true"
      @load-picker-questions="loadPickerQuestions"
      @add-manual-question="addManualQuestion"
      @sort-paper="sortPaperQuestions"
      @move-paper-question="movePaperQuestion"
      @remove-paper-question="removePaperQuestion"
      @close-exam="closeCurrentExam"
      @save-draft="saveDraft"
      @publish-exam="publishCurrentExam"
    />

    <ExamPaperPreviewDialog v-model:visible="previewVisible" :questions="paperQuestions" />
    <ExamResultsDrawer
      v-model:visible="resultsVisible"
      :exam="selectedResultExam"
      :results="examResults"
      :loading="resultsLoading"
      @open-detail="openResultDetail"
    />
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

import ExamEditorDialog from '@/components/admin/exams/ExamEditorDialog.vue'
import ExamPaperPreviewDialog from '@/components/admin/exams/ExamPaperPreviewDialog.vue'
import ExamResultsDrawer from '@/components/admin/exams/ExamResultsDrawer.vue'
import { fetchDepartments, type Department } from '@/api/admin'
import { useExamEditor } from '@/composables/useExamEditor'
import {
  closeExam,
  copyExam,
  createExam,
  deleteExam,
  downloadExamPaper,
  fetchAdminExamDetail,
  fetchAdminExams,
  fetchAdminResults,
  fetchQuestionBanks,
  publishExam,
  revokeExam,
  updateExam,
  type Exam,
  type ExamResult,
  type QuestionBank,
} from '@/api/exam-business'
import { downloadBlob } from '@/utils/download'
import { formatDateTime } from '@/utils/datetime'

const router = useRouter()
const exams = ref<Exam[]>([])
const banks = ref<QuestionBank[]>([])
const departments = ref<Department[]>([])
const examResults = ref<ExamResult[]>([])
const total = ref(0)
const loading = ref(false)
const resultsLoading = ref(false)
const resultsVisible = ref(false)
const selectedResultExam = ref<Exam | null>(null)
const query = reactive({ page: 1, size: 20, keyword: '' })

const {
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
} = useExamEditor()

onMounted(async () => {
  await Promise.all([loadBanks(), loadDepartments(), loadExams()])
})

async function loadBanks() {
  const result = await fetchQuestionBanks({ page: 1, size: 200 })
  banks.value = result.records
  picker.bankId = picker.bankId || banks.value[0]?.id || null
}

async function loadDepartments() {
  departments.value = await fetchDepartments()
}

async function loadExams() {
  loading.value = true
  try {
    const result = await fetchAdminExams({ page: query.page, size: query.size, keyword: query.keyword || undefined })
    exams.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function openCreateEditor() {
  editingExam.value = null
  resetForm()
  addRule()
  editorVisible.value = true
}

async function openEditEditor(exam: Exam) {
  resetForm()
  const detail = await fetchAdminExamDetail(exam.id)
  await fillEditor(detail)
  editorVisible.value = true
}

async function saveDraft() {
  await formRef.value?.validate()
  const payload = await buildPayload()
  if (!payload) {
    return
  }
  saving.value = true
  try {
    const saved = editingExam.value ? await updateExam(editingExam.value.id, payload) : await createExam(payload)
    const detail = await fetchAdminExamDetail(saved.id)
    await fillEditor(detail)
    ElMessage.success('草稿已保存')
    await loadExams()
  } finally {
    saving.value = false
  }
}

async function publishCurrentExam() {
  if (!editingExam.value) {
    ElMessage.warning('请先保存草稿后再发布')
    return
  }
  publishing.value = true
  try {
    const published = await publishExam(editingExam.value.id)
    await fillEditor(await fetchAdminExamDetail(published.id))
    ElMessage.success('考试已发布')
    await loadExams()
  } finally {
    publishing.value = false
  }
}

async function closeCurrentExam() {
  if (!editingExam.value) {
    return
  }
  closing.value = true
  try {
    const closed = await closeExam(editingExam.value.id)
    await fillEditor(await fetchAdminExamDetail(closed.id))
    ElMessage.success('考试已关闭')
    await loadExams()
  } finally {
    closing.value = false
  }
}

async function copyCurrentExam(exam: Exam) {
  const copied = await copyExam(exam.id)
  ElMessage.success('试卷已复制')
  await loadExams()
  await openEditEditor(copied)
}

async function downloadCurrentExam(exam: Exam) {
  const blob = await downloadExamPaper(exam.id)
  downloadBlob(blob, `${exam.title}.xlsx`)
}

async function revokeCurrentExam(exam: Exam) {
  await ElMessageBox.confirm(`确认撤销发布“${exam.title}”？`, '撤销发布', {
    type: 'warning',
    confirmButtonText: '撤销发布',
    cancelButtonText: '取消',
  })
  await revokeExam(exam.id)
  ElMessage.success('考试已撤销发布')
  await loadExams()
}

async function deleteCurrentExam(exam: Exam) {
  await ElMessageBox.confirm(`确认删除考试“${exam.title}”？没有作答或成绩的考试会连同草稿和发布快照一起删除。`, '删除考试', {
    type: 'warning',
    confirmButtonText: '删除考试',
    cancelButtonText: '取消',
  })
  await deleteExam(exam.id)
  ElMessage.success('考试已删除')
  if (editingExam.value?.id === exam.id) {
    editorVisible.value = false
    editingExam.value = null
  }
  await loadExams()
}

async function openResults(exam: Exam) {
  selectedResultExam.value = exam
  resultsVisible.value = true
  resultsLoading.value = true
  try {
    examResults.value = await fetchAdminResults({ examId: exam.id })
  } finally {
    resultsLoading.value = false
  }
}

function openResultDetail(resultId: number) {
  void router.push({ name: 'admin-result-detail', params: { resultId } })
}

function statusText(status: Exam['status']) {
  return status === 'PUBLISHED' ? '已发布' : status === 'CLOSED' ? '已关闭' : '草稿'
}

function statusType(status: Exam['status']) {
  return status === 'PUBLISHED' ? 'success' : status === 'CLOSED' ? 'info' : 'warning'
}

function displayModeText(displayMode: Exam['displayMode']) {
  return displayMode === 'ALL' ? '整卷一页' : '逐题显示'
}

function questionOrderText(mode: Exam['questionOrderMode']) {
  return mode === 'RANDOM' ? '随机顺序' : '固定顺序'
}

</script>

<template>
  <section class="answer-sheet-block">
    <div class="section-title-row">
      <h2>答题卡</h2>
      <el-button @click="addSingleItem">添加题号</el-button>
    </div>

    <div class="batch-card">
      <el-input-number v-model="batch.start" :min="1" :controls="false" />
      <span>至</span>
      <el-input-number v-model="batch.end" :min="batch.start" :controls="false" />
      <el-select v-model="batch.answerType">
        <el-option label="单选" value="SINGLE_CHOICE" />
        <el-option label="多选" value="MULTIPLE_CHOICE" />
        <el-option label="写作" value="WRITING" />
      </el-select>
      <el-select v-model="batch.optionEndLabel" :disabled="batch.answerType === 'WRITING'" filterable>
        <el-option v-for="label in optionEndLabels" :key="label" :label="`A-${label}`" :value="label" />
      </el-select>
      <el-input-number v-model="batch.score" :min="0.5" :step="0.5" :controls="false" />
      <el-button type="primary" plain @click="addBatch">批量生成</el-button>
    </div>

    <el-empty v-if="answerCardItems.length === 0" description="添加题号后配置选项、答案和分值" />

    <el-table v-else :data="answerCardItems" border>
      <el-table-column label="题号" width="95">
        <template #default="{ row }">
          <el-input-number v-model="row.questionNo" :min="1" :controls="false" />
        </template>
      </el-table-column>
      <el-table-column label="作答类型" width="130">
        <template #default="{ row }">
          <el-select v-model="row.answerType" @change="normalizeCardItem(row)">
            <el-option label="单选" value="SINGLE_CHOICE" />
            <el-option label="多选" value="MULTIPLE_CHOICE" />
            <el-option label="写作" value="WRITING" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="选项范围" width="130">
        <template #default="{ row }">
          <el-select
            :model-value="answerOptionEndLabel(row.optionLabels)"
            :disabled="row.answerType === 'WRITING'"
            filterable
            @change="setOptionEndLabel(row, String($event))"
          >
            <el-option v-for="label in optionEndLabels" :key="label" :label="`A-${label}`" :value="label" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="正确答案" min-width="220">
        <template #default="{ row }">
          <span v-if="row.answerType === 'WRITING'" class="muted-text">人工阅卷</span>
          <el-radio-group v-else-if="row.answerType === 'SINGLE_CHOICE'" class="answer-option-group" :model-value="row.correctLabels[0]" @change="row.correctLabels = [String($event)]">
            <el-radio-button v-for="label in row.optionLabels" :key="label" :value="label">{{ label }}</el-radio-button>
          </el-radio-group>
          <el-checkbox-group v-else v-model="row.correctLabels" class="answer-option-group">
            <el-checkbox-button v-for="label in row.optionLabels" :key="label" :value="label">{{ label }}</el-checkbox-button>
          </el-checkbox-group>
        </template>
      </el-table-column>
      <el-table-column label="分值" width="120">
        <template #default="{ row }">
          <el-input-number v-model="row.score" :min="0.5" :step="0.5" :controls="false" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190">
        <template #default="{ $index }">
          <el-button link :disabled="$index === 0" @click="moveItem($index, -1)">上移</el-button>
          <el-button link :disabled="$index === answerCardItems.length - 1" @click="moveItem($index, 1)">下移</el-button>
          <el-button link type="danger" @click="answerCardItems.splice($index, 1)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

import type { ExamPayload } from '@/api/exam-business'
import {
  DEFAULT_ANSWER_OPTION_END_LABEL,
  answerOptionEndLabel,
  answerOptionEndLabels,
  answerOptionLabelsTo,
} from '@/utils/admin-answer-sheet-editor'

const answerCardItems = defineModel<ExamPayload['answerCardItems']>('answerCardItems', { required: true })

const optionEndLabels = answerOptionEndLabels()

const batch = reactive({
  start: 1,
  end: 5,
  answerType: 'SINGLE_CHOICE' as ExamPayload['answerCardItems'][number]['answerType'],
  optionEndLabel: DEFAULT_ANSWER_OPTION_END_LABEL,
  score: 5,
})

function addSingleItem() {
  const nextNo = Math.max(0, ...answerCardItems.value.map((item) => item.questionNo)) + 1
  answerCardItems.value.push(createItem(nextNo, 'SINGLE_CHOICE', answerOptionLabelsTo(DEFAULT_ANSWER_OPTION_END_LABEL), 5))
}

function addBatch() {
  if (batch.end < batch.start) {
    ElMessage.error('结束题号不能小于起始题号')
    return
  }
  const existing = new Set(answerCardItems.value.map((item) => item.questionNo))
  const labels = batch.answerType === 'WRITING' ? [] : answerOptionLabelsTo(batch.optionEndLabel)
  const created = []
  for (let questionNo = batch.start; questionNo <= batch.end; questionNo += 1) {
    if (existing.has(questionNo)) {
      continue
    }
    created.push(createItem(questionNo, batch.answerType, labels, batch.score))
  }
  answerCardItems.value.push(...created)
  sortByQuestionNo()
  ElMessage.success(`已生成 ${created.length} 个题号`)
}

function createItem(
  questionNo: number,
  answerType: ExamPayload['answerCardItems'][number]['answerType'],
  optionLabels: string[],
  score: number,
) {
  return {
    questionNo,
    answerType,
    optionLabels: [...optionLabels],
    correctLabels: defaultCorrectLabels(answerType, optionLabels),
    score,
    sortOrder: (answerCardItems.value.length + 1) * 10,
  }
}

function normalizeCardItem(row: ExamPayload['answerCardItems'][number]) {
  if (row.answerType === 'WRITING') {
    row.optionLabels = []
    row.correctLabels = []
    return
  }
  if (row.optionLabels.length < 2) {
    row.optionLabels = answerOptionLabelsTo(DEFAULT_ANSWER_OPTION_END_LABEL)
  }
  row.correctLabels = defaultCorrectLabels(row.answerType, row.optionLabels)
}

function setOptionEndLabel(row: ExamPayload['answerCardItems'][number], endLabel: string) {
  row.optionLabels = answerOptionLabelsTo(endLabel)
  row.correctLabels = defaultCorrectLabels(row.answerType, row.optionLabels)
}

function defaultCorrectLabels(answerType: ExamPayload['answerCardItems'][number]['answerType'], labels: string[]) {
  if (answerType === 'WRITING') {
    return []
  }
  if (answerType === 'MULTIPLE_CHOICE') {
    return labels.slice(0, 2)
  }
  return labels.slice(0, 1)
}

function moveItem(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= answerCardItems.value.length) {
    return
  }
  const [current] = answerCardItems.value.splice(index, 1)
  answerCardItems.value.splice(target, 0, current)
  normalizeSort()
}

function sortByQuestionNo() {
  answerCardItems.value.sort((left, right) => left.questionNo - right.questionNo)
  normalizeSort()
}

function normalizeSort() {
  answerCardItems.value.forEach((item, index) => {
    item.sortOrder = (index + 1) * 10
  })
}
</script>

<style scoped>
.answer-sheet-block {
  display: grid;
  gap: 12px;
}

.section-title-row,
.batch-card {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.section-title-row {
  justify-content: space-between;
}

.section-title-row h2 {
  margin: 0;
  font-size: 16px;
  letter-spacing: 0;
}

.batch-card {
  flex-wrap: wrap;
  padding: 12px;
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: var(--ks-panel-muted);
}

.batch-card :deep(.el-input-number) {
  width: 86px;
}

.batch-card :deep(.el-select) {
  width: 120px;
}

.answer-option-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.answer-option-group :deep(.el-radio-button),
.answer-option-group :deep(.el-checkbox-button) {
  margin: 0;
}
</style>

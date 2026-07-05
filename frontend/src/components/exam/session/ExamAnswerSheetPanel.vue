<template>
  <section class="answer-sheet-panel" aria-label="答题卡作答区域">
    <header class="answer-sheet-panel__header">
      <div>
        <h2>答题卡</h2>
      </div>
      <span>{{ questions.length }} 题</span>
    </header>

    <div class="answer-sheet-panel__list">
      <article
        v-for="(question, index) in questions"
        :id="panelId(question.questionId)"
        :key="question.questionId"
        class="answer-sheet-question"
      >
        <div class="answer-sheet-question__title">
          <strong>{{ index + 1 }}</strong>
        </div>

        <el-checkbox-group
          v-if="isMultipleAnswerType(question.type)"
          v-model="multipleAnswers[question.questionId]"
          class="answer-sheet-options"
          :disabled="disabled"
          @change="$emit('schedule-save', question)"
        >
          <el-checkbox-button v-for="option in question.options" :key="option.id" :value="option.label">
            {{ option.label }}
          </el-checkbox-button>
        </el-checkbox-group>

        <el-radio-group
          v-else-if="isOptionBasedQuestion(question)"
          v-model="singleAnswers[question.questionId]"
          class="answer-sheet-options"
          :disabled="disabled"
          @change="$emit('schedule-save', question)"
        >
          <el-radio-button v-for="option in question.options" :key="option.id" :value="option.label">
            {{ option.label }}
          </el-radio-button>
        </el-radio-group>

        <el-input
          v-else
          v-model="textAnswers[question.questionId]"
          type="textarea"
          :rows="8"
          maxlength="5000"
          show-word-limit
          resize="vertical"
          placeholder="请输入答案"
          :disabled="disabled"
          @input="$emit('schedule-save', question)"
          @blur="$emit('save-immediately', question)"
        />
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ExamQuestion } from '@/api/exam-business'
import type { MultipleAnswerMap, SingleAnswerMap, TextAnswerMap } from '@/utils/exam-session'
import { isMultipleAnswerType, questionTypeMeta } from '@/utils/question-types'

withDefaults(defineProps<{
  questions: ExamQuestion[]
  singleAnswers: SingleAnswerMap
  multipleAnswers: MultipleAnswerMap
  textAnswers: TextAnswerMap
  disabled?: boolean
}>(), {
  disabled: false,
})

defineEmits<{
  'schedule-save': [question: ExamQuestion]
  'save-immediately': [question: ExamQuestion]
}>()

function panelId(questionId: number) {
  return `answer-sheet-question-${questionId}`
}

function isOptionBasedQuestion(question: ExamQuestion) {
  return questionTypeMeta(question.type).optionBased
}
</script>

<style scoped>
.answer-sheet-panel {
  display: grid;
  gap: 16px;
  padding: clamp(16px, 2vw, 22px);
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: var(--ks-panel);
}

.answer-sheet-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.answer-sheet-panel__header h2 {
  margin: 0;
  color: var(--ks-text);
  font-size: 18px;
  letter-spacing: 0;
}

.answer-sheet-panel__header span {
  flex: none;
  color: var(--ks-text-muted);
  font-size: 13px;
}

.answer-sheet-panel__list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  gap: 12px;
}

.answer-sheet-question {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: var(--ks-panel-muted);
  scroll-margin-top: 96px;
}

.answer-sheet-question__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.answer-sheet-question__title strong {
  display: grid;
  flex: none;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 6px;
  background: var(--ks-primary);
  color: #fff;
}

.answer-sheet-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.answer-sheet-options :deep(.el-radio-button),
.answer-sheet-options :deep(.el-checkbox-button) {
  margin: 0;
}

.answer-sheet-options :deep(.el-radio-button__inner),
.answer-sheet-options :deep(.el-checkbox-button__inner) {
  min-width: 42px;
  border-left: var(--el-border);
  border-radius: 6px;
}

@media (max-width: 760px) {
  .answer-sheet-panel__header {
    flex-direction: column;
  }
}
</style>

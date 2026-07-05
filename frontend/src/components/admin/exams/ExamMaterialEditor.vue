<template>
  <section class="answer-sheet-block">
    <div class="section-title-row">
      <h2>试卷材料</h2>
      <el-button @click="addGroup">添加材料块</el-button>
    </div>

    <el-empty v-if="materialGroups.length === 0" description="添加 PDF、图片、音频、文件或外部链接作为试卷材料" />

    <div v-for="(group, groupIndex) in materialGroups" :key="group.sortOrder" class="material-group">
      <div class="material-group__header">
        <el-input v-model.trim="group.title" placeholder="材料块名称，如 听力材料" />
        <div class="material-group__actions">
          <el-button size="small" :disabled="groupIndex === 0" @click="moveGroup(groupIndex, -1)">上移</el-button>
          <el-button size="small" :disabled="groupIndex === materialGroups.length - 1" @click="moveGroup(groupIndex, 1)">下移</el-button>
          <el-button size="small" type="danger" plain @click="materialGroups.splice(groupIndex, 1)">删除</el-button>
        </div>
      </div>
      <el-input v-model.trim="group.description" type="textarea" :rows="2" placeholder="材料说明" />

      <div class="material-group__tools">
        <el-upload :show-file-list="false" :before-upload="uploadHandler(groupIndex)" accept=".jpg,.jpeg,.png,.gif,.webp,.mp3,.wav,.ogg,.mp4,.pdf">
          <el-button :icon="Upload" :loading="uploadingGroupIndex === groupIndex">上传文件</el-button>
        </el-upload>
        <el-select v-model="existingAttachmentDrafts[groupIndex]" filterable clearable placeholder="从已有附件选择">
          <el-option
            v-for="attachment in existingAttachments"
            :key="attachment.fileUrl"
            :label="attachment.fileName"
            :value="attachment.fileUrl"
          />
        </el-select>
        <el-button @click="addExistingAttachment(groupIndex)">添加附件</el-button>
        <el-input v-model.trim="urlDrafts[groupIndex]" placeholder="输入外部链接或本地资源路径" />
        <el-select v-model="urlMediaTypes[groupIndex]" class="material-type-select">
          <el-option label="PDF/文件" value="FILE" />
          <el-option label="图片" value="IMAGE" />
          <el-option label="音频" value="AUDIO" />
          <el-option label="视频" value="VIDEO" />
        </el-select>
        <el-button @click="addUrlFile(groupIndex)">添加链接</el-button>
      </div>

      <el-table v-if="group.files.length" :data="group.files" border class="material-table">
        <el-table-column label="材料" min-width="220">
          <template #default="{ row }">
            <div class="material-file-main">
              <el-tag effect="plain">{{ mediaTypeText(row.mediaType) }}</el-tag>
              <el-input v-model.trim="row.displayName" placeholder="展示名称" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="180">
          <template #default="{ row }">
            <el-input v-model.trim="row.description" placeholder="文件说明" />
          </template>
        </el-table-column>
        <el-table-column label="地址" min-width="260">
          <template #default="{ row }">
            <el-link :href="resolveResourceUrl(row.fileUrl)" target="_blank">{{ row.fileName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ $index }">
            <el-button link :disabled="$index === 0" @click="moveFile(groupIndex, $index, -1)">上移</el-button>
            <el-button link :disabled="$index === group.files.length - 1" @click="moveFile(groupIndex, $index, 1)">下移</el-button>
            <el-button link type="danger" @click="group.files.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, type UploadRawFile } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'

import { fetchQuestions, uploadFile, type ExamPayload, type QuestionAttachmentPayload } from '@/api/exam-business'
import { resolveResourceUrl } from '@/utils/resource-url'

const materialGroups = defineModel<ExamPayload['materialGroups']>('materialGroups', { required: true })

const uploadingGroupIndex = ref<number | null>(null)
const urlDrafts = ref<Record<number, string>>({})
const urlMediaTypes = ref<Record<number, QuestionAttachmentPayload['mediaType']>>({})
const existingAttachmentDrafts = ref<Record<number, string>>({})
const existingAttachments = ref<QuestionAttachmentPayload[]>([])

onMounted(async () => {
  const result = await fetchQuestions({ page: 1, size: 500 })
  existingAttachments.value = result.records.flatMap((question) => question.attachments.map((attachment) => ({
    fileName: attachment.fileName,
    fileUrl: attachment.fileUrl,
    mediaType: attachment.mediaType,
  })))
})

function addGroup() {
  const index = materialGroups.value.length
  materialGroups.value.push({
    title: index === 0 ? '试卷材料' : `材料块 ${index + 1}`,
    description: '',
    sortOrder: (index + 1) * 10,
    files: [],
  })
  urlMediaTypes.value[index] = 'FILE'
}

async function handleUpload(groupIndex: number, file: UploadRawFile) {
  uploadingGroupIndex.value = groupIndex
  try {
    const uploaded = await uploadFile(file)
    materialGroups.value[groupIndex].files.push({
      sourceType: 'UPLOAD',
      displayName: uploaded.fileName,
      description: '',
      fileName: uploaded.fileName,
      fileUrl: uploaded.fileUrl,
      mediaType: uploaded.mediaType,
      sortOrder: (materialGroups.value[groupIndex].files.length + 1) * 10,
    })
    ElMessage.success('材料已上传')
  } finally {
    uploadingGroupIndex.value = null
  }
  return false
}

function uploadHandler(groupIndex: number) {
  return (file: UploadRawFile) => handleUpload(groupIndex, file)
}

function addUrlFile(groupIndex: number) {
  const url = urlDrafts.value[groupIndex]?.trim()
  if (!url) {
    ElMessage.error('请输入材料链接')
    return
  }
  const fileName = url.split('/').pop() || 'material'
  const mediaType = inferMediaType(url, urlMediaTypes.value[groupIndex] || 'FILE')
  materialGroups.value[groupIndex].files.push({
    sourceType: url.startsWith('/local-assets/') ? 'LOCAL_ASSET' : 'EXTERNAL_LINK',
    displayName: fileName,
    description: '',
    fileName,
    fileUrl: url,
    mediaType,
    sortOrder: (materialGroups.value[groupIndex].files.length + 1) * 10,
  })
  urlDrafts.value[groupIndex] = ''
}

function addExistingAttachment(groupIndex: number) {
  const fileUrl = existingAttachmentDrafts.value[groupIndex]
  const attachment = existingAttachments.value.find((item) => item.fileUrl === fileUrl)
  if (!attachment) {
    ElMessage.error('请选择已有附件')
    return
  }
  materialGroups.value[groupIndex].files.push({
    sourceType: 'EXISTING_ATTACHMENT',
    displayName: attachment.fileName,
    description: '',
    fileName: attachment.fileName,
    fileUrl: attachment.fileUrl,
    mediaType: attachment.mediaType,
    sortOrder: (materialGroups.value[groupIndex].files.length + 1) * 10,
  })
  existingAttachmentDrafts.value[groupIndex] = ''
}

function moveGroup(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= materialGroups.value.length) {
    return
  }
  const [current] = materialGroups.value.splice(index, 1)
  materialGroups.value.splice(target, 0, current)
  normalizeGroupSort()
}

function moveFile(groupIndex: number, index: number, offset: number) {
  const files = materialGroups.value[groupIndex].files
  const target = index + offset
  if (target < 0 || target >= files.length) {
    return
  }
  const [current] = files.splice(index, 1)
  files.splice(target, 0, current)
  files.forEach((file, fileIndex) => {
    file.sortOrder = (fileIndex + 1) * 10
  })
}

function normalizeGroupSort() {
  materialGroups.value.forEach((group, index) => {
    group.sortOrder = (index + 1) * 10
  })
}

function inferMediaType(url: string, fallback: QuestionAttachmentPayload['mediaType']) {
  if (/\.(png|jpe?g|gif|webp)(\?.*)?$/i.test(url)) {
    return 'IMAGE'
  }
  if (/\.(mp3|wav|ogg)(\?.*)?$/i.test(url)) {
    return 'AUDIO'
  }
  if (/\.(mp4|webm)(\?.*)?$/i.test(url)) {
    return 'VIDEO'
  }
  return fallback
}

function mediaTypeText(type: QuestionAttachmentPayload['mediaType']) {
  const names: Record<QuestionAttachmentPayload['mediaType'], string> = {
    IMAGE: '图片',
    AUDIO: '音频',
    VIDEO: '视频',
    FILE: '文件',
  }
  return names[type]
}
</script>

<style scoped>
.answer-sheet-block,
.material-group {
  display: grid;
  gap: 12px;
}

.section-title-row,
.material-group__header,
.material-group__actions,
.material-file-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.section-title-row,
.material-group__header {
  justify-content: space-between;
}

.section-title-row h2 {
  margin: 0;
  font-size: 16px;
  letter-spacing: 0;
}

.material-group {
  padding: 12px;
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: var(--ks-panel-muted);
}

.material-group__tools {
  display: grid;
  grid-template-columns: auto minmax(180px, 1fr) auto minmax(220px, 1fr) 120px auto;
  gap: 10px;
  align-items: center;
}

.material-type-select {
  width: 120px;
}

.material-table {
  background: #fff;
}

@media (max-width: 900px) {
  .material-group__header,
  .material-group__tools {
    grid-template-columns: 1fr;
    display: grid;
  }

  .material-group__actions {
    flex-wrap: wrap;
  }
}
</style>

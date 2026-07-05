<template>
  <section v-if="materialGroups.length" class="exam-materials" aria-label="试卷材料">
    <article v-for="group in materialGroups" :key="group.id" class="exam-material-group">
      <header>
        <h2>{{ group.title }}</h2>
        <p v-if="group.description">{{ group.description }}</p>
      </header>
      <div class="exam-material-group__files">
        <div v-for="file in group.files" :key="file.id" class="exam-material-file">
          <strong>{{ file.displayName }}</strong>
          <p v-if="file.description">{{ file.description }}</p>
          <img v-if="file.mediaType === 'IMAGE'" :src="resolveResourceUrl(file.fileUrl)" :alt="file.fileName" />
          <audio v-else-if="file.mediaType === 'AUDIO'" :src="resolveResourceUrl(file.fileUrl)" controls />
          <iframe v-else-if="file.fileUrl.toLowerCase().endsWith('.pdf')" :src="resolveResourceUrl(file.fileUrl)" :title="file.displayName" />
          <el-link v-else :href="resolveResourceUrl(file.fileUrl)" target="_blank">{{ file.fileName }}</el-link>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import type { ExamMaterialGroup } from '@/api/exam-business'
import { resolveResourceUrl } from '@/utils/resource-url'

defineProps<{
  materialGroups: ExamMaterialGroup[]
}>()
</script>

<style scoped>
.exam-materials {
  display: grid;
  gap: 12px;
}

.exam-material-group,
.exam-material-file {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.exam-material-group {
  padding: 14px;
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: #fff;
}

.exam-material-group h2 {
  margin: 0;
  font-size: 16px;
}

.exam-material-group p,
.exam-material-file p {
  margin: 0;
  color: var(--ks-text-muted);
  line-height: 1.6;
}

.exam-material-group__files {
  display: grid;
  gap: 12px;
}

.exam-material-file {
  padding: 10px;
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: var(--ks-panel-muted);
}

.exam-material-file img {
  width: 100%;
  max-height: 620px;
  object-fit: contain;
}

.exam-material-file audio {
  width: 100%;
}

.exam-material-file iframe {
  width: 100%;
  height: min(72vh, 760px);
  border: 1px solid var(--ks-border);
  border-radius: var(--ks-radius);
  background: #fff;
}
</style>

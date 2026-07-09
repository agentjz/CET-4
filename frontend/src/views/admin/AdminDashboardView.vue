<template>
  <div class="dashboard">
    <section class="dashboard-hero">
      <div class="dashboard-hero__copy">
        <p class="eyebrow">开源版体验</p>
        <h1>CET-4 四级考试平台</h1>
        <p>
          以大学英语四级真题作为演示样例，展示题库维护、考试发布、在线作答、阅卷评分和成绩复盘流程。
        </p>
        <div class="dashboard-hero__tags">
          <el-tag effect="plain">Vue 3</el-tag>
          <el-tag effect="plain">Spring Boot 3</el-tag>
          <el-tag effect="plain">MySQL 8</el-tag>
          <el-tag effect="plain">GitHub Pages Demo</el-tag>
        </div>
        <div class="dashboard-hero__actions">
          <el-button type="primary" @click="router.push('/my/exam')">开始体验考试</el-button>
          <el-button @click="router.push('/exam/repo')">查看题库</el-button>
          <a class="dashboard-link" href="https://github.com/luckymaomi/CET-4" target="_blank" rel="noreferrer" aria-label="GitHub 仓库">
            <svg aria-hidden="true" viewBox="0 0 16 16">
              <path
                fill="currentColor"
                d="M8 0C3.58 0 0 3.67 0 8.2c0 3.62 2.29 6.69 5.47 7.77.4.08.55-.18.55-.4v-1.4c-2.23.5-2.7-1.1-2.7-1.1-.36-.95-.89-1.2-.89-1.2-.73-.51.05-.5.05-.5.81.06 1.23.85 1.23.85.72 1.26 1.88.9 2.34.69.07-.53.28-.9.51-1.1-1.78-.21-3.64-.91-3.64-4.04 0-.89.31-1.62.82-2.19-.08-.21-.36-1.04.08-2.16 0 0 .67-.22 2.2.84A7.47 7.47 0 0 1 8 3.98c.68 0 1.36.09 2 .28 1.52-1.06 2.19-.84 2.19-.84.44 1.12.16 1.95.08 2.16.51.57.82 1.3.82 2.19 0 3.14-1.87 3.83-3.65 4.03.29.26.54.76.54 1.54v2.28c0 .22.14.48.55.4A8.15 8.15 0 0 0 16 8.2C16 3.67 12.42 0 8 0Z"
              />
            </svg>
            <span>GitHub</span>
          </a>
        </div>
      </div>
    </section>

    <section class="dashboard-section">
      <div class="dashboard-section__head">
        <p class="eyebrow">核心能力</p>
        <h2>从题库到成绩的完整考试闭环</h2>
      </div>
      <div class="dashboard-feature-grid">
        <article v-for="feature in features" :key="feature.title" class="dashboard-feature">
          <component :is="feature.icon" />
          <h3>{{ feature.title }}</h3>
          <p>{{ feature.summary }}</p>
        </article>
      </div>
    </section>

    <section class="dashboard-grid">
      <article v-for="module in modules" :key="module.title" class="dashboard-module">
        <div class="dashboard-module__head">
          <component :is="module.icon" />
          <h2>{{ module.title }}</h2>
        </div>
        <p>{{ module.summary }}</p>
        <div class="dashboard-actions">
          <el-button v-for="action in module.actions" :key="action.path" text type="primary" @click="router.push(action.path)">
            {{ action.label }}
          </el-button>
        </div>
      </article>
    </section>

    <section class="workflow-panel">
      <p class="eyebrow">使用路径</p>
      <h2>如何体验这个平台</h2>
      <ol>
        <li v-for="item in workflows" :key="item">{{ item }}</li>
      </ol>
    </section>

    <section class="metric-grid">
      <article class="metric">
        <span>当前用户</span>
        <strong>{{ auth.user?.displayName }}</strong>
      </article>
      <article class="metric">
        <span>当前角色</span>
        <strong>{{ auth.user?.roles.join(', ') }}</strong>
      </article>
      <article class="metric">
        <span>可用权限</span>
        <strong>{{ auth.permissions.length }}</strong>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { Collection, DataAnalysis, EditPen, Files, Finished, Setting } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

const features = [
  {
    title: '四级真题体验',
    summary: '内置四级题库样例，支持听力音频、阅读材料、写作题和考试作答流程。',
    icon: Files,
  },
  {
    title: '考试生命周期',
    summary: '覆盖草稿、组卷、发布、作答、提交、阅卷和成绩确认，避免隐式更新和状态混乱。',
    icon: Finished,
  },
  {
    title: '管理端闭环',
    summary: '管理员可以维护用户、部门、角色、题库、试题、考试和成绩，适合继续定制扩展。',
    icon: DataAnalysis,
  },
]

const modules = [
  {
    title: '在线考试',
    summary: '考生从这里查看可参加考试、进入准备页、开始作答并查看自己的成绩。',
    icon: EditPen,
    actions: [
      { label: '考试中心', path: '/my/exam' },
      { label: '我的成绩', path: '/my/exam/records' },
    ],
  },
  {
    title: '考试管理',
    summary: '考务人员在题库工作台维护题库和试题，再配置考试并发布。',
    icon: Collection,
    actions: [
      { label: '题库管理', path: '/exam/repo' },
      { label: '考试管理', path: '/exam/manage' },
    ],
  },
  {
    title: '系统管理',
    summary: '管理员先维护角色权限，再维护部门结构，最后维护用户归属。',
    icon: Setting,
    actions: [
      { label: '角色管理', path: '/sys/roles' },
      { label: '部门管理', path: '/sys/departments' },
      { label: '用户管理', path: '/sys/users' },
    ],
  },
]

const workflows = [
  '用左侧“在线考试”进入考试中心，选择四级考试并开始作答。',
  '用“题库管理”查看题库、试题和附件。',
  '用“考试管理”查看考试配置、成绩列表和人工阅卷流程。',
]
</script>

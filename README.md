# CET-4

<p align="center">
  <strong>CET-4 四级考试平台</strong>
</p>

<p align="center">
  <a href="https://github.com/luckymaomi/CET-4"><img alt="GitHub Repo" src="https://img.shields.io/badge/GitHub-luckymaomi%2FCET--4-181717?logo=github"></a>
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?logo=openjdk">
  <img alt="Spring Boot 3" src="https://img.shields.io/badge/Spring%20Boot-3-6DB33F?logo=springboot&logoColor=white">
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-42B883?logo=vuedotjs&logoColor=white">
  <img alt="TypeScript" src="https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript">
  <img alt="License MIT" src="https://img.shields.io/badge/License-MIT-blue">
</p>

## 🌐 在线体验

在线项目体验地址：[https://luckymaomi.github.io/CET-4/](https://luckymaomi.github.io/CET-4/)

体验账号：`admin` / `password`

CET-4 是一个面向大学英语四级练习和考试管理的现代化考试平台，覆盖题库建设、考试发布、在线作答、自动评分、人工阅卷和成绩归档等核心流程。

## ✨ 已实现功能

- 控制台：统一入口、菜单导航、页面标签和登录状态管理。
- 身份入口：邮箱注册、邮箱验证码、找回密码、重置密码、注册策略、注册申请审核和邮件服务状态自检。
- 在线考试：考试中心、准备考试、倒计时作答、答题卡、答案保存、提交锁定和成绩查看。
- 题库管理：题库树、试题分类、单选题、多选题、写作题、选项、答案、解析、难度和附件维护。
- 考试管理：考试草稿、发布、关闭、删除边界、规则组卷、手工选题、答题卡试卷、材料分组、文件上传、已有附件选择、外部链接材料、显式更新试卷、试卷预览和下载。
- 考试治理：考生名册、个人额外时长/次数、补考授权、成绩发布策略、基础报表、治理事件、文件资产 registry、安全策略、监考事件、阅卷 rubric、阅卷任务和复核。
- 成绩管理：选择题自动评分、写作题人工阅卷、阅卷进度保存、阅卷任务生成、复核记录、最终成绩确认和成绩详情复盘。
- 系统管理：角色管理、部门管理、用户管理、平台设置、权限配置、用户导入和用户导出。
- 平台治理：通知中心、外部集成配置、secret 脱敏、启停状态和测试事件记录。
- 富媒体题目：支持音频、图片和文件附件展示；答题卡试卷支持上方分组材料、下方答题卡作答和右侧题号导航。
- 演示体验：内置管理员、考生、题库、考试、作答和成绩样例，打开即可体验完整链路。

## 🛠️ 技术栈

- 后端：Java 21、Spring Boot 3、MyBatis-Plus、Spring Security、JWT
- 数据：MySQL 8、Redis、Flyway
- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus
- 文件与导入导出：Apache POI、文件附件
- 测试：JUnit、MockMvc、Vitest、Playwright
- 部署：Docker Compose、GitHub Pages

## 邮件配置

邮箱注册和找回密码使用通用 SMTP。生产部署通过环境变量配置，不把密码写入数据库，也不会在管理端回显：

- `KAOSHI_MAIL_ENABLED=true`
- `KAOSHI_MAIL_HOST`
- `KAOSHI_MAIL_PORT`
- `KAOSHI_MAIL_USERNAME`
- `KAOSHI_MAIL_PASSWORD`
- `KAOSHI_MAIL_FROM`
- `KAOSHI_MAIL_SSL_ENABLED`
- `KAOSHI_MAIL_STARTTLS_ENABLED`

QQ 邮箱、企业邮箱或云邮件服务都走同一套 SMTP 配置。

## License

MIT License

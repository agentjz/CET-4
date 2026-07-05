import { expect, test } from '@playwright/test'

import { collectConsoleIssues, login, setNumberInput } from './helpers'

const CET4_EXAM = 'CET-4 四级考试平台演示'
const CET4_BANK = '四级样例题库'

test.describe('考试管理', () => {
  test('考试管理支持搜索、新建、保存草稿、发布和详情读回', async ({ page }) => {
    const consoleIssues = collectConsoleIssues(page)
    await login(page)

    await page.locator('li.el-menu-item').filter({ hasText: /^考试管理$/ }).click()
    await expect(page.locator('.admin-page__header h1', { hasText: '考试管理' })).toBeVisible()
    await page.getByPlaceholder('搜索考试名称').fill('四级')
    await page.getByRole('button', { name: '搜索' }).click()
    await expect(page.getByText(CET4_EXAM)).toBeVisible()
    await page.getByRole('row').filter({ hasText: CET4_EXAM }).getByRole('button', { name: '成绩' }).click()
    const resultDrawer = page.getByRole('dialog', { name: new RegExp(`${CET4_EXAM} - 成绩`) })
    await expect(resultDrawer).toBeVisible()
    await expect(resultDrawer).toContainText('考试人数')
    await expect(resultDrawer.locator('.result-summary strong').first()).toHaveText('0')
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: '新建考试' }).click()
    const examDialog = page.getByRole('dialog', { name: '新建考试' })
    await expect(examDialog.getByText('组卷信息')).toBeVisible()
    await expect(examDialog.getByText('试卷总分')).toBeVisible()
    await examDialog.locator('.rule-item .el-select').first().click()
    await page.getByRole('option', { name: CET4_BANK }).click()
    await expect(examDialog.getByText(/单选题（可用 [1-9]/)).toBeVisible()
    await expect(examDialog.getByText(/写作题（可用 [1-9]/)).toBeVisible()
    const ruleNumbers = examDialog.locator('.rule-fields .el-input-number input')
    await setNumberInput(ruleNumbers.nth(0), '1')
    await setNumberInput(ruleNumbers.nth(1), '5')
    await setNumberInput(ruleNumbers.nth(2), '0')
    await setNumberInput(ruleNumbers.nth(3), '0')
    await setNumberInput(ruleNumbers.nth(4), '0')
    await setNumberInput(ruleNumbers.nth(5), '0')
    const examTitle = `浏览器考试 ${Date.now()}`
    await examDialog.getByLabel('考试名称').fill(examTitle)
    await examDialog.getByLabel('考试描述').fill('Playwright 创建考试')
    await examDialog.getByText('限定次数').click()
    await setNumberInput(examDialog.locator('.inline-control .el-input-number input'), '2')
    await examDialog.getByText('整卷一页').click()
    await examDialog.getByText('随机顺序').click()
    await examDialog.getByLabel('开放范围').getByText('公开考试').click()
    await examDialog.getByRole('button', { name: '按规则生成' }).click()
    await expect(examDialog.locator('.paper-table')).toContainText('essential')
    await expect(examDialog.getByRole('button', { name: '更新试卷' })).toBeEnabled()
    await examDialog.locator('.manual-picker').getByRole('button', { name: '加载试题' }).click()
    await examDialog.locator('.picker-table').getByRole('button', { name: '加入试卷' }).nth(1).click()
    await expect(examDialog.locator('.paper-table tbody tr')).toHaveCount(2)
    await examDialog.locator('.paper-table').getByRole('button', { name: '下移' }).first().click()
    await expect(examDialog.locator('.paper-table tbody tr').first()).not.toContainText('essential')
    await examDialog.getByRole('button', { name: '预览试卷' }).click()
    await expect(page.getByRole('dialog', { name: '试卷预览' })).toBeVisible()
    await page.keyboard.press('Escape')
    await examDialog.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText('草稿已保存')).toBeVisible()
    const savedDialog = page.getByRole('dialog', { name: '编辑考试' })
    await expect(savedDialog.locator('.publish-summary strong').filter({ hasText: /^草稿$/ })).toBeVisible()
    await expect(savedDialog.locator('.paper-table tbody tr')).toHaveCount(2)
    await savedDialog.getByRole('button', { name: '发布考试' }).click()
    await expect(page.getByText('考试已发布')).toBeVisible()
    await expect(savedDialog.locator('.publish-summary strong').filter({ hasText: /^已发布$/ })).toBeVisible()
    await savedDialog.getByRole('button', { name: '取消' }).click()
    await page.getByPlaceholder('搜索考试名称').fill(examTitle)
    await page.getByRole('button', { name: '搜索' }).click()
    const savedRow = page.getByRole('row').filter({ hasText: examTitle, hasNotText: '副本' })
    const paperDownloadPromise = page.waitForEvent('download')
    await savedRow.getByRole('button', { name: '下载' }).click()
    await expect((await paperDownloadPromise).suggestedFilename()).toContain('.xlsx')
    await savedRow.getByRole('button', { name: '复制' }).click()
    await expect(page.getByText('试卷已复制')).toBeVisible()
    await page.getByRole('dialog', { name: '编辑考试' }).getByRole('button', { name: '取消' }).click()
    await savedRow.getByRole('button', { name: '撤销发布' }).click()
    await page.getByRole('dialog', { name: '撤销发布' }).getByRole('button', { name: '撤销发布' }).click()
    await expect(page.getByText('考试已撤销发布')).toBeVisible()
    await page.getByRole('row').filter({ hasText: examTitle, hasNotText: '副本' }).getByRole('button', { name: '编辑' }).click()
    const editDialog = page.getByRole('dialog', { name: '编辑考试' })
    await expect(editDialog).toBeVisible()
    await expect(editDialog.locator('.rule-item .el-select')).toContainText(CET4_BANK)
    await expect(editDialog.locator('.paper-table tbody tr')).toHaveCount(2)
    await expect(editDialog.locator('.publish-summary strong').filter({ hasText: /^草稿$/ })).toBeVisible()
    await editDialog.getByRole('button', { name: '取消' }).click()

    expect(consoleIssues).toEqual([])
  })

  test('考试草稿不会被题库编辑魔法更新，显式更新试卷后才持久化新快照', async ({ page }) => {
    const consoleIssues = collectConsoleIssues(page)
    await login(page)
    const token = await page.evaluate(() => window.localStorage.getItem('kaoshi.accessToken'))
    const headers = { Authorization: `Bearer ${token}` }
    const suffix = Date.now()
    const originalStem = `草稿快照原题干 ${suffix}`
    const changedStem = `草稿快照新题干 ${suffix}`
    const examTitle = `无魔法草稿考试 ${suffix}`

    const bankResponse = await page.request.post('/api/admin/question-banks', {
      headers,
      data: {
        categoryId: 1,
        name: `无魔法题库 ${suffix}`,
        description: 'Playwright 快照夹具',
        status: 'ACTIVE',
      },
    })
    expect(bankResponse.ok()).toBeTruthy()
    const bank = (await bankResponse.json()).data

    const questionResponse = await page.request.post('/api/admin/questions', {
      headers,
      data: {
        bankId: bank.id,
        type: 'SINGLE_CHOICE',
        stem: originalStem,
        analysis: '原解析',
        difficulty: 'EASY',
        status: 'ACTIVE',
        options: [
          { label: 'A', content: '错误', correct: false },
          { label: 'B', content: '正确', correct: true },
        ],
        attachments: [],
      },
    })
    expect(questionResponse.ok()).toBeTruthy()
    const question = (await questionResponse.json()).data

    const examResponse = await page.request.post('/api/admin/exams', {
      headers,
      data: {
        title: examTitle,
        description: '验证题库编辑不会隐式改写草稿',
        qualifyScore: 0,
        startTime: '2026-01-01T00:00:00',
        endTime: '2026-12-31T23:59:59',
        durationMinutes: 20,
        timeLimit: false,
        attemptLimit: null,
        examMode: 'STRUCTURED',
        displayMode: 'ALL',
        questionOrderMode: 'FIXED',
        openType: 'PUBLIC',
        departmentIds: [],
        rules: [
          {
            bankId: bank.id,
            singleCount: 1,
            singleScore: 5,
            multipleCount: 0,
            multipleScore: 0,
            writingCount: 0,
            writingScore: 0,
          },
        ],
        paperQuestions: [],
        materialGroups: [],
        answerCardItems: [],
      },
    })
    expect(examResponse.ok()).toBeTruthy()

    await page.locator('li.el-menu-item').filter({ hasText: /^考试管理$/ }).click()
    await page.getByPlaceholder('搜索考试名称').fill(examTitle)
    await page.getByRole('button', { name: '搜索' }).click()
    await page.getByRole('row').filter({ hasText: examTitle }).getByRole('button', { name: '编辑' }).click()
    let examDialog = page.getByRole('dialog', { name: '编辑考试' })
    await expect(examDialog.locator('.paper-table')).toContainText(originalStem)
    await examDialog.getByRole('button', { name: '取消' }).click()

    const updateQuestionResponse = await page.request.put(`/api/admin/questions/${question.id}`, {
      headers,
      data: {
        bankId: bank.id,
        type: 'SINGLE_CHOICE',
        stem: changedStem,
        analysis: '新解析',
        difficulty: 'EASY',
        status: 'ACTIVE',
        options: [
          { label: 'A', content: '正确', correct: true },
          { label: 'B', content: '错误', correct: false },
        ],
        attachments: [],
      },
    })
    expect(updateQuestionResponse.ok()).toBeTruthy()

    await page.getByRole('row').filter({ hasText: examTitle }).getByRole('button', { name: '编辑' }).click()
    examDialog = page.getByRole('dialog', { name: '编辑考试' })
    await expect(examDialog.locator('.paper-table')).toContainText(originalStem)
    await expect(examDialog.locator('.paper-table')).not.toContainText(changedStem)
    await examDialog.getByRole('button', { name: '更新试卷' }).click()
    await expect(examDialog.locator('.paper-table')).toContainText(changedStem)
    await examDialog.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText('草稿已保存')).toBeVisible()
    await page.getByRole('dialog', { name: '编辑考试' }).getByRole('button', { name: '取消' }).click()

    await page.getByRole('row').filter({ hasText: examTitle }).getByRole('button', { name: '编辑' }).click()
    examDialog = page.getByRole('dialog', { name: '编辑考试' })
    await expect(examDialog.locator('.paper-table')).toContainText(changedStem)
    await expect(examDialog.locator('.paper-table')).not.toContainText(originalStem)
    await examDialog.getByRole('button', { name: '取消' }).click()

    expect(consoleIssues).toEqual([])
  })

  test('管理端支持通过 UI 创建和发布答题卡试卷', async ({ page }) => {
    const consoleIssues = collectConsoleIssues(page)
    await login(page)

    await page.locator('li.el-menu-item').filter({ hasText: /^考试管理$/ }).click()
    await page.getByRole('button', { name: '新建考试' }).click()
    const examDialog = page.getByRole('dialog', { name: '新建考试' })
    await examDialog.getByText('答题卡试卷').click()

    await examDialog.getByRole('button', { name: '添加材料块' }).click()
    await examDialog.getByPlaceholder('材料块名称，如 听力材料').fill('听力材料')
    await examDialog.getByPlaceholder('输入外部链接或本地资源路径').fill('/local-assets/cet4/2023-03/set-1/2023-03-cet4-listening.mp3')
    await examDialog.getByRole('button', { name: '添加链接' }).click()
    await expect(examDialog.locator('.material-table')).toContainText('2023-03-cet4-listening.mp3')

    const batchCard = examDialog.locator('.batch-card')
    await setNumberInput(batchCard.locator('.el-input-number input').nth(0), '1')
    await setNumberInput(batchCard.locator('.el-input-number input').nth(1), '1')
    await setNumberInput(batchCard.locator('.el-input-number input').nth(2), '5')
    await batchCard.getByRole('button', { name: '批量生成' }).click()
    await batchCard.locator('.el-select').first().click()
    await page.getByRole('option', { name: '多选' }).click()
    await setNumberInput(batchCard.locator('.el-input-number input').nth(0), '2')
    await setNumberInput(batchCard.locator('.el-input-number input').nth(1), '2')
    await batchCard.getByRole('button', { name: '批量生成' }).click()
    await batchCard.locator('.el-select').first().click()
    await page.getByRole('option', { name: '写作' }).click()
    await setNumberInput(batchCard.locator('.el-input-number input').nth(0), '3')
    await setNumberInput(batchCard.locator('.el-input-number input').nth(1), '3')
    await setNumberInput(batchCard.locator('.el-input-number input').nth(2), '10')
    await batchCard.getByRole('button', { name: '批量生成' }).click()
    await expect(examDialog.getByText('3 题')).toBeVisible()
    await expect(examDialog.getByText('20 分')).toBeVisible()

    const examTitle = `答题卡浏览器考试 ${Date.now()}`
    await examDialog.getByLabel('考试名称').fill(examTitle)
    await examDialog.getByLabel('考试描述').fill('Playwright 创建答题卡考试')
    await examDialog.getByText('整卷一页').click()
    await examDialog.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText('草稿已保存')).toBeVisible()
    const savedDialog = page.getByRole('dialog', { name: '编辑考试' })
    await expect(savedDialog.locator('.material-table')).toContainText('2023-03-cet4-listening.mp3')
    await expect(savedDialog.getByText('3 题')).toBeVisible()
    await savedDialog.getByRole('button', { name: '发布考试' }).click()
    await expect(page.getByText('考试已发布')).toBeVisible()
    await savedDialog.getByRole('button', { name: '取消' }).click()

    await page.getByPlaceholder('搜索考试名称').fill(examTitle)
    await page.getByRole('button', { name: '搜索' }).click()
    await expect(page.getByRole('row').filter({ hasText: examTitle })).toContainText('3 题 / 20 分')

    expect(consoleIssues).toEqual([])
  })
})

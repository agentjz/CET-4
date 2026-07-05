import { expect, test, type Page } from '@playwright/test'

import { collectConsoleIssues, login } from './helpers'

const CET4_EXAM = 'CET-4 四级考试平台演示'
const ANSWER_SHEET_EXAM = '答题卡试卷演示'
const CET4_AUDIO = '2023-03-cet4-listening.mp3'

test.describe('在线考试', () => {
  function cet4ExamRow(page: Page) {
    return page.getByRole('row').filter({ hasText: CET4_EXAM })
  }

  test('考试中心支持进入 CET4 准备页并返回', async ({ page }) => {
    const consoleIssues = collectConsoleIssues(page)
    await login(page)

    await page.getByRole('menuitem', { name: '考试中心' }).click()
    await expect(page.getByRole('heading', { name: '考试中心' })).toBeVisible()
    await cet4ExamRow(page).getByRole('button', { name: '准备考试' }).click()
    await expect(page.getByRole('heading', { name: CET4_EXAM })).toBeVisible()
    await page.getByRole('button', { name: '返回考试中心' }).click()
    await expect(page.getByRole('heading', { name: '考试中心' })).toBeVisible()

    expect(consoleIssues).toEqual([])
  })

  test('在线作答支持写作保存读回、音频题作答、提交待阅卷和人工阅卷锁定', async ({ page }) => {
    const consoleIssues = collectConsoleIssues(page)
    await login(page)

    await page.getByRole('menuitem', { name: '考试中心' }).click()
    await expect(page.getByRole('heading', { name: '考试中心' })).toBeVisible()
    await cet4ExamRow(page).getByRole('button', { name: '准备考试' }).click()
    await page.getByRole('button', { name: '开始考试' }).click()
    await expect(page.getByText('答题卡')).toBeVisible()
    await expect(page.getByText('写作').first()).toBeVisible()
    await expect(page.getByText('online learning').first()).toBeVisible()

    const writingAnswer = 'This book is worth reading because it gives practical ideas in clear language and can start meaningful discussion in our book club.'
    const writingSaveResponse = page.waitForResponse((response) => {
      return response.url().includes('/api/exam/1/answers')
        && response.request().method() === 'POST'
        && Boolean(response.request().postData()?.includes('practical ideas'))
    })
    await page.getByPlaceholder('请输入答案').fill(writingAnswer)
    await page.getByPlaceholder('请输入答案').blur()
    await expect((await writingSaveResponse).ok()).toBe(true)

    page.once('dialog', (dialog) => {
      void dialog.accept()
    })
    await page.reload()
    await expect(page.getByText('答题卡')).toBeVisible()
    await expect(page.getByPlaceholder('请输入答案')).toHaveValue(writingAnswer)

    await page.locator('.answer-card__item').filter({ hasText: /^1$/ }).click()
    await expect(page.locator(`audio[src$="${CET4_AUDIO}"]`).first()).toBeVisible()
    await page.getByText('A. True').click()
    await expect(page.getByText('答案已保存')).toBeVisible()
    await page.getByText('A. necessary').click()
    await page.getByText('A. Reviewing notes').click()
    await page.getByText('B. Planning study time').click()
    await page.getByText('D. Practicing with past papers').click()
    await expect(page.getByText('答案已保存')).toBeVisible()

    await page.locator('.exam-actions').getByRole('button', { name: '提交试卷' }).click()
    await page.getByRole('button', { name: '提交', exact: true }).click()

    await expect(page.getByText('本次考试已经提交，答案已锁定。')).toBeVisible()
    await expect(page.getByRole('heading', { name: CET4_EXAM })).toBeVisible()
    await expect(page.getByText('阅卷状态').locator('..')).toContainText('待阅卷')
    await expect(page.getByText(writingAnswer)).toBeVisible()
    await expect(page.getByText('正确答案：A').first()).toBeVisible()

    await page.getByRole('menuitem', { name: '我的成绩' }).click()
    await expect(page.getByRole('heading', { name: '我的成绩' })).toBeVisible()
    await page.getByRole('button', { name: '查看详情' }).first().click()
    await expect(page.getByText(writingAnswer)).toBeVisible()

    await page.locator('li.el-menu-item').filter({ hasText: /^考试管理$/ }).click()
    await expect(page.locator('.admin-page__header h1', { hasText: '考试管理' })).toBeVisible()
    await page.getByPlaceholder('搜索考试名称').fill(CET4_EXAM)
    await page.getByRole('button', { name: '搜索' }).click()
    await cet4ExamRow(page).getByRole('button', { name: '成绩' }).click()
    const resultDrawer = page.getByRole('dialog', { name: new RegExp(`${CET4_EXAM} - 成绩`) })
    await expect(resultDrawer).toBeVisible()
    await expect(resultDrawer.getByText('待阅卷').first()).toBeVisible()
    await resultDrawer.getByRole('button', { name: '查看详情' }).first().click()
    await expect(page.getByRole('heading', { name: CET4_EXAM })).toBeVisible()
    await expect(page.getByText(writingAnswer)).toBeVisible()

    const resultId = Number(page.url().split('/').pop())
    expect(Number.isFinite(resultId)).toBe(true)
    const firstReviewForm = page.locator('.writing-review-form').first()
    await firstReviewForm.locator('.el-input-number input').fill('1')
    await firstReviewForm.getByPlaceholder('阅卷评语').fill('CET4 主观题评分 1')
    const firstReviewResponse = page.waitForResponse((response) => {
      return response.url().includes(`/api/admin/results/${resultId}/questions/`)
        && response.request().method() === 'POST'
    })
    await firstReviewForm.getByRole('button', { name: '保存评分' }).click()
    await expect((await firstReviewResponse).ok()).toBe(true)
    await expect(page.getByText('评分已保存').last()).toBeVisible()

    await page.reload()
    await expect(page.getByText('CET4 主观题评分 1')).toBeVisible()
    const token = await page.evaluate(() => window.localStorage.getItem('kaoshi.accessToken'))
    const headers = { Authorization: `Bearer ${token}` }
    const detailResponse = await page.request.get(`/api/admin/results/${resultId}`, { headers })
    expect(detailResponse.ok()).toBeTruthy()
    const resultDetail = (await detailResponse.json()).data
    for (const question of resultDetail.questions.filter((item: { type: string; reviewedAt: string | null }) => item.type === 'WRITING' && !item.reviewedAt)) {
      const response = await page.request.post(`/api/admin/results/${resultId}/questions/${question.questionId}/review`, {
        headers,
        data: {
          score: 1,
          comment: `CET4 主观题评分 ${question.questionId}`,
        },
      })
      expect(response.ok()).toBeTruthy()
    }
    await page.reload()
    await expect(page.getByRole('button', { name: '完成阅卷' })).toBeEnabled()
    await page.getByRole('button', { name: '完成阅卷' }).click()
    await expect(page.getByText('阅卷已完成')).toBeVisible()
    await expect(page.getByText('阅卷状态').locator('..')).toContainText('已出分')
    await expect(page.getByRole('button', { name: '保存评分' })).toHaveCount(0)

    expect(consoleIssues).toEqual([])
  })

  test('答题卡试卷支持材料查看、题号作答、提交和人工阅卷保存', async ({ page }) => {
    const consoleIssues = collectConsoleIssues(page)
    await login(page)

    await page.getByRole('menuitem', { name: '考试中心' }).click()
    await page.getByRole('row').filter({ hasText: ANSWER_SHEET_EXAM }).getByRole('button', { name: '准备考试' }).click()
    await page.getByRole('button', { name: '开始考试' }).click()
    await expect(page.getByRole('heading', { name: ANSWER_SHEET_EXAM })).toBeVisible()
    await expect(page.getByText('听力材料')).toBeVisible()
    await expect(page.locator(`audio[src$="${CET4_AUDIO}"]`).first()).toBeVisible()
    await expect(page.getByRole('heading', { name: '阅读材料' })).toBeVisible()

    const answerSheetQuestions = page.locator('.answer-sheet-question')
    await answerSheetQuestions.nth(0).getByText('A', { exact: true }).click()
    await answerSheetQuestions.nth(1).getByText('A', { exact: true }).click()
    await answerSheetQuestions.nth(1).getByText('C', { exact: true }).click()
    const writingAnswer = 'This answer sheet writing response is saved and reviewed in the browser flow.'
    await answerSheetQuestions.nth(2).getByPlaceholder('请输入答案').fill(writingAnswer)
    await expect(page.getByText('答案已保存')).toBeVisible()

    await page.locator('.exam-actions').getByRole('button', { name: '提交试卷' }).click()
    await page.getByRole('button', { name: '提交', exact: true }).click()
    await expect(page.getByRole('heading', { name: ANSWER_SHEET_EXAM })).toBeVisible()
    await expect(page.getByText('阅卷状态').locator('..')).toContainText('待阅卷')
    await expect(page.getByText(writingAnswer)).toBeVisible()

    await page.locator('li.el-menu-item').filter({ hasText: /^考试管理$/ }).click()
    await page.getByPlaceholder('搜索考试名称').fill(ANSWER_SHEET_EXAM)
    await page.getByRole('button', { name: '搜索' }).click()
    await page.getByRole('row').filter({ hasText: ANSWER_SHEET_EXAM }).getByRole('button', { name: '成绩' }).click()
    await page.getByRole('dialog', { name: new RegExp(`${ANSWER_SHEET_EXAM} - 成绩`) }).getByRole('button', { name: '查看详情' }).first().click()
    const reviewForm = page.locator('.writing-review-form').first()
    await reviewForm.locator('.el-input-number input').fill('8')
    await reviewForm.getByPlaceholder('阅卷评语').fill('答题卡写作评分')
    await reviewForm.getByRole('button', { name: '保存评分' }).click()
    await expect(page.getByText('评分已保存')).toBeVisible()

    expect(consoleIssues).toEqual([])
  })
})

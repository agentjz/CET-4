import { seedExamBusiness } from './demo-exam-seed'
import { seedIdentity } from './demo-identity-seed'
import type { DemoState } from './demo-model'

export function seedState(): DemoState {
  const state: DemoState = {
    currentUserId: null,
    departments: [
      { id: 1, parentId: null, name: '默认组织', code: 'ROOT', description: '演示组织根节点', status: 'ACTIVE', children: [] },
      { id: 2, parentId: 1, name: '教学部', code: 'TEACHING', description: '演示教学部门', status: 'ACTIVE', children: [] },
    ],
    users: [],
    roles: [],
    permissions: [],
    menus: [],
    categories: [{ id: 101, code: 'cet4-demo', name: '四级样例', description: '大学英语四级演示题库分类', sortOrder: 10 }],
    banks: [],
    questions: [],
    correctLabelsByQuestionId: {},
    exams: [],
    attempts: [],
    results: [],
    nextId: 1000,
  }
  state.departments[0].children = [state.departments[1]]
  seedIdentity(state)
  seedExamBusiness(state)
  return state
}

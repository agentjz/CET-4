import type {
  AdminMenu,
  AdminPermission,
  AdminRole,
} from '../../admin'
import { nowIso, type DemoState, type DemoUser } from './demo-model'

export function seedIdentity(state: DemoState) {
  state.permissions = buildPermissions()
  state.menus = buildMenus()
  state.roles = buildRoles(state.permissions, state.menus)
  state.users = buildUsers()
}

function buildPermissions(): AdminPermission[] {
  const codes = [
    ['admin:users', '用户管理'],
    ['admin:roles', '角色管理'],
    ['admin:departments', '部门管理'],
    ['exam:questions', '题库管理'],
    ['exam:manage', '考试管理'],
    ['exam:review', '成绩阅卷'],
    ['exam:take', '在线考试'],
  ]
  return codes.map(([code, name], index) => ({ id: index + 1, code, name, description: `${name}权限` }))
}

function buildMenus(): AdminMenu[] {
  return [
    { id: 1, code: 'online-exam', title: '在线考试', path: '/my/exam', parentId: null, sortOrder: 10, icon: null },
    { id: 2, code: 'exam-management', title: '考试管理', path: '/exam/manage', parentId: null, sortOrder: 20, icon: null },
    { id: 3, code: 'system-management', title: '系统管理', path: '/sys/roles', parentId: null, sortOrder: 30, icon: null },
  ]
}

function buildRoles(permissions: AdminPermission[], menus: AdminMenu[]): AdminRole[] {
  return [
    { id: 1, code: 'ADMIN', name: '系统管理员', description: '拥有演示环境全部权限', permissions, menus },
    { id: 2, code: 'STUDENT', name: '考生', description: '参加考试并查看成绩', permissions: permissions.filter((item) => item.code === 'exam:take'), menus: menus.slice(0, 1) },
  ]
}

function buildUsers(): DemoUser[] {
  const timestamp = nowIso()
  return [
    { id: 1, departmentId: 1, departmentName: '默认组织', username: 'admin', displayName: '系统管理员', status: 'ACTIVE', roles: ['ADMIN'], roleIds: [1], password: 'password', createdAt: timestamp, updatedAt: timestamp },
    { id: 2, departmentId: 2, departmentName: '教学部', username: 'zhangsan', displayName: '张三', status: 'ACTIVE', roles: ['STUDENT'], roleIds: [2], password: 'password', createdAt: timestamp, updatedAt: timestamp },
  ]
}

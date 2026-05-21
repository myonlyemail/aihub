import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AdminLoginVO } from '@/types'
import { loginApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('admin_token') || '')
  const adminInfo = ref<AdminLoginVO | null>(
    JSON.parse(localStorage.getItem('admin_info') || 'null')
  )
  const permissions = ref<string[]>(adminInfo.value?.permissions || [])
  const roles = ref<string[]>(adminInfo.value?.roles || [])

  async function login(username: string, password: string) {
    const res = await loginApi({ username, password })
    const data = res.data
    token.value = data.token
    adminInfo.value = data
    permissions.value = data.permissions || []
    roles.value = data.roles || []
    localStorage.setItem('admin_token', data.token)
    localStorage.setItem('admin_info', JSON.stringify(data))
    return data
  }

  function logout() {
    token.value = ''
    adminInfo.value = null
    permissions.value = []
    roles.value = []
    localStorage.removeItem('admin_token')
    localStorage.removeItem('admin_info')
  }

  function hasPermission(perm: string): boolean {
    return permissions.value.includes(perm) || roles.value.includes('SUPER_ADMIN')
  }

  return { token, adminInfo, permissions, roles, login, logout, hasPermission }
})

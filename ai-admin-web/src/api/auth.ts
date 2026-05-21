import api from './index'
import type { LoginRequest } from '@/types'

export function loginApi(data: LoginRequest) {
  return api.post('/admin/login', data)
}

export function getAdminInfoApi() {
  return api.get('/admin/info')
}

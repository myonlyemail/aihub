import api from './index'

export function getUsersApi(params: any) {
  return api.get('/admin/users', { params })
}

export function updateUserStatusApi(userId: number, status: number) {
  return api.put(`/admin/users/${userId}/status`, { status })
}

export function updateUserTokensApi(userId: number, amount: number) {
  return api.put(`/admin/users/${userId}/tokens`, { amount })
}

export function getDashboardApi() {
  return api.get('/admin/dashboard')
}

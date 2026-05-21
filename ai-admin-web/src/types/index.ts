export interface LoginRequest {
  username: string
  password: string
}

export interface AdminLoginVO {
  adminId: number
  username: string
  nickname: string
  token: string
  roles: string[]
  permissions: string[]
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  tokenBalance: number
  vipLevel: number
  vipExpireTime: string
  createTime: string
}

export interface ChatSession {
  id: number
  userId: number
  title: string
  model: string
  messageCount: number
  createTime: string
  updateTime: string
}

export interface ImageTask {
  id: number
  userId: number
  prompt: string
  model: string
  status: number
  resultUrl: string
  tokenCost: number
  createTime: string
}

export interface VideoTask {
  id: number
  userId: number
  title: string
  prompt: string
  model: string
  duration: number
  status: number
  resultUrl: string
  tokenCost: number
  createTime: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

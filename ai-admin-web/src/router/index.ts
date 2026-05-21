import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layout/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '仪表盘', icon: 'Odometer' }
        },
        {
          path: 'users',
          name: 'Users',
          component: () => import('@/views/users/UserListView.vue'),
          meta: { title: '用户管理', icon: 'User', perm: 'user' }
        },
        {
          path: 'chat',
          name: 'Chat',
          component: () => import('@/views/chat/ChatListView.vue'),
          meta: { title: 'AI聊天', icon: 'ChatDotRound' }
        },
        {
          path: 'image',
          name: 'Image',
          component: () => import('@/views/image/ImageListView.vue'),
          meta: { title: 'AI图片', icon: 'Picture' }
        },
        {
          path: 'video',
          name: 'Video',
          component: () => import('@/views/video/VideoListView.vue'),
          meta: { title: 'AI视频', icon: 'VideoCamera' }
        },
        {
          path: 'settings',
          name: 'Settings',
          component: () => import('@/views/settings/SettingsView.vue'),
          meta: { title: '系统设置', icon: 'Setting', perm: 'settings' }
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('admin_token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else {
    next()
  }
})

export default router

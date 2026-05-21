<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const isCollapse = ref(false)

const menuItems = [
  { path: '/dashboard', title: '仪表盘', icon: 'Odometer' },
  { path: '/users', title: '用户管理', icon: 'User', perm: 'user' },
  { path: '/chat', title: 'AI聊天', icon: 'ChatDotRound' },
  { path: '/image', title: 'AI图片', icon: 'Picture' },
  { path: '/video', title: 'AI视频', icon: 'VideoCamera' },
  { path: '/settings', title: '系统设置', icon: 'Setting', perm: 'settings' }
]

const visibleMenu = computed(() =>
  menuItems.filter(item => !item.perm || auth.hasPermission(item.perm))
)

const activeMenu = computed(() => route.path)

function handleSelect(path: string) {
  router.push(path)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="admin-layout">
    <el-container>
      <el-aside :width="isCollapse ? '64px' : '220px'" class="aside">
        <div class="logo">
          <el-icon :size="28" color="#409EFF"><Platform /></el-icon>
          <span v-show="!isCollapse" class="logo-text">AIHub Admin</span>
        </div>
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409EFF"
          @select="handleSelect"
        >
          <el-menu-item v-for="item in visibleMenu" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn" @click="isCollapse = !isCollapse" :size="22">
              <Fold v-if="!isCollapse" /><Expand v-else />
            </el-icon>
          </div>
          <div class="header-right">
            <el-tag v-for="role in auth.roles" :key="role" size="small" style="margin-right: 8px">
              {{ role }}
            </el-tag>
            <span style="margin-right: 16px">{{ auth.adminInfo?.nickname || auth.adminInfo?.username }}</span>
            <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
          </div>
        </el-header>

        <el-main>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.admin-layout, .el-container {
  height: 100%;
}
.aside {
  background-color: #304156;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-text {
  white-space: nowrap;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;
}
.header-left {
  display: flex;
  align-items: center;
}
.collapse-btn {
  cursor: pointer;
}
.header-right {
  display: flex;
  align-items: center;
}
.el-menu {
  border-right: none;
}
.el-main {
  background: #f0f2f5;
  min-height: calc(100vh - 60px);
}
</style>

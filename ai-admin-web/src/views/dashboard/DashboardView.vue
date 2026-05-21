<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDashboardApi } from '@/api/admin'

const stats = ref([
  { label: '总用户数', value: 0, icon: 'User', color: '#409EFF' },
  { label: '今日活跃', value: 0, icon: 'TrendCharts', color: '#67C23A' },
  { label: 'Token消耗', value: 0, icon: 'Coin', color: '#E6A23C' },
  { label: '总收入', value: '¥0', icon: 'Money', color: '#F56C6C' }
])

onMounted(async () => {
  try {
    const res = await getDashboardApi()
    if (res.data) {
      stats.value[0].value = res.data.userCount || 0
      stats.value[1].value = res.data.todayActive || 0
      stats.value[2].value = res.data.tokenUsage || 0
      stats.value[3].value = '¥' + (res.data.totalRevenue || 0)
    }
  } catch {
    // use defaults
  }
})
</script>

<template>
  <div>
    <h3 style="margin-bottom: 20px">仪表盘</h3>
    <el-row :gutter="20">
      <el-col :span="6" v-for="s in stats" :key="s.label">
        <el-card shadow="hover" style="margin-bottom: 20px">
          <div style="display: flex; align-items: center; gap: 16px">
            <el-icon :size="36" :color="s.color"><component :is="s.icon" /></el-icon>
            <div>
              <div style="color: #909399; font-size: 14px">{{ s.label }}</div>
              <div style="font-size: 24px; font-weight: bold">{{ s.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="14">
        <el-card>
          <template #header>系统概览</template>
          <div style="padding: 20px 0">
            <p><strong>平台名称：</strong>AIHub 智能体聚合平台</p>
            <p><strong>版本：</strong>v1.0.0 MVP</p>
            <p><strong>运行状态：</strong><el-tag type="success" size="small">正常运行</el-tag></p>
            <p><strong>微服务数量：</strong>8 个服务 + 1 个网关</p>
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>快捷操作</template>
          <div style="display: flex; flex-direction: column; gap: 12px">
            <el-button type="primary" @click="$router.push('/users')">用户管理</el-button>
            <el-button type="success" @click="$router.push('/chat')">查看聊天记录</el-button>
            <el-button type="warning" @click="$router.push('/image')">查看图片任务</el-button>
            <el-button type="info" @click="$router.push('/settings')">系统设置</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

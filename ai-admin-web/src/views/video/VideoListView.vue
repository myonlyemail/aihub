<script setup lang="ts">
import { ref, onMounted } from 'vue'
import api from '@/api'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

function getStatusTag(status: number) {
  const map: Record<number, { type: string; text: string }> = {
    0: { type: 'info', text: '等待中' },
    1: { type: 'warning', text: '生成中' },
    2: { type: 'success', text: '已完成' },
    3: { type: 'danger', text: '失败' }
  }
  return map[status] || { type: 'info', text: '未知' }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await api.get('/admin/video/tasks', {
      params: { page: page.value, pageSize: pageSize.value }
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handleSizeChange(size: number) {
  pageSize.value = size
  fetchList()
}

function handlePageChange(p: number) {
  page.value = p
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div>
    <el-card>
      <template #header><span>AI 视频任务</span></template>
      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="userId" label="用户ID" width="80" />
        <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
        <el-table-column prop="prompt" label="提示词" min-width="200" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" width="100" />
        <el-table-column prop="duration" label="时长(秒)" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status).type" size="small">
              {{ getStatusTag(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tokenCost" label="Token消耗" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="结果" width="100">
          <template #default="{ row }">
            <el-button v-if="row.resultUrl" size="small" link type="primary" @click="window.open(row.resultUrl)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; text-align: right">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

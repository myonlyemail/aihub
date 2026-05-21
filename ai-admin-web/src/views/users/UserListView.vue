<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsersApi, updateUserStatusApi, updateUserTokensApi } from '@/api/admin'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const searchKeyword = ref('')

async function fetchUsers() {
  loading.value = true
  try {
    const res = await getUsersApi({
      page: page.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value
    })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    // handled
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchUsers()
}

async function toggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1
  const action = newStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}用户 ${row.nickname || row.username}？`, '提示')
    await updateUserStatusApi(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    fetchUsers()
  } catch {
    // cancelled
  }
}

async function handleTokens(row: any) {
  try {
    const { value } = await ElMessageBox.prompt('请输入要充值的Token数量', 'Token充值', {
      inputType: 'number',
      inputValue: '100'
    })
    if (value) {
      await updateUserTokensApi(row.id, parseInt(value))
      ElMessage.success('充值成功')
      fetchUsers()
    }
  } catch {
    // cancelled
  }
}

function handleSizeChange(size: number) {
  pageSize.value = size
  fetchUsers()
}

function handlePageChange(p: number) {
  page.value = p
  fetchUsers()
}

onMounted(fetchUsers)
</script>

<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>用户管理</span>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索用户名/手机号"
            style="width: 240px"
            clearable
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch">搜索</el-button>
            </template>
          </el-input>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="tokenBalance" label="Token余额" width="100" />
        <el-table-column label="VIP等级" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.vipLevel > 0" type="warning" size="small">VIP{{ row.vipLevel }}</el-tag>
            <span v-else style="color: #909399">普通用户</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleTokens(row)">充值</el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'danger' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
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

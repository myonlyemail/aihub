<script setup lang="ts">
</script>

<template>
  <div>
    <el-card>
      <template #header><span>系统设置</span></template>

      <el-tabs>
        <el-tab-pane label="基础设置">
          <el-form label-width="120px" style="max-width: 600px">
            <el-form-item label="站点名称">
              <el-input value="AIHub 智能体聚合平台" />
            </el-form-item>
            <el-form-item label="管理员邮箱">
              <el-input value="admin@aihub.com" />
            </el-form-item>
            <el-form-item label="用户注册">
              <el-switch :model-value="true" />
            </el-form-item>
            <el-form-item label="注册赠送Token">
              <el-input-number :model-value="100" :min="0" :step="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary">保存设置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="API密钥配置">
          <el-form label-width="140px" style="max-width: 600px">
            <el-form-item label="OpenAI API Key">
              <el-input type="password" show-password placeholder="sk-..." />
            </el-form-item>
            <el-form-item label="Claude API Key">
              <el-input type="password" show-password placeholder="sk-ant-..." />
            </el-form-item>
            <el-form-item label="Runway API Key">
              <el-input type="password" show-password placeholder="key_..." />
            </el-form-item>
            <el-form-item label="可灵 Access Key">
              <el-input type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary">保存配置</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="角色权限">
          <el-table :data="[
            { role: 'SUPER_ADMIN', desc: '超级管理员', perms: '全部权限' },
            { role: 'ADMIN', desc: '管理员', perms: '用户管理、内容管理' },
            { role: 'OPERATOR', desc: '运营', perms: '内容查看、数据查看' }
          ]" border stripe>
            <el-table-column prop="role" label="角色编码" width="150" />
            <el-table-column prop="desc" label="描述" width="150" />
            <el-table-column prop="perms" label="权限范围" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

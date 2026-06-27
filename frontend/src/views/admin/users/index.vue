<template>
  <div class="users-page">
    <el-card>
      <template #header>
      <div class="card-header">
        <span>用户管理</span>
      </div>
    </template>
      <el-form :inline="true" :model="queryParams" class="search-form">
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable>
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="queryParams.keyword"
            placeholder="用户名/邮箱"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="role" label="角色">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'primary'">
              {{ row.role === 'admin' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="handleToggleStatus(row)"
            >
              <el-icon><Switch /></el-icon>
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="handleResetPassword(row)">
              <el-icon><Lock /></el-icon>
              重置密码
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
        class="pagination"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'edit' ? '编辑用户' : '重置密码'"
      width="500px"
    >
      <el-form
        v-if="dialogType === 'edit'"
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-width="100px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="editForm.role">
            <el-option label="用户" value="user" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-form
        v-else
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDialogConfirm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Refresh, Edit, Switch, Lock } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUserList,
  updateUser,
  updateUserStatus,
  resetUserPassword
} from '@/api/admin'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const queryParams = reactive({
  page: 1,
  size: 10,
  status: null,
  keyword: ''
})

const dialogVisible = ref(false)
const dialogType = ref('edit')
const currentUser = ref(null)
const editFormRef = ref(null)
const passwordFormRef = ref(null)

const editForm = reactive({
  username: '',
  phone: '',
  role: 'user'
})

const passwordForm = reactive({
  newPassword: ''
})

const editRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

const passwordRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ]
}

onMounted(() => {
  handleSearch()
})

async function handleSearch() {
  loading.value = true
  try {
    const res = await getUserList(queryParams)
    tableData.value = res.data.list
    total.value = res.data.total
  } catch (err) {
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryParams.page = 1
  queryParams.status = null
  queryParams.keyword = ''
  handleSearch()
}

function handleEdit(row) {
  currentUser.value = row
  dialogType.value = 'edit'
  editForm.username = row.username
  editForm.phone = row.phone
  editForm.role = row.role
  dialogVisible.value = true
}

function handleResetPassword(row) {
  currentUser.value = row
  dialogType.value = 'password'
  passwordForm.newPassword = ''
  dialogVisible.value = true
}

async function handleToggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '启用成功' : '禁用成功')
    handleSearch()
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

async function handleDialogConfirm() {
  if (dialogType.value === 'edit') {
    await editFormRef.value?.validate()
    try {
      await updateUser(currentUser.value.id, editForm)
      ElMessage.success('更新成功')
      dialogVisible.value = false
      handleSearch()
    } catch (err) {
      ElMessage.error('更新失败')
    }
  } else {
    await passwordFormRef.value?.validate()
    try {
      await resetUserPassword(currentUser.value.id, passwordForm.newPassword)
      ElMessage.success('密码重置成功')
      dialogVisible.value = false
    } catch (err) {
      ElMessage.error('密码重置失败')
    }
  }
}
</script>

<style lang="scss" scoped>
.users-page {
  padding: 20px;

  .card-header {
    font-weight: bold;
    font-size: 16px;
  }

  .search-form {
    margin-bottom: 20px;

    .el-select {
      min-width: 180px;
    }

    .el-input {
      min-width: 200px;
    }
  }

  .pagination {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>

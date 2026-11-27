<template>
  <div class="oidc-manage-root">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <h2 class="panel-title">OIDC 提供者列表</h2>
        <span class="record-count">共 {{ pagination.total }} 条</span>
      </div>
      <div class="toolbar-right">
        <button class="btn btn-ghost" @click="handleRefresh" :disabled="loading">
          <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z" clip-rule="evenodd" />
          </svg>
          刷新
        </button>
        <button class="btn btn-primary" @click="handleAdd">
          <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd" />
          </svg>
          添加 OIDC 提供者
        </button>
      </div>
    </div>

    <!-- 表格容器 -->
    <div class="table-card">
      <a-table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="false"
        row-key="provider_id"
        :scroll="{ x: '100%', y: 'calc(100vh - 320px)' }"
      >
        <template #bodyCell="{ column, record }">
          <!-- 提供者ID -->
          <template v-if="column.key === 'provider_id'">
            <span class="text-white">{{ record.provider_id }}</span>
          </template>

          <!-- 密钥状态 -->
          <template v-if="column.key === 'has_secret'">
            <span :class="['type-badge', record.has_secret ? 'type-badge--web' : 'type-badge--pilot']">
              {{ record.has_secret ? '已配置' : '未配置' }}
            </span>
          </template>

          <!-- 作用域 -->
          <template v-if="column.key === 'scopes'">
            <div class="scope-tags">
              <span v-for="scope in record.scopes.split(' ').slice(0, 2)" :key="scope" class="scope-tag">
                {{ scope }}
              </span>
              <span v-if="record.scopes.split(' ').length > 2" class="scope-tag">
                +{{ record.scopes.split(' ').length - 2 }}
              </span>
            </div>
          </template>

          <!-- State 有效期 -->
          <template v-if="column.key === 'state_ttl'">
            <span class="text-muted">{{ record.state_ttl }}秒</span>
          </template>

          <!-- 启用状态 -->
          <template v-if="column.key === 'enable'">
            <label class="switch">
              <input
                type="checkbox"
                :checked="record.enable"
                @change="handleToggleStatus(record, ($event.target as HTMLInputElement).checked)"
              >
              <span class="switch-slider"></span>
            </label>
          </template>

          <!-- 创建时间 -->
          <template v-if="column.key === 'create_time'">
            <span class="text-muted">{{ formatTime(record.create_time) }}</span>
          </template>

          <!-- 操作 -->
          <template v-if="column.key === 'action'">
            <div class="action-btns">
              <button class="action-btn action-btn--enable" @click="handleView(record)">查看</button>
              <button class="action-btn action-btn--enable" @click="handleEdit(record)">编辑</button>
              <button class="action-btn action-btn--enable" @click="handleTest(record)">测试</button>
              <button class="action-btn action-btn--delete" @click="confirmDelete(record)">删除</button>
            </div>
          </template>
        </template>
      </a-table>

      <div class="pagination-bar">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :show-total="(total: number) => `共 ${total} 条记录`"
          show-quick-jumper
          @change="handlePageChange"
          @showSizeChange="handlePageChange"
        />
      </div>
    </div>

    <!-- 添加/编辑弹窗 -->
    <div v-if="modalVisible" class="modal-overlay" @click.self="handleModalCancel">
      <div class="modal-box modal-box--large">
        <div class="modal-header">
          <h3 class="modal-title">{{ modalTitle }}</h3>
          <button class="modal-close" @click="handleModalCancel">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <form class="form-grid">
            <!-- Provider ID -->
            <div class="form-group">
              <label class="form-label">
                提供者ID <span class="required">*</span>
              </label>
              <input
                v-model="formData.provider_id"
                type="text"
                class="form-input"
                placeholder="如: oidc-keycloak"
                :disabled="isEdit"
              />
              <span class="form-hint">唯一标识，只能包含字母、数字、下划线和横线</span>
            </div>

            <!-- Client ID -->
            <div class="form-group">
              <label class="form-label">
                客户端ID <span class="required">*</span>
              </label>
              <input
                v-model="formData.client_id"
                type="text"
                class="form-input"
                placeholder="OAuth2 客户端ID"
              />
            </div>

            <!-- Client Secret -->
            <div class="form-group form-group--full">
              <label class="form-label">
                客户端密钥 {{ isEdit ? '(留空则不修改)' : '*' }}
              </label>
              <input
                v-model="formData.client_secret"
                type="password"
                class="form-input"
                placeholder="OAuth2 客户端密钥"
              />
            </div>

            <!-- Issuer -->
            <div class="form-group form-group--full">
              <label class="form-label">
                Issuer URL <span class="required">*</span>
              </label>
              <input
                v-model="formData.issuer"
                type="text"
                class="form-input"
                placeholder="https://your-auth-server.com"
              />
            </div>

            <!-- Authorization URI -->
            <div class="form-group form-group--full">
              <label class="form-label">
                授权端点 <span class="required">*</span>
              </label>
              <input
                v-model="formData.authorization_uri"
                type="text"
                class="form-input"
                placeholder="https://your-auth-server.com/oauth2/authorize"
              />
            </div>

            <!-- Token URI -->
            <div class="form-group form-group--full">
              <label class="form-label">
                Token 端点 <span class="required">*</span>
              </label>
              <input
                v-model="formData.token_uri"
                type="text"
                class="form-input"
                placeholder="https://your-auth-server.com/oauth2/token"
              />
            </div>

            <!-- Redirect URI -->
            <div class="form-group form-group--full">
              <label class="form-label">
                回调地址 <span class="required">*</span>
              </label>
              <input
                v-model="formData.redirect_uri"
                type="text"
                class="form-input"
                placeholder="http://your-app.com/callback"
              />
            </div>

            <!-- JWK Set URI -->
            <div class="form-group form-group--full">
              <label class="form-label">
                JWK Set URI <span class="required">*</span>
              </label>
              <input
                v-model="formData.jwk_set_uri"
                type="text"
                class="form-input"
                placeholder="https://your-auth-server.com/oauth2/jwks"
              />
            </div>

            <!-- Scopes -->
            <div class="form-group">
              <label class="form-label">
                OAuth2 作用域 <span class="required">*</span>
              </label>
              <input
                v-model="formData.scopes"
                type="text"
                class="form-input"
                placeholder="openid profile email"
              />
              <span class="form-hint">空格分隔多个作用域</span>
            </div>

            <!-- Username Claim -->
            <div class="form-group">
              <label class="form-label">
                用户名声明字段 <span class="required">*</span>
              </label>
              <input
                v-model="formData.username_claim"
                type="text"
                class="form-input"
                placeholder="preferred_username"
              />
            </div>

            <!-- State TTL -->
            <div class="form-group">
              <label class="form-label">
                State 有效期(秒) <span class="required">*</span>
              </label>
              <input
                v-model.number="formData.state_ttl"
                type="number"
                min="60"
                max="3600"
                class="form-input"
                placeholder="1800"
              />
            </div>

            <!-- Enable -->
            <div class="form-group">
              <label class="form-label">启用状态</label>
              <label class="switch">
                <input type="checkbox" v-model="formData.enable">
                <span class="switch-slider"></span>
                <span class="switch-label">{{ formData.enable ? '已启用' : '已禁用' }}</span>
              </label>
            </div>
          </form>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="handleModalCancel">取消</button>
          <button class="btn btn-primary" @click="handleModalOk" :disabled="modalLoading">
            <span v-if="modalLoading" class="spinner"></span>
            确定
          </button>
        </div>
      </div>
    </div>

    <!-- 查看详情弹窗 -->
    <div v-if="viewModalVisible" class="modal-overlay" @click.self="viewModalVisible = false">
      <div class="modal-box modal-box--large">
        <div class="modal-header">
          <h3 class="modal-title">OIDC 提供者详情</h3>
          <button class="modal-close" @click="viewModalVisible = false">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
            </svg>
          </button>
        </div>
        <div class="modal-body" v-if="currentRecord">
          <div class="detail-grid">
            <div class="detail-item">
              <span class="detail-label">提供者ID</span>
              <span class="detail-value">{{ currentRecord.provider_id }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">客户端ID</span>
              <span class="detail-value">{{ currentRecord.client_id }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">客户端密钥</span>
              <span :class="['type-badge', currentRecord.has_secret ? 'type-badge--web' : 'type-badge--pilot']">
                {{ currentRecord.has_secret ? '已配置' : '未配置' }}
              </span>
            </div>
            <div class="detail-item">
              <span class="detail-label">启用状态</span>
              <span class="status-dot"
                :style="{ '--dot-color': currentRecord.enable ? '#34d399' : '#f87171', '--dot-bg': currentRecord.enable ? 'rgba(52, 211, 153, 0.12)' : 'rgba(248, 113, 113, 0.12)' }">
                {{ currentRecord.enable ? '已启用' : '已禁用' }}
              </span>
            </div>
            <div class="detail-item detail-item--full">
              <span class="detail-label">Issuer</span>
              <span class="detail-value">{{ currentRecord.issuer }}</span>
            </div>
            <div class="detail-item detail-item--full">
              <span class="detail-label">授权端点</span>
              <span class="detail-value">{{ currentRecord.authorization_uri }}</span>
            </div>
            <div class="detail-item detail-item--full">
              <span class="detail-label">Token 端点</span>
              <span class="detail-value">{{ currentRecord.token_uri }}</span>
            </div>
            <div class="detail-item detail-item--full">
              <span class="detail-label">回调地址</span>
              <span class="detail-value">{{ currentRecord.redirect_uri }}</span>
            </div>
            <div class="detail-item detail-item--full">
              <span class="detail-label">JWK Set URI</span>
              <span class="detail-value">{{ currentRecord.jwk_set_uri }}</span>
            </div>
            <div class="detail-item detail-item--full">
              <span class="detail-label">OAuth2 作用域</span>
              <div class="scope-tags">
                <span v-for="scope in currentRecord.scopes.split(' ')" :key="scope" class="scope-tag">
                  {{ scope }}
                </span>
              </div>
            </div>
            <div class="detail-item">
              <span class="detail-label">用户名声明</span>
              <span class="detail-value">{{ currentRecord.username_claim }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">State 有效期</span>
              <span class="detail-value">{{ currentRecord.state_ttl }} 秒</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">创建时间</span>
              <span class="detail-value">{{ formatTime(currentRecord.create_time) }}</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">更新时间</span>
              <span class="detail-value">{{ formatTime(currentRecord.update_time) }}</span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="viewModalVisible = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="deleteModalVisible" class="modal-overlay" @click.self="deleteModalVisible = false">
      <div class="modal-box">
        <div class="modal-header">
          <h3 class="modal-title">确认删除</h3>
          <button class="modal-close" @click="deleteModalVisible = false">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <p class="confirm-text">确定要删除 OIDC 提供者 <strong>{{ deleteTarget?.provider_id }}</strong> 吗？此操作不可恢复。</p>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="deleteModalVisible = false">取消</button>
          <button class="btn btn-primary" style="background: #f87171;" @click="handleDelete" :disabled="deleteLoading">
            <span v-if="deleteLoading" class="spinner"></span>
            确定删除
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import type { OidcProvider, OidcProviderFormData } from '@/types/oidc'
import {
  getOidcProviderList,
  createOidcProvider,
  updateOidcProvider,
  deleteOidcProvider,
  toggleOidcProviderStatus,
  testOidcConnection
} from '@/api/oidc'

// 表格列配置
const columns = [
  {
    title: '提供者ID',
    dataIndex: 'provider_id',
    key: 'provider_id',
    className: 'titleStyle',
  },
  {
    title: '客户端ID',
    dataIndex: 'client_id',
    key: 'client_id',
    className: 'titleStyle',
  },
  {
    title: '密钥状态',
    key: 'has_secret',
    className: 'titleStyle',
  },
  {
    title: 'Issuer',
    dataIndex: 'issuer',
    key: 'issuer',
    className: 'titleStyle',
  },
  {
    title: '作用域',
    key: 'scopes',
    className: 'titleStyle',
  },
  {
    title: '用户名声明',
    dataIndex: 'username_claim',
    key: 'username_claim',
    className: 'titleStyle',
  },
  {
    title: 'State有效期',
    key: 'state_ttl',
    className: 'titleStyle',
  },
  {
    title: '启用状态',
    key: 'enable',
    className: 'titleStyle',
  },
  {
    title: '创建时间',
    key: 'create_time',
    className: 'titleStyle',
  },
  {
    title: '操作',
    key: 'action',
    className: 'titleStyle',
  },
]

// 数据源
const dataSource = ref<OidcProvider[]>([])
const loading = ref(false)

// 分页配置
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 表单相关
const modalVisible = ref(false)
const modalLoading = ref(false)
const modalTitle = ref('添加 OIDC 提供者')
const isEdit = ref(false)
const formData = reactive<OidcProviderFormData>({
  provider_id: '',
  client_id: '',
  client_secret: '',
  issuer: '',
  authorization_uri: '',
  token_uri: '',
  redirect_uri: '',
  jwk_set_uri: '',
  scopes: 'openid profile',
  username_claim: 'preferred_username',
  state_ttl: 1800,
  enable: true
})

// 查看详情相关
const viewModalVisible = ref(false)
const currentRecord = ref<OidcProvider | null>(null)

// 删除确认相关
const deleteModalVisible = ref(false)
const deleteLoading = ref(false)
const deleteTarget = ref<OidcProvider | null>(null)

// 加载 OIDC 提供者列表
const loadOidcProviders = async () => {
  loading.value = true
  try {
    const response = await getOidcProviderList({
      page: pagination.current,
      page_size: pagination.pageSize
    })

    if (response.code === 0 && response.data) {
      dataSource.value = response.data.list || []
      pagination.total = response.data.pagination?.total || 0
    } else {
      message.error(response.message || '加载 OIDC 提供者列表失败')
    }
  } catch (error: any) {
    message.error(error.message || '加载 OIDC 提供者列表失败')
  } finally {
    loading.value = false
  }
}

// 分页变化
const handlePageChange = () => {
  loadOidcProviders()
}

// 刷新列表
const handleRefresh = () => {
  pagination.current = 1
  loadOidcProviders()
}

// 时间格式化
const formatTime = (timestamp: number) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 添加 OIDC 提供者
const handleAdd = () => {
  isEdit.value = false
  modalTitle.value = '添加 OIDC 提供者'
  resetFormData()
  modalVisible.value = true
}

// 编辑 OIDC 提供者
const handleEdit = (record: OidcProvider) => {
  isEdit.value = true
  modalTitle.value = '编辑 OIDC 提供者'
  Object.assign(formData, {
    provider_id: record.provider_id,
    client_id: record.client_id,
    client_secret: '',
    issuer: record.issuer,
    authorization_uri: record.authorization_uri,
    token_uri: record.token_uri,
    redirect_uri: record.redirect_uri,
    jwk_set_uri: record.jwk_set_uri,
    scopes: record.scopes,
    username_claim: record.username_claim,
    state_ttl: record.state_ttl,
    enable: record.enable
  })
  modalVisible.value = true
}

// 查看详情
const handleView = (record: OidcProvider) => {
  currentRecord.value = record
  viewModalVisible.value = true
}

// 确认删除
const confirmDelete = (record: OidcProvider) => {
  deleteTarget.value = record
  deleteModalVisible.value = true
}

// 删除
const handleDelete = async () => {
  if (!deleteTarget.value) return

  deleteLoading.value = true
  try {
    const response = await deleteOidcProvider(deleteTarget.value.provider_id)
    if (response.code === 0) {
      message.success('删除成功')
      deleteModalVisible.value = false
      deleteTarget.value = null
      loadOidcProviders()
    } else {
      message.error(response.message || '删除失败')
    }
  } catch (error: any) {
    message.error(error.message || '删除失败')
  } finally {
    deleteLoading.value = false
  }
}

// 切换启用状态
const handleToggleStatus = async (record: OidcProvider, checked: boolean) => {
  try {
    const response = await toggleOidcProviderStatus(record.provider_id, checked)
    if (response.code === 0) {
      message.success(checked ? '已启用' : '已禁用')
      record.enable = checked
    } else {
      message.error(response.message || '操作失败')
    }
  } catch (error: any) {
    message.error(error.message || '操作失败')
  }
}

// 测试连接
const handleTest = async (record: OidcProvider) => {
  const hide = message.loading('正在测试连接...', 0)
  try {
    const testData: OidcProviderFormData = {
      provider_id: record.provider_id,
      client_id: record.client_id,
      issuer: record.issuer,
      authorization_uri: record.authorization_uri,
      token_uri: record.token_uri,
      redirect_uri: record.redirect_uri,
      jwk_set_uri: record.jwk_set_uri,
      scopes: record.scopes,
      username_claim: record.username_claim,
      state_ttl: record.state_ttl
    }
    const response = await testOidcConnection(testData)
    hide()
    if (response.code === 0) {
      message.success('连接测试成功')
    } else {
      message.error(response.message || '连接测试失败')
    }
  } catch (error: any) {
    hide()
    message.error(error.message || '连接测试失败')
  }
}

// 表单验证
const validateForm = (): boolean => {
  if (!formData.provider_id.trim()) {
    message.warning('请输入提供者ID')
    return false
  }
  if (!/^[a-zA-Z0-9_-]+$/.test(formData.provider_id)) {
    message.warning('提供者ID只能包含字母、数字、下划线和横线')
    return false
  }
  if (!formData.client_id.trim()) {
    message.warning('请输入客户端ID')
    return false
  }
  if (!isEdit.value && !formData.client_secret?.trim()) {
    message.warning('请输入客户端密钥')
    return false
  }
  if (!formData.issuer.trim()) {
    message.warning('请输入 Issuer URL')
    return false
  }
  if (!formData.authorization_uri.trim()) {
    message.warning('请输入授权端点')
    return false
  }
  if (!formData.token_uri.trim()) {
    message.warning('请输入 Token 端点')
    return false
  }
  if (!formData.redirect_uri.trim()) {
    message.warning('请输入回调地址')
    return false
  }
  if (!formData.jwk_set_uri.trim()) {
    message.warning('请输入 JWK Set URI')
    return false
  }
  if (!formData.scopes.trim()) {
    message.warning('请输入 OAuth2 作用域')
    return false
  }
  if (!formData.username_claim.trim()) {
    message.warning('请输入用户名声明字段')
    return false
  }
  if (!formData.state_ttl || formData.state_ttl < 60 || formData.state_ttl > 3600) {
    message.warning('State 有效期必须在 60-3600 秒之间')
    return false
  }
  return true
}

// 弹窗确定
const handleModalOk = async () => {
  if (!validateForm()) return

  modalLoading.value = true
  try {
    const submitData: any = { ...formData }
    if (isEdit.value && !submitData.client_secret) {
      delete submitData.client_secret
    }

    const response = isEdit.value
      ? await updateOidcProvider(formData.provider_id, submitData)
      : await createOidcProvider(submitData)

    if (response.code === 0) {
      message.success(isEdit.value ? '更新成功' : '创建成功')
      modalVisible.value = false
      loadOidcProviders()
    } else {
      message.error(response.message || (isEdit.value ? '更新失败' : '创建失败'))
    }
  } catch (error: any) {
    message.error(error.message || (isEdit.value ? '更新失败' : '创建失败'))
  } finally {
    modalLoading.value = false
  }
}

// 弹窗取消
const handleModalCancel = () => {
  modalVisible.value = false
  resetFormData()
}

// 重置表单数据
const resetFormData = () => {
  Object.assign(formData, {
    provider_id: '',
    client_id: '',
    client_secret: '',
    issuer: '',
    authorization_uri: '',
    token_uri: '',
    redirect_uri: '',
    jwk_set_uri: '',
    scopes: 'openid profile',
    username_claim: 'preferred_username',
    state_ttl: 1800,
    enable: true
  })
}

// 组件挂载时加载数据
onMounted(() => {
  loadOidcProviders()
})
</script>

<style scoped lang="scss">
/* ========== Design Tokens ========== */
$bg-page: #0a101e;
$bg-card: #111827;
$bg-surface: #1e293b;
$bg-hover: rgba(255, 255, 255, 0.04);
$border: rgba(255, 255, 255, 0.08);
$border-focus: #3b82f6;
$text-primary: rgba(255, 255, 255, 0.92);
$text-secondary: rgba(255, 255, 255, 0.60);
$text-muted: rgba(255, 255, 255, 0.38);
$accent: #3b82f6;
$accent-hover: #2563eb;
$radius-sm: 6px;
$radius-md: 10px;
$radius-lg: 14px;
$transition: 200ms ease;

/* ========== Root Container ========== */
.oidc-manage-root {
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* ========== Toolbar ========== */
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.record-count {
  font-size: 12px;
  color: $text-muted;
  background: $bg-surface;
  padding: 2px 10px;
  border-radius: 999px;
}

.toolbar-right {
  display: flex;
  gap: 10px;
}

/* ========== Buttons ========== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 500;
  border-radius: $radius-sm;
  border: none;
  cursor: pointer;
  transition: all $transition;
  white-space: nowrap;
}

.btn-icon {
  width: 15px;
  height: 15px;
}

.btn-primary {
  background: $accent;
  color: #fff;

  &:hover:not(:disabled) {
    background: $accent-hover;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.btn-ghost {
  background: transparent;
  color: $text-secondary;
  border: 1px solid $border;

  &:hover:not(:disabled) {
    color: $text-primary;
    border-color: rgba(255, 255, 255, 0.2);
    background: $bg-hover;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* ========== Table Card ========== */
.table-card {
  background: $bg-card;
  border-radius: $radius-lg;
  border: 1px solid $border;
  overflow: hidden;
}

.text-white {
  color: rgba(255, 255, 255, 0.92);
}

.text-muted {
  color: $text-secondary;
  font-size: 13px;
}

.type-badge {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  border-radius: 999px;

  &--web {
    background: rgba(59, 130, 246, 0.12);
    color: #60a5fa;
  }

  &--pilot {
    background: rgba(248, 113, 113, 0.12);
    color: #f87171;
  }
}

.status-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  font-size: 12px;
  border-radius: 999px;
  color: var(--dot-color);
  background: var(--dot-bg);

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: var(--dot-color);
  }
}

/* ========== Scope Tags ========== */
.scope-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.scope-tag {
  padding: 2px 8px;
  font-size: 11px;
  color: $text-secondary;
  background: $bg-surface;
  border: 1px solid $border;
  border-radius: $radius-sm;
}

/* ========== Switch ========== */
.switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;

  input {
    position: absolute;
    opacity: 0;
    width: 0;
    height: 0;
  }
}

.switch-slider {
  width: 38px;
  height: 20px;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  position: relative;
  transition: background $transition;

  &::before {
    content: '';
    position: absolute;
    width: 16px;
    height: 16px;
    left: 2px;
    top: 2px;
    background: #fff;
    border-radius: 50%;
    transition: transform $transition;
  }

  input:checked + & {
    background: $accent;

    &::before {
      transform: translateX(18px);
    }
  }
}

.switch-label {
  font-size: 13px;
  color: $text-secondary;
}

/* ========== Action Buttons ========== */
.action-btns {
  display: flex;
  gap: 4px;
}

.action-btn {
  padding: 3px 12px;
  font-size: 12px;
  border-radius: $radius-sm;
  border: none;
  cursor: pointer;
  transition: all $transition;
  font-weight: 500;

  &--enable {
    background: rgba(52, 211, 153, 0.12);
    color: #34d399;

    &:hover {
      background: rgba(52, 211, 153, 0.22);
    }
  }

  &--delete {
    background: rgba(248, 113, 113, 0.10);
    color: #f87171;

    &:hover {
      background: rgba(248, 113, 113, 0.20);
    }
  }
}

/* ========== Pagination Bar ========== */
.pagination-bar {
  padding: 14px 20px;
  border-top: 1px solid $border;
  display: flex;
  justify-content: flex-end;
}

/* ========== Modal ========== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.60);
  backdrop-filter: blur(6px);
}

.modal-box {
  width: 460px;
  background: $bg-card;
  border: 1px solid $border;
  border-radius: $radius-lg;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  animation: modal-in 200ms ease;

  &--large {
    width: 800px;
  }
}

@keyframes modal-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.97);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid $border;
}

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.modal-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: transparent;
  border: none;
  color: $text-muted;
  cursor: pointer;
  border-radius: $radius-sm;
  transition: all $transition;

  svg {
    width: 16px;
    height: 16px;
  }

  &:hover {
    color: $text-primary;
    background: $bg-hover;
  }
}

.modal-body {
  padding: 24px;
  max-height: 70vh;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid $border;
}

/* ========== Form ========== */
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &--full {
    grid-column: 1 / -1;
  }
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: $text-secondary;
}

.required {
  color: #f87171;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  font-size: 13px;
  color: $text-primary;
  background: $bg-surface;
  border: 1px solid $border;
  border-radius: $radius-sm;
  outline: none;
  transition: border-color $transition, box-shadow $transition;

  &::placeholder {
    color: $text-muted;
  }

  &:focus {
    border-color: $border-focus;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
  }

  &:disabled {
    background: rgba(255, 255, 255, 0.04);
    color: rgba(255, 255, 255, 0.35);
    cursor: not-allowed;
  }
}

.form-hint {
  font-size: 11px;
  color: $text-muted;
}

/* ========== Detail Grid ========== */
.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &--full {
    grid-column: 1 / -1;
  }
}

.detail-label {
  font-size: 11px;
  color: $text-muted;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
}

.detail-value {
  font-size: 13px;
  color: $text-primary;
  word-break: break-all;
}

/* ========== Confirm Text ========== */
.confirm-text {
  font-size: 14px;
  color: $text-secondary;
  line-height: 1.6;
  margin: 0;

  strong {
    color: $text-primary;
    font-weight: 600;
  }
}

/* ========== Loading Spinner ========== */
.spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== Ant Design Dark Overrides ========== */
:deep(.ant-table) {
  background: transparent !important;
  color: $text-primary;
}

:deep(.ant-table-thead > tr > th) {
  background: $bg-surface !important;
  color: $text-secondary !important;
  border-bottom: 1px solid $border !important;
  font-weight: 600;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 12px 16px !important;
}

:deep(.ant-table-tbody > tr > td) {
  background: #111827 !important;
  border-bottom: 1px solid $border !important;
  color: $text-primary;
  padding: 12px 16px !important;
  font-size: 13px;
}

:deep(.ant-table-tbody > tr:hover > td),
:deep(.ant-table-tbody > tr.ant-table-row-hover > td),
:deep(.ant-table-tbody > tr > td.ant-table-cell-row-hover) {
  background: #1f2937 !important;
}

:deep(.ant-table-tbody > tr:last-child > td) {
  border-bottom: none !important;
}

:deep(.ant-table-header) {
  border: none !important;
}

:deep(.ant-table-cell-scrollbar) {
  box-shadow: none !important;
}

:deep(.ant-pagination-item) {
  background: transparent !important;
  border-color: $border !important;

  a {
    color: $text-secondary !important;
  }
}

:deep(.ant-pagination-item-active) {
  border-color: $accent !important;
  background: rgba(59, 130, 246, 0.12) !important;

  a {
    color: $accent !important;
  }
}

:deep(.ant-pagination-prev button),
:deep(.ant-pagination-next button) {
  color: $text-secondary !important;
  background: transparent !important;
}

:deep(.ant-pagination-total-text) {
  color: $text-muted !important;
  font-size: 12px;
}

:deep(.ant-pagination-options) {
  .ant-select-selector {
    background: $bg-surface !important;
    border-color: $border !important;
    color: $text-secondary !important;
  }
}
</style>

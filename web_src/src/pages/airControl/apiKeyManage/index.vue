<script setup lang="ts">
import { onMounted, ref, reactive, computed } from "vue";
import { message } from 'ant-design-vue';
import {
    getThirdPartyApiKeyList,
    createThirdPartyApiKey,
    deleteThirdPartyApiKey,
    toggleThirdPartyApiKeyStatus,
    ThirdPartyApiKey,
    ApiKeyFormData
} from "@/api/manage";
import { useClipboard } from '@vueuse/core';

// 状态
const loading = ref(false);
const apiKeyList = ref<ThirdPartyApiKey[]>([]);
const modalVisible = ref(false);

// 删除确认弹窗
const deleteConfirmVisible = ref(false);
const deleteTarget = ref<ThirdPartyApiKey | null>(null);

// 创建成功后显示 API Key
const createdKeyVisible = ref(false);
const createdApiKey = ref('');
const createdAppName = ref('');
const keyCopied = ref(false);
const deleteLoading = ref(false);

// 表单数据
const formData = reactive<ApiKeyFormData>({
    uid: undefined,
    app: '',
    expire_time: null,
    enable: true
});

// 是否永久有效
const isPermanent = ref(true);
// 日期时间输入
const expireDateStr = ref('');

// 搜索关键词
const searchKeyword = ref('');

// 显示/隐藏 Key 值
const visibleKeys = ref<Set<string>>(new Set());

// 剪贴板
const { copy, isSupported: clipboardSupported } = useClipboard();

// 过滤后的列表
const filteredList = computed(() => {
    if (!searchKeyword.value) return apiKeyList.value;
    const keyword = searchKeyword.value.toLowerCase();
    return apiKeyList.value.filter(item =>
        item.app.toLowerCase().includes(keyword) ||
        (item.uid.toLowerCase().includes(keyword))
    );
});

// 加载列表
const loadApiKeyList = async () => {
    loading.value = true;
    try {
        const res = await getThirdPartyApiKeyList();
        if (res.code === 0 && res.data) {
            apiKeyList.value = res.data;
        }
    } catch (error) {
        console.error('加载 API Key 列表失败:', error);
        message.error('加载失败');
    } finally {
        loading.value = false;
    }
};

// 打开新增弹窗
const openAddModal = () => {
    Object.assign(formData, {
        uid: undefined,
        app: '',
        expire_time: null,
        enable: true
    });
    isPermanent.value = true;
    expireDateStr.value = '';
    modalVisible.value = true;
};

// 提交表单
const handleSubmit = async () => {
    if (!formData.app.trim()) {
        message.warning('请输入应用名称');
        return;
    }

    // 处理过期时间
    if (!isPermanent.value) {
        if (!expireDateStr.value) {
            message.warning('请选择过期时间');
            return;
        }
        formData.expire_time = new Date(expireDateStr.value).getTime();
    } else {
        formData.expire_time = null;
    }

    loading.value = true;
    try {
        const res = await createThirdPartyApiKey(formData);
        modalVisible.value = false;
        // 显示返回的 API Key，提示用户保存
        if (res.code === 0 && res.data?.api_key) {
            createdAppName.value = formData.app;
            createdApiKey.value = res.data.api_key;
            keyCopied.value = false;
            createdKeyVisible.value = true;
        } else {
            message.error(res.message);
        }
        message.success('创建成功');
        await loadApiKeyList();
    } catch (error) {
        console.error('保存失败:', error);
        message.error('保存失败');
    } finally {
        loading.value = false;
    }
};

// 删除
const handleDelete = (record: ThirdPartyApiKey) => {
    deleteTarget.value = record;
    deleteConfirmVisible.value = true;
};

const confirmDelete = async () => {
    if (!deleteTarget.value) return;
    deleteLoading.value = true;
    try {
        await deleteThirdPartyApiKey(deleteTarget.value.app);
        message.success('删除成功');
        deleteConfirmVisible.value = false;
        deleteTarget.value = null;
        await loadApiKeyList();
    } catch (error) {
        message.error('删除失败');
    } finally {
        deleteLoading.value = false;
    }
};

const cancelDelete = () => {
    deleteConfirmVisible.value = false;
    deleteTarget.value = null;
};

// 切换状态
const handleToggleStatus = async (record: ThirdPartyApiKey) => {
    const newStatus = !record.enable;
    try {
        await toggleThirdPartyApiKeyStatus(record.app, newStatus);
        record.enable = newStatus;
        message.success(newStatus ? '已启用' : '已禁用');
    } catch (error) {
        message.error('状态更新失败');
    }
};

// 复制 Key 值
const handleCopy = async (text: string) => {
    if (clipboardSupported) {
        await copy(text);
        message.success('已复制到剪贴板');
    } else {
        message.warning('浏览器不支持复制功能');
    }
};

// 切换 Key 显示/隐藏
const toggleKeyVisibility = (uid: string) => {
    if (visibleKeys.value.has(uid)) {
        visibleKeys.value.delete(uid);
    } else {
        visibleKeys.value.add(uid);
    }
};

// 遮盖 Key 值显示
const maskKeyValue = (value: string, uid: string) => {
    if (!value) return '';
    if (visibleKeys.value.has(uid)) {
        return value;
    }
    if (value.length <= 8) {
        return '••••••••';
    }
    return value.slice(0, 4) + '••••••••' + value.slice(-4);
};

// 格式化时间
const formatDate = (timestamp?: number) => {
    if (!timestamp) return '';
    return new Date(timestamp).toLocaleString();
};

// 判断是否已过期
const isExpired = (timestamp?: number) => {
    if (!timestamp) return false;
    return timestamp < Date.now();
};

onMounted(() => {
    loadApiKeyList();
});
</script>

<template>
    <div class="apikey-root">
        <!-- 顶部工具栏 -->
        <div class="toolbar">
            <div class="toolbar-left">
                <h2 class="panel-title">API 密钥列表</h2>
                <span class="record-count">共 {{ filteredList.length }} 条</span>
            </div>
            <div class="toolbar-right">
                <div class="search-box">
                    <svg class="search-icon" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z"
                            clip-rule="evenodd" />
                    </svg>
                    <input v-model="searchKeyword" type="text" placeholder="搜索名称、UID..." class="search-input" />
                    <button v-if="searchKeyword" class="search-clear" @click="searchKeyword = ''">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-3.5 h-3.5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <button class="btn btn-primary" @click="openAddModal">
                    <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
                            clip-rule="evenodd" />
                    </svg>
                    新增 API Key
                </button>
            </div>
        </div>

        <!-- 表格区域 -->
        <div class="table-card">
            <a-spin :spinning="loading">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th style="width: 180px">应用名称</th>

                            <th style="width: 200px">过期时间</th>
                            <th style="width: 100px">状态</th>
                            <th style="width: 120px">操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-if="filteredList.length === 0">
                            <td colspan="4" class="empty-state">

                                <div class="empty-content">
                                    <svg class="empty-icon" viewBox="0 0 20 20" fill="currentColor">
                                        <path fill-rule="evenodd"
                                            d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v2H2v-4l4.257-4.257A6 6 0 1118 8zm-6-4a1 1 0 100 2 2 2 0 012 2 1 1 0 102 0 4 4 0 00-4-4z"
                                            clip-rule="evenodd" />
                                    </svg>
                                    <span>暂无 API Key 数据</span>
                                    <button class="btn-link" @click="openAddModal">点击添加</button>
                                </div>
                            </td>
                        </tr>
                        <tr v-for="(record, idx) in filteredList" :key="record.uid"
                            :class="{ 'row-alt': idx % 2 === 1 }">
                            <td>
                                <span class="app-name">{{ record.app }}</span>
                            </td>

                            <td>
                                <span v-if="!record.expire_time" class="expire-permanent">永久有效</span>
                                <span v-else
                                    :class="['expire-date', { 'expire-expired': isExpired(record.expire_time) }]">
                                    {{ formatDate(record.expire_time) }}
                                    <span v-if="isExpired(record.expire_time)" class="expire-tag">已过期</span>
                                </span>
                            </td>
                            <td>
                                <label class="toggle" @click="handleToggleStatus(record)">
                                    <span class="toggle-track" :class="{ 'toggle-on': record.enable }">
                                        <span class="toggle-thumb"></span>
                                    </span>
                                    <span class="toggle-label" :class="{ 'toggle-label-on': record.enable }">
                                        {{ record.enable ? '启用' : '禁用' }}
                                    </span>
                                </label>
                            </td>
                            <td>
                                <button class="action-btn action-btn--delete" @click="handleDelete(record)">
                                    <svg viewBox="0 0 20 20" fill="currentColor" class="w-3.5 h-3.5">
                                        <path fill-rule="evenodd"
                                            d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
                                            clip-rule="evenodd" />
                                    </svg>
                                    删除
                                </button>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </a-spin>
        </div>

        <!-- 新增弹窗 -->
        <div v-if="modalVisible" class="modal-overlay" @click.self="modalVisible = false">
            <div class="modal-box">
                <div class="modal-header">
                    <h3 class="modal-title">新增 API Key</h3>
                    <button class="modal-close" @click="modalVisible = false">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>

                <form class="modal-body" @submit.prevent="handleSubmit">
                    <!-- 应用名称 -->
                    <div class="form-group">
                        <label class="form-label">应用名称 <span class="required">*</span></label>
                        <input v-model="formData.app" type="text" placeholder="请输入应用名称" maxlength="50"
                            class="form-input" />
                    </div>

                    <!-- 过期时间 -->
                    <div class="form-group">
                        <label class="form-label">过期时间</label>
                        <label class="checkbox-item">
                            <input type="checkbox" v-model="isPermanent" class="checkbox-input" />
                            <span class="checkbox-box">
                                <svg v-if="isPermanent" viewBox="0 0 20 20" fill="currentColor" class="w-3 h-3">
                                    <path fill-rule="evenodd"
                                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                                        clip-rule="evenodd" />
                                </svg>
                            </span>
                            <span class="checkbox-label">永久有效</span>
                        </label>
                        <div v-if="!isPermanent" class="datetime-wrap">
                            <input v-model="expireDateStr" type="datetime-local" class="form-input"
                                :style="{ colorScheme: 'dark' }" />
                            <p class="form-hint">不设置过期时间则永久生效</p>
                        </div>
                    </div>
                </form>

                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="modalVisible = false">取消</button>
                    <button class="btn btn-primary" @click="handleSubmit" :disabled="loading">
                        <span v-if="loading" class="spinner"></span>
                        创建
                    </button>
                </div>
            </div>
        </div>

        <!-- 删除确认弹窗 -->
        <div v-if="deleteConfirmVisible" class="modal-overlay" @click.self="cancelDelete">
            <div class="modal-box delete-confirm-box">
                <div class="modal-header delete-confirm-header">
                    <div class="delete-confirm-title-wrap">
                        <div class="delete-icon-circle">
                            <svg viewBox="0 0 20 20" fill="currentColor" class="delete-icon-svg">
                                <path fill-rule="evenodd"
                                    d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
                                    clip-rule="evenodd" />
                            </svg>
                        </div>
                        <h3 class="modal-title">确认删除</h3>
                    </div>
                    <button class="modal-close" @click="cancelDelete">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <div class="delete-confirm-body">
                    <p class="delete-confirm-text">
                        确定要删除 <strong class="delete-app-name">"{{ deleteTarget?.app }}"</strong> 吗？
                    </p>
                    <p class="delete-confirm-warn">此操作不可恢复，删除后该 API Key 将立即失效。</p>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="cancelDelete" :disabled="deleteLoading">取消</button>
                    <button class="btn btn-danger" @click="confirmDelete" :disabled="deleteLoading">
                        <span v-if="deleteLoading" class="spinner spinner--sm"></span>
                        <svg v-else viewBox="0 0 20 20" fill="currentColor" class="btn-icon">
                            <path fill-rule="evenodd"
                                d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z"
                                clip-rule="evenodd" />
                        </svg>
                        确认删除
                    </button>
                </div>
            </div>
        </div>

        <!-- API Key 创建成功弹窗 -->
        <div v-if="createdKeyVisible" class="modal-overlay">
            <div class="modal-box apikey-result-box">
                <div class="modal-header apikey-result-header">
                    <div class="delete-confirm-title-wrap">
                        <div class="apikey-result-icon-circle">
                            <svg viewBox="0 0 20 20" fill="currentColor" class="apikey-result-icon-svg">
                                <path fill-rule="evenodd"
                                    d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v2H2v-4l4.257-4.257A6 6 0 1118 8zm-6-4a1 1 0 100 2 2 2 0 012 2 1 1 0 102 0 4 4 0 00-4-4z"
                                    clip-rule="evenodd" />
                            </svg>
                        </div>
                        <h3 class="modal-title">API Key 创建成功</h3>
                    </div>
                </div>
                <div class="apikey-result-body">
                    <div class="apikey-result-warn">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="apikey-warn-svg">
                            <path fill-rule="evenodd"
                                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                                clip-rule="evenodd" />
                        </svg>
                        <span>请立即复制并妥善保存此 API Key，关闭后将无法再次查看！</span>
                    </div>
                    <div class="apikey-result-info">
                        <div class="apikey-result-label">应用名称</div>
                        <div class="apikey-result-appname">{{ createdAppName }}</div>
                    </div>
                    <div class="apikey-result-info">
                        <div class="apikey-result-label">API Key</div>
                        <div class="apikey-result-key-wrap">
                            <code class="apikey-result-key">{{ createdApiKey }}</code>
                            <button class="btn apikey-copy-btn" :class="{ 'apikey-copied': keyCopied }"
                                @click="async () => { await handleCopy(createdApiKey); keyCopied = true; }">
                                <svg v-if="!keyCopied" viewBox="0 0 20 20" fill="currentColor" class="btn-icon">
                                    <path d="M8 2a1 1 0 000 2h2a1 1 0 100-2H8z" />
                                    <path
                                        d="M3 5a2 2 0 012-2 3 3 0 003 3h2a3 3 0 003-3 2 2 0 012 2v6h-4.586l1.293-1.293a1 1 0 00-1.414-1.414l-3 3a1 1 0 000 1.414l3 3a1 1 0 001.414-1.414L10.414 13H15v3a2 2 0 01-2 2H5a2 2 0 01-2-2V5zM15 11h2a1 1 0 110 2h-2v-2z" />
                                </svg>
                                <svg v-else viewBox="0 0 20 20" fill="currentColor" class="btn-icon">
                                    <path fill-rule="evenodd"
                                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                                        clip-rule="evenodd" />
                                </svg>
                                {{ keyCopied ? '已复制' : '复制' }}
                            </button>
                        </div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-primary" @click="createdKeyVisible = false">
                        我已保存，关闭
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

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

/* ========== Root ========== */
.apikey-root {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

/* ========== Toolbar ========== */
.toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
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
    align-items: center;
}

/* ========== Search ========== */
.search-box {
    position: relative;
    display: flex;
    align-items: center;
}

.search-icon {
    position: absolute;
    left: 10px;
    width: 15px;
    height: 15px;
    color: $text-muted;
    pointer-events: none;
}

.search-input {
    width: 220px;
    padding: 8px 30px 8px 32px;
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
}

.search-clear {
    position: absolute;
    right: 6px;
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    background: transparent;
    color: $text-muted;
    border-radius: 50%;
    cursor: pointer;
    transition: all $transition;

    &:hover {
        color: $text-primary;
        background: $bg-hover;
    }
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

    &:hover {
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

    &:hover {
        color: $text-primary;
        border-color: rgba(255, 255, 255, 0.2);
        background: $bg-hover;
    }
}

.btn-link {
    background: none;
    border: none;
    color: $accent;
    cursor: pointer;
    font-size: 13px;
    padding: 0;

    &:hover {
        text-decoration: underline;
    }
}

/* ========== Data Table ========== */
.table-card {
    background: $bg-card;
    border-radius: $radius-lg;
    border: 1px solid $border;
    overflow: hidden;
}

.data-table {
    width: 100%;
    border-collapse: collapse;

    th {
        text-align: left;
        padding: 12px 16px;
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        color: $text-secondary;
        background: $bg-surface;
        border-bottom: 1px solid $border;
    }

    td {
        padding: 12px 16px;
        font-size: 13px;
        color: $text-primary;
        border-bottom: 1px solid $border;
        vertical-align: middle;
    }

    tr:last-child td {
        border-bottom: none;
    }

    .row-alt {
        background: rgba(255, 255, 255, 0.015);
    }

    tbody tr {
        transition: background $transition;

        &:hover {
            background: $bg-hover;
        }
    }
}

/* ========== Cells ========== */
.app-name {
    font-weight: 500;
    color: $text-primary;
}

.uid-cell {
    display: flex;
    align-items: center;
    gap: 8px;
}

.uid-text {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 12px;
    color: $text-secondary;
    background: transparent;
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.uid-actions {
    display: flex;
    gap: 2px;
    flex-shrink: 0;
}

.icon-btn {
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    background: transparent;
    color: $text-muted;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: all $transition;

    &:hover {
        color: $accent;
        background: rgba(59, 130, 246, 0.08);
    }
}

.expire-permanent {
    color: #34d399;
    font-size: 12px;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 10px;
    border-radius: 999px;
    background: rgba(52, 211, 153, 0.10);

    &::before {
        content: '';
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #34d399;
    }
}

.expire-date {
    color: $text-secondary;
    font-size: 13px;
}

.expire-expired {
    color: #f87171;
}

.expire-tag {
    display: inline-block;
    font-size: 10px;
    padding: 1px 6px;
    margin-left: 6px;
    border-radius: 999px;
    background: rgba(248, 113, 113, 0.15);
    color: #f87171;
    font-weight: 500;
}

/* ========== Toggle ========== */
.toggle {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
}

.toggle-track {
    position: relative;
    width: 36px;
    height: 20px;
    border-radius: 999px;
    background: #374151;
    transition: background $transition;
}

.toggle-on {
    background: $accent;
}

.toggle-thumb {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #fff;
    transition: transform $transition;

    .toggle-on & {
        transform: translateX(16px);
    }
}

.toggle-label {
    font-size: 12px;
    color: $text-muted;
    font-weight: 500;
    transition: color $transition;
}

.toggle-label-on {
    color: $accent;
}

/* ========== Action Buttons ========== */
.action-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 4px 12px;
    font-size: 12px;
    border-radius: $radius-sm;
    border: none;
    cursor: pointer;
    transition: all $transition;
    font-weight: 500;

    &--delete {
        background: rgba(248, 113, 113, 0.10);
        color: #f87171;

        &:hover {
            background: rgba(248, 113, 113, 0.20);
        }
    }
}

/* ========== Empty State ========== */
.empty-state {
    text-align: center;
    padding: 60px 20px !important;
}

.empty-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: $text-muted;
    font-size: 13px;
}

.empty-icon {
    width: 32px;
    height: 32px;
    color: rgba(255, 255, 255, 0.15);
    margin-bottom: 4px;
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
    width: 440px;
    background: $bg-card;
    border: 1px solid $border;
    border-radius: $radius-lg;
    box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4);
    overflow: hidden;
    animation: modal-in 200ms ease;
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
    padding: 16px 24px;
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
    border: none;
    background: transparent;
    color: $text-muted;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: all $transition;

    &:hover {
        color: $text-primary;
        background: $bg-hover;
    }
}

.modal-body {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.modal-footer {
    padding: 14px 24px;
    border-top: 1px solid $border;
    display: flex;
    justify-content: flex-end;
    gap: 10px;
}

/* ========== Form ========== */
.form-group {
    display: flex;
    flex-direction: column;
    gap: 8px;
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
    box-sizing: border-box;

    &::placeholder {
        color: $text-muted;
    }

    &:focus {
        border-color: $border-focus;
        box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.15);
    }
}

.form-hint {
    font-size: 11px;
    color: $text-muted;
    margin: 2px 0 0;
}

.datetime-wrap {
    display: flex;
    flex-direction: column;
    gap: 4px;
}

/* ========== Checkbox ========== */
.checkbox-item {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    user-select: none;
}

.checkbox-input {
    position: absolute;
    opacity: 0;
    pointer-events: none;
}

.checkbox-box {
    width: 18px;
    height: 18px;
    border-radius: 4px;
    border: 1.5px solid rgba(255, 255, 255, 0.20);
    background: $bg-surface;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all $transition;
    color: #fff;
    flex-shrink: 0;

    .checkbox-input:checked+& {
        background: $accent;
        border-color: $accent;
    }
}

.checkbox-label {
    font-size: 13px;
    color: $text-secondary;
}

/* ========== Delete Confirm ========== */
.delete-confirm-box {
    width: 420px;
}

.delete-confirm-header {
    border-bottom: 1px solid rgba(248, 113, 113, 0.15) !important;
}

.delete-confirm-title-wrap {
    display: flex;
    align-items: center;
    gap: 12px;
}

.delete-icon-circle {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: rgba(248, 113, 113, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}

.delete-icon-svg {
    width: 18px;
    height: 18px;
    color: #f87171;
}

.delete-confirm-body {
    padding: 20px 24px;
}

.delete-confirm-text {
    font-size: 14px;
    color: $text-primary;
    margin: 0 0 8px;
    line-height: 1.6;
}

.delete-app-name {
    color: #f87171;
    font-weight: 600;
}

.delete-confirm-warn {
    font-size: 12px;
    color: $text-muted;
    margin: 0;
    padding: 8px 12px;
    background: rgba(248, 113, 113, 0.06);
    border-left: 3px solid rgba(248, 113, 113, 0.4);
    border-radius: 0 $radius-sm $radius-sm 0;
    line-height: 1.5;
}

/* ========== API Key Result ========== */
.apikey-result-box {
    width: 520px;
}

.apikey-result-header {
    border-bottom: 1px solid rgba(52, 211, 153, 0.2) !important;
}

.apikey-result-icon-circle {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: rgba(52, 211, 153, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}

.apikey-result-icon-svg {
    width: 18px;
    height: 18px;
    color: #34d399;
}

.apikey-result-body {
    padding: 20px 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.apikey-result-warn {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 14px;
    background: rgba(251, 191, 36, 0.08);
    border: 1px solid rgba(251, 191, 36, 0.2);
    border-radius: $radius-sm;
    font-size: 13px;
    color: #fbbf24;
    line-height: 1.5;
}

.apikey-warn-svg {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
    margin-top: 1px;
}

.apikey-result-info {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.apikey-result-label {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    color: $text-muted;
}

.apikey-result-appname {
    font-size: 14px;
    font-weight: 500;
    color: $text-primary;
}

.apikey-result-key-wrap {
    display: flex;
    align-items: stretch;
    gap: 8px;
}

.apikey-result-key {
    flex: 1;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 12px;
    color: #34d399;
    background: rgba(52, 211, 153, 0.06);
    border: 1px solid rgba(52, 211, 153, 0.15);
    border-radius: $radius-sm;
    padding: 10px 14px;
    word-break: break-all;
    line-height: 1.6;
    user-select: all;
}

.apikey-copy-btn {
    background: $bg-surface;
    color: $text-secondary;
    border: 1px solid $border;
    flex-shrink: 0;
    padding: 8px 14px;

    &:hover {
        color: $text-primary;
        border-color: rgba(255, 255, 255, 0.2);
        background: $bg-hover;
    }
}

.apikey-copied {
    color: #34d399 !important;
    border-color: rgba(52, 211, 153, 0.3) !important;
    background: rgba(52, 211, 153, 0.08) !important;
}

.btn-danger {
    background: #dc2626;
    color: #fff;

    &:hover {
        background: #b91c1c;
    }

    &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
}

/* ========== Spinner ========== */
.spinner {
    width: 14px;
    height: 14px;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-top-color: #fff;
    border-radius: 50%;
    animation: spin 0.6s linear infinite;
    margin-right: 4px;

    &--sm {
        width: 12px;
        height: 12px;
    }
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

/* ========== Utility ========== */
.w-3 {
    width: 12px;
}

.h-3 {
    height: 12px;
}

.w-3\.5 {
    width: 14px;
}

.h-3\.5 {
    height: 14px;
}

.w-4 {
    width: 16px;
}

.h-4 {
    height: 16px;
}

.w-5 {
    width: 20px;
}

.h-5 {
    height: 20px;
}
</style>

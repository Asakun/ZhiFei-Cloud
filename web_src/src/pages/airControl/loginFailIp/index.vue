<script setup lang="ts">
import { ref, onMounted, computed, createVNode } from "vue";
import { useRouter } from "vue-router";
import { message, Modal } from "ant-design-vue";
import { loginFailList, unlockLoginFail, getUserInfo } from "@/api/manage";
import { ExclamationCircleOutlined } from '@ant-design/icons-vue';

// IP列表数据
interface IpRecord {
    ip: string;
    failCount: number;
}

const dataSource = ref<IpRecord[]>([]);
const loading = ref(false);
const userInfo = ref<any>(null);
const router = useRouter();

const goBack = () => {
    router.back();
};

// 判断是否为管理员
const isAdmin = computed(() => {
    return userInfo.value?.role_name === '管理员' || userInfo.value?.role_name === 'admin';
});

// 获取用户信息
const fetchUserInfo = async () => {
    try {
        const res = await getUserInfo();
        if (res.code === 0) {
            userInfo.value = res.data;
        }
    } catch (error) {
        console.error("获取用户信息失败:", error);
    }
};

// 获取失败IP列表
const fetchLoginFailList = async () => {
    loading.value = true;
    try {
        const res = await loginFailList();
        if (res.code === 0 && res.data) {
            const list: IpRecord[] = [];
            for (const [ip, count] of Object.entries(res.data)) {
                list.push({
                    ip,
                    failCount: count as number
                });
            }
            list.sort((a, b) => b.failCount - a.failCount);
            dataSource.value = list;
        } else {
            dataSource.value = [];
        }
    } catch (error) {
        console.error("获取失败IP列表出错:", error);
        message.error("获取失败IP列表出错");
    } finally {
        loading.value = false;
    }
};

// 解锁IP
const handleUnlock = (record: IpRecord) => {
    Modal.confirm({
        title: '确认解锁',
        icon: createVNode(ExclamationCircleOutlined),
        content: `确定要解锁IP地址 "${record.ip}" 吗？`,
        okText: '确定',
        cancelText: '取消',
        centered: true,
        wrapClassName: 'dark-theme-modal',
        onOk: async () => {
            try {
                const res = await unlockLoginFail(record.ip);
                if (res.code === 0) {
                    message.success(`IP地址 ${record.ip} 解锁成功`);
                    fetchLoginFailList();
                } else {
                    message.error(res.message || "解锁失败");
                }
            } catch (error) {
                console.error("解锁失败:", error);
                message.error("解锁操作出错");
            }
        }
    });
};

// 刷新列表
const handleRefresh = () => {
    fetchLoginFailList();
};

// 获取失败次数严重等级
const getSeverity = (count: number) => {
    if (count >= 5) return { level: 'critical', color: '#f87171', bg: 'rgba(248,113,113,0.12)' };
    if (count >= 3) return { level: 'warning', color: '#fbbf24', bg: 'rgba(251,191,36,0.12)' };
    return { level: 'low', color: '#94a3b8', bg: 'rgba(148,163,184,0.10)' };
};

onMounted(async () => {
    await fetchUserInfo();
    await fetchLoginFailList();
});
</script>

<template>
    <div class="page-root">
        <!-- 顶部工具栏 -->
        <div class="toolbar">
            <div class="toolbar-left">
                <button class="btn btn-ghost btn-back" @click="goBack">
                    <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M9.707 16.707a1 1 0 01-1.414 0l-6-6a1 1 0 010-1.414l6-6a1 1 0 011.414 1.414L5.414 9H17a1 1 0 110 2H5.414l4.293 4.293a1 1 0 010 1.414z"
                            clip-rule="evenodd" />
                    </svg>
                    返回
                </button>
                <div class="title-group">
                    <h2 class="panel-title">
                        <svg class="title-icon" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd"
                                d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5C18.266 6.5 18.5 8.088 18.5 9.72c0 5.21-3.816 9.582-8.5 10.28C5.316 19.302 1.5 14.93 1.5 9.72c0-1.632.234-3.22.666-4.721zM10 12a2 2 0 100-4 2 2 0 000 4z"
                                clip-rule="evenodd" />
                        </svg>
                        IP 安全管理
                    </h2>
                    <span class="record-count">{{ dataSource.length }} 条记录</span>
                </div>
            </div>
            <div class="toolbar-right">
                <button class="btn btn-primary" @click="handleRefresh" :disabled="loading">
                    <svg class="btn-icon" :class="{ spinning: loading }" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                            d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 010 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.057a1 1 0 011.276.61A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.276z"
                            clip-rule="evenodd" />
                    </svg>
                    刷新列表
                </button>
            </div>
        </div>

        <!-- 权限提示 -->
        <div v-if="!isAdmin" class="notice-bar">
            <svg class="notice-icon" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd"
                    d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
                    clip-rule="evenodd" />
            </svg>
            <span>当前用户没有解锁IP的权限，仅管理员可以执行解锁操作</span>
        </div>

        <!-- 统计卡片 -->
        <div class="stats-row">
            <div class="stat-card">
                <div class="stat-icon-wrap stat-icon--blue">
                    <svg viewBox="0 0 20 20" fill="currentColor" class="stat-svg">
                        <path fill-rule="evenodd"
                            d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5C18.266 6.5 18.5 8.088 18.5 9.72c0 5.21-3.816 9.582-8.5 10.28C5.316 19.302 1.5 14.93 1.5 9.72c0-1.632.234-3.22.666-4.721zM10 12a2 2 0 100-4 2 2 0 000 4z"
                            clip-rule="evenodd" />
                    </svg>
                </div>
                <div class="stat-info">
                    <span class="stat-value">{{ dataSource.length }}</span>
                    <span class="stat-label">被锁定IP</span>
                </div>
            </div>
            <div class="stat-card">
                <div class="stat-icon-wrap stat-icon--amber">
                    <svg viewBox="0 0 20 20" fill="currentColor" class="stat-svg">
                        <path fill-rule="evenodd"
                            d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                            clip-rule="evenodd" />
                    </svg>
                </div>
                <div class="stat-info">
                    <span class="stat-value">{{dataSource.reduce((sum, item) => sum + item.failCount, 0)}}</span>
                    <span class="stat-label">总失败次数</span>
                </div>
            </div>
        </div>

        <!-- 表格区域 -->
        <div class="table-card">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>IP 地址</th>
                        <th style="width: 160px">失败次数</th>
                        <th v-if="isAdmin" style="width: 120px">操作</th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-if="loading && dataSource.length === 0">
                        <td :colspan="isAdmin ? 3 : 2" class="loading-state">
                            <div class="loading-content">
                                <span class="spinner-lg"></span>
                                <span>加载中...</span>
                            </div>
                        </td>
                    </tr>
                    <tr v-else-if="dataSource.length === 0">
                        <td :colspan="isAdmin ? 3 : 2" class="empty-state">
                            <div class="empty-content">
                                <svg class="empty-icon" viewBox="0 0 20 20" fill="currentColor">
                                    <path fill-rule="evenodd"
                                        d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5C18.266 6.5 18.5 8.088 18.5 9.72c0 5.21-3.816 9.582-8.5 10.28C5.316 19.302 1.5 14.93 1.5 9.72c0-1.632.234-3.22.666-4.721zM10 12a2 2 0 100-4 2 2 0 000 4z"
                                        clip-rule="evenodd" />
                                </svg>
                                <span class="empty-title">暂无被锁定的IP地址</span>
                                <span class="empty-desc">系统运行正常，没有触发登录失败锁定</span>
                            </div>
                        </td>
                    </tr>
                    <tr v-for="(record, idx) in dataSource" :key="record.ip" :class="{ 'row-alt': idx % 2 === 1 }">
                        <td>
                            <div class="ip-cell">
                                <svg class="ip-icon" viewBox="0 0 20 20" fill="currentColor">
                                    <path fill-rule="evenodd"
                                        d="M4.083 9h1.946c.089-1.546.383-2.97.837-4.118A6.004 6.004 0 004.083 9zM10 2a8 8 0 100 16 8 8 0 000-16zm0 2c-.076 0-.232.032-.465.262-.238.234-.497.623-.737 1.182-.389.907-.673 2.142-.766 3.556h3.936c-.093-1.414-.377-2.649-.766-3.556-.24-.56-.5-.948-.737-1.182C10.232 4.032 10.076 4 10 4zm3.971 5c-.089-1.546-.383-2.97-.837-4.118A6.004 6.004 0 0115.917 9h-1.946zm-2.003 2H8.032c.093 1.414.377 2.649.766 3.556.24.56.5.948.737 1.182.233.23.389.262.465.262.076 0 .232-.032.465-.262.238-.234.497-.623.737-1.182.389-.907.673-2.142.766-3.556zm1.166 4.118c.454-1.147.748-2.572.837-4.118h1.946a6.004 6.004 0 01-2.783 4.118zm-6.268 0C6.412 13.97 6.118 12.546 6.029 11H4.083a6.004 6.004 0 002.783 4.118z"
                                        clip-rule="evenodd" />
                                </svg>
                                <code class="ip-text">{{ record.ip }}</code>
                            </div>
                        </td>
                        <td>
                            <span class="fail-badge"
                                :style="{ '--badge-color': getSeverity(record.failCount).color, '--badge-bg': getSeverity(record.failCount).bg }">
                                {{ record.failCount }} 次
                            </span>
                        </td>
                        <td v-if="isAdmin">
                            <button class="action-btn action-btn--unlock" @click="handleUnlock(record)">
                                <svg viewBox="0 0 20 20" fill="currentColor" class="w-3.5 h-3.5">
                                    <path
                                        d="M10 2a5 5 0 00-5 5v2a2 2 0 00-2 2v5a2 2 0 002 2h10a2 2 0 002-2v-5a2 2 0 00-2-2H7V7a3 3 0 015.905-.75 1 1 0 001.937-.5A5.002 5.002 0 0010 2z" />
                                </svg>
                                解锁
                            </button>
                        </td>
                    </tr>
                </tbody>
            </table>
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

/* ========== Page Root ========== */
.page-root {
    height: 100%;
    background: $bg-page;
    padding: 20px 24px;
    overflow-y: auto;
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
    align-items: center;
    gap: 16px;
}

.title-group {
    display: flex;
    align-items: baseline;
    gap: 12px;
}

.panel-title {
    font-size: 18px;
    font-weight: 600;
    color: $text-primary;
    margin: 0;
    display: flex;
    align-items: center;
    gap: 8px;
}

.title-icon {
    width: 20px;
    height: 20px;
    color: $accent;
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

.btn-back {
    padding: 6px 12px;
}

.spinning {
    animation: spin 0.8s linear infinite;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

/* ========== Notice Bar ========== */
.notice-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    background: rgba(251, 191, 36, 0.08);
    border: 1px solid rgba(251, 191, 36, 0.20);
    border-radius: $radius-md;
    color: #fbbf24;
    font-size: 13px;
}

.notice-icon {
    width: 18px;
    height: 18px;
    flex-shrink: 0;
}

/* ========== Stats Row ========== */
.stats-row {
    display: flex;
    gap: 16px;
}

.stat-card {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 16px 24px;
    background: $bg-card;
    border: 1px solid $border;
    border-radius: $radius-lg;
    transition: all $transition;
    cursor: default;

    &:hover {
        border-color: rgba(255, 255, 255, 0.15);
    }
}

.stat-icon-wrap {
    width: 42px;
    height: 42px;
    border-radius: $radius-md;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
}

.stat-icon--blue {
    background: rgba(59, 130, 246, 0.12);
    color: #60a5fa;
}

.stat-icon--amber {
    background: rgba(251, 191, 36, 0.12);
    color: #fbbf24;
}

.stat-svg {
    width: 20px;
    height: 20px;
}

.stat-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.stat-value {
    font-size: 24px;
    font-weight: 700;
    color: $text-primary;
    line-height: 1;
}

.stat-label {
    font-size: 12px;
    color: $text-muted;
}

/* ========== Data Table ========== */
.table-card {
    background: $bg-card;
    border-radius: $radius-lg;
    border: 1px solid $border;
    overflow: hidden;
    flex: 1;
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

/* ========== IP Cell ========== */
.ip-cell {
    display: flex;
    align-items: center;
    gap: 8px;
}

.ip-icon {
    width: 16px;
    height: 16px;
    color: $accent;
    flex-shrink: 0;
}

.ip-text {
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 13px;
    color: #60a5fa;
    background: transparent;
}

/* ========== Fail Badge ========== */
.fail-badge {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 10px;
    font-size: 12px;
    font-weight: 500;
    border-radius: 999px;
    color: var(--badge-color);
    background: var(--badge-bg);

    &::before {
        content: '';
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: var(--badge-color);
    }
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

    &--unlock {
        background: rgba(52, 211, 153, 0.10);
        color: #34d399;

        &:hover {
            background: rgba(52, 211, 153, 0.20);
        }
    }
}

/* ========== Empty / Loading States ========== */
.empty-state,
.loading-state {
    text-align: center;
    padding: 60px 20px !important;
}

.empty-content,
.loading-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
}

.empty-icon {
    width: 36px;
    height: 36px;
    color: rgba(52, 211, 153, 0.30);
    margin-bottom: 4px;
}

.empty-title {
    font-size: 14px;
    color: $text-secondary;
    font-weight: 500;
}

.empty-desc {
    font-size: 12px;
    color: $text-muted;
}

.spinner-lg {
    width: 24px;
    height: 24px;
    border: 3px solid rgba(255, 255, 255, 0.1);
    border-top-color: $accent;
    border-radius: 50%;
    animation: spin 0.7s linear infinite;
}

/* ========== Utility ========== */
.w-3\.5 {
    width: 14px;
}

.h-3\.5 {
    height: 14px;
}
</style>

<style lang="scss">
// Modal styles - 全局样式用于 Modal.confirm
.dark-theme-modal {
    .ant-modal-content {
        background: #111827;
        border: 1px solid rgba(255, 255, 255, 0.08);
        box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4);
        border-radius: 14px;
    }

    .ant-modal-confirm-title {
        color: rgba(255, 255, 255, 0.92) !important;
    }

    .ant-modal-confirm-content {
        color: rgba(255, 255, 255, 0.60) !important;
    }

    .ant-modal-confirm-btns {
        .ant-btn {
            border-color: rgba(255, 255, 255, 0.08);
            color: rgba(255, 255, 255, 0.60);
            background: transparent;
            border-radius: 6px;
            transition: all 200ms ease;

            &:hover {
                border-color: rgba(255, 255, 255, 0.2);
                color: rgba(255, 255, 255, 0.92);
            }
        }

        .ant-btn-primary {
            background: #3b82f6;
            border-color: #3b82f6;
            color: #fff;

            &:hover {
                background: #2563eb;
                border-color: #2563eb;
            }
        }
    }

    .anticon {
        color: #fbbf24 !important;
    }
}
</style>

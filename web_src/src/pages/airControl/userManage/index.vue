<script setup lang="ts">
import { useRouter } from "vue-router";
import { ref, onMounted, reactive, defineAsyncComponent } from "vue";
import { message } from "ant-design-vue";
import {
    getAllUsersInfo,
    registerUser,
    RegisterBody,
    getLoginPublicKey,
    upateUserStatus,
    getUserInfo,
    getWorkspacePageList,
    addWorkspace,
    updateWorkspace,
    updateUserInfo,
    changeSelfWorkspaceId,
    initUserPassword
} from "@/api/manage";
import { rsaEncrypt } from "@/utils/rsa";
import { ELocalStorageKey, EUserType } from "@/types";
import { IPage } from "@/api/http/type";

// 异步加载 API密钥管理组件
const ApiKeyManage = defineAsyncComponent(() => import('@/pages/airControl/apiKeyManage/index.vue'));
const OidcManage = defineAsyncComponent(() => import('@/pages/airControl/oidcManage/index.vue'));

// 当前标签页
const activeTab = ref('userManage');

// Columns configuration
const columns = [
    {
        title: "用户名",
        dataIndex: "username",
        key: "username",
        className: "titleStyle",
    },
    {
        title: "用户昵称",
        dataIndex: "remark",
        key: "remark",
        className: "titleStyle",
    },
    {
        title: "用户类型",
        dataIndex: "user_type",
        key: "user_type",
        className: "titleStyle",
    },
    {
        title: "角色名称",
        dataIndex: "role_name",
        key: "role_name",
        className: "titleStyle",
    },
    {
        title: "所属组织",
        dataIndex: "workspace_name",
        key: "workspace_name",
        className: "titleStyle",
    },
    {
        title: "状态",
        dataIndex: "status",
        key: "status",
        className: "titleStyle",
    },
    {
        title: "创建时间",
        dataIndex: "create_time",
        key: "create_time",
        className: "titleStyle",
    },
    {
        title: "操作",
        key: "action",
        className: "titleStyle",
    },
];

const dataSource = ref<any[]>([]);
const loading = ref(false);
const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    pageSizeOptions: ["10", "20", "50"],
});


const router = useRouter();
const isAdmin = ref(false);
const currentUserInfo = ref<any>(null);

const getWorkspaceId = () => localStorage.getItem(ELocalStorageKey.WorkspaceId) || "";

// Fetch user list
const fetchUsers = async () => {
    const wid = getWorkspaceId();
    if (!wid) {
        message.error("缺少工作区ID");
        return;
    }
    loading.value = true;
    try {
        const page: IPage = {
            page: pagination.current,
            page_size: pagination.pageSize,
            total: 0
        }
        const res = await getAllUsersInfo(wid, page);
        if (res.code === 0) {
            dataSource.value = res.data.list;
            pagination.total = res.data.pagination.total;
        } else {
            message.error(res.message || "获取用户列表失败");
        }
    } catch (error) {
        console.error(error);
        message.error("获取用户列表出错");
    } finally {
        loading.value = false;
    }
};

const handleTableChange = (pag: any) => {
    pagination.current = pag.current;
    pagination.pageSize = pag.pageSize;
    fetchUsers();
};


const publicKey = ref("");

// 组织/工作空间下拉列表
const workspaceList = ref<any[]>([]);
const getWorkspaceNameById = (id: string) => {
    if (!id) {
        return localStorage.getItem(ELocalStorageKey.WorkspaceName) || '';
    }
    const ws = workspaceList.value.find(item => item.workspace_id === id);
    if (ws) return ws.workspace_name;
    if (id === getWorkspaceId()) {
        return localStorage.getItem(ELocalStorageKey.WorkspaceName) || '';
    }
    return '';
};
const fetchWorkspaceList = async () => {
    try {
        const res = await getWorkspacePageList(1, 100);
        if (res.code === 0) {
            workspaceList.value = res.data.list;
        }
    } catch (e) {
        console.error("加载工作空间列表失败", e);
    }
};

onMounted(async () => {
    fetchUsers();
    try {
        const res = await getLoginPublicKey();
        if (res.code === 0) {
            publicKey.value = res.data;
        }
    } catch (e) {
        console.error("Failed to fetch public key", e);
    }

    try {
        const userRes = await getUserInfo();
        if (userRes.code === 0) {
            currentUserInfo.value = userRes.data;
            if (userRes.data.role_name === '管理员') {
                isAdmin.value = true;
                fetchOrgs();
                fetchWorkspaceList();
            }
        }
    } catch (e) {
        console.error("Failed to fetch user info", e);
    }
});

const goToIpSafe = () => {
    router.push({ name: 'IP安全管理' });
};

// Create User Modal Logic
const isModalVisible = ref(false);
const confirmLoading = ref(false);
const formRef = ref();
const formState = reactive<RegisterBody & { workspace_id?: string }>({
    username: "",
    password: "",
    flag: EUserType.Web,
    workspace_id: "",
});

const rules = {
    username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
    password: [{ required: true, message: "请输入密码", trigger: "blur" }],
    flag: [{ required: true, message: "请选择用户类型", trigger: "change" }],
};

const showModal = () => {
    formState.username = "";
    formState.password = "";
    formState.flag = EUserType.Web;
    formState.workspace_id = "";
    isModalVisible.value = true;
};

const handleOk = async () => {
    if (!formState.username) {
        message.warning("请输入用户名");
        return;
    }
    if (!formState.password) {
        message.warning("请输入密码");
        return;
    }
    if (isAdmin.value && !formState.workspace_id) {
        message.warning("请选择所属组织");
        return;
    }

    try {
        confirmLoading.value = true;
        let submitData = { ...formState };
        if (publicKey.value) {
            const encrypted = rsaEncrypt(formState.password, publicKey.value);
            if (encrypted) {
                submitData.password = encrypted.toString();
            } else {
                message.error("加密失败");
                confirmLoading.value = false;
                return;
            }
        }

        const res = await registerUser(submitData);
        if (res.code === 0) {
            message.success("创建用户成功");
            isModalVisible.value = false;
            fetchUsers();
        } else {
            message.error(res.message || "创建用户失败");
        }
    } catch (error) {
        console.error(error);
    } finally {
        confirmLoading.value = false;
    }
};

const handleCancel = () => {
    isModalVisible.value = false;
};

const formatUserType = (type: number | string) => {
    return Number(type) === EUserType.Pilot ? "Pilot端用户" : "Web端用户";
};

const getFormatStatus = (status: number) => {
    switch (status) {
        case 1:
            return { text: '正常', color: '#34d399', bg: 'rgba(52,211,153,0.12)' };
        case 0:
            return { text: '未启用', color: '#fbbf24', bg: 'rgba(251,191,36,0.12)' };
        case -1:
            return { text: '已删除', color: '#f87171', bg: 'rgba(248,113,113,0.12)' };
        default:
            return { text: '-', color: '#94a3b8', bg: 'transparent' };
    }
};

const handlePageChange = (page: number, pageSize: number) => {
    pagination.current = page
    pagination.pageSize = pageSize
    fetchUsers()
}

const handleStatus = async (record: any, status: number) => {
    const wid = getWorkspaceId();
    if (!wid) {
        message.error("缺少工作区ID");
        return;
    }
    try {
        const res = await upateUserStatus(record.user_id, status);
        if (res.code === 0) {
            message.success("操作成功");
            fetchUsers();
        } else {
            message.error(res.message || "操作失败");
        }
    } catch (error) {
        console.error(error);
        message.error("操作出错");
    }
}

// ==================== 组织管理逻辑 ====================
const orgDataSource = ref<any[]>([]);
const orgLoading = ref(false);
const orgPagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
});

const orgColumns = [
    {
        title: "组织名称",
        dataIndex: "workspace_name",
        key: "workspace_name",
        className: "titleStyle",
    },
    {
        title: "组织描述",
        dataIndex: "workspace_desc",
        key: "workspace_desc",
        className: "titleStyle",
    },
    {
        title: "绑定码",
        dataIndex: "bind_code",
        key: "bind_code",
        className: "titleStyle",
    },
    {
        title: "空间ID",
        dataIndex: "workspace_id",
        key: "workspace_id",
        className: "titleStyle",
    },
    {
        title: "操作",
        key: "action",
        className: "titleStyle",
    },
];

// Fetch organization list
const fetchOrgs = async () => {
    orgLoading.value = true;
    try {
        const res = await getWorkspacePageList(orgPagination.current, orgPagination.pageSize);
        if (res.code === 0) {
            orgDataSource.value = res.data.list;
            orgPagination.total = res.data.pagination.total;
        } else {
            message.error(res.message || "获取组织列表失败");
        }
    } catch (error) {
        console.error(error);
        message.error("获取组织列表出错");
    } finally {
        orgLoading.value = false;
    }
};

const handleOrgTableChange = (pag: any) => {
    orgPagination.current = pag.current;
    orgPagination.pageSize = pag.pageSize;
    fetchOrgs();
};

const handleOrgPageChange = (page: number, pageSize: number) => {
    orgPagination.current = page;
    orgPagination.pageSize = pageSize;
    fetchOrgs();
};

// Add & Edit Org Modal
const isOrgModalVisible = ref(false);
const orgModalType = ref<'add' | 'edit'>('add');
const orgConfirmLoading = ref(false);
const orgFormState = reactive({
    workspace_id: "",
    workspace_name: "",
    workspace_desc: "",
    bind_code: "",
});

const showOrgModal = (type: 'add' | 'edit', record?: any) => {
    orgModalType.value = type;
    if (type === 'add') {
        orgFormState.workspace_id = "";
        orgFormState.workspace_name = "";
        orgFormState.workspace_desc = "";
        orgFormState.bind_code = "";
    } else if (record) {
        orgFormState.workspace_id = record.workspace_id;
        orgFormState.workspace_name = record.workspace_name;
        orgFormState.workspace_desc = record.workspace_desc;
        orgFormState.bind_code = record.bind_code;
    }
    isOrgModalVisible.value = true;
};

const handleOrgCancel = () => {
    isOrgModalVisible.value = false;
};

const handleOrgOk = async () => {
    if (!orgFormState.workspace_name) {
        message.warning("请输入组织名称");
        return;
    }
    if (!orgFormState.workspace_desc) {
        message.warning("请输入组织描述");
        return;
    }
    if (!orgFormState.bind_code) {
        message.warning("请输入绑定码");
        return;
    }

    // 校验绑定码的唯一性 (本地校验)
    const isDuplicate = orgDataSource.value.some(item => 
        item.bind_code === orgFormState.bind_code && 
        (orgModalType.value === 'add' || item.workspace_id !== orgFormState.workspace_id)
    );
    if (isDuplicate) {
        message.error("绑定码已存在，请输入唯一的绑定码");
        return;
    }

    try {
        orgConfirmLoading.value = true;
        if (orgModalType.value === 'add') {
            const res = await addWorkspace({
                workspace_name: orgFormState.workspace_name,
                workspace_desc: orgFormState.workspace_desc,
                bind_code: orgFormState.bind_code,
            });
            if (res.code === 0) {
                message.success("新增组织成功");
                isOrgModalVisible.value = false;
                fetchOrgs();
            } else {
                message.error(res.message || "新增组织失败");
            }
        } else {
            const res = await updateWorkspace({
                workspace_id: orgFormState.workspace_id,
                workspace_name: orgFormState.workspace_name,
                workspace_desc: orgFormState.workspace_desc,
                bind_code: orgFormState.bind_code,
            });
            if (res.code === 0) {
                message.success("修改组织成功");
                isOrgModalVisible.value = false;
                fetchOrgs();
            } else {
                message.error(res.message || "修改组织失败");
            }
        }
    } catch (error) {
        console.error(error);
        message.error("保存失败");
    } finally {
        orgConfirmLoading.value = false;
    }
};

// ==================== 修改用户昵称逻辑 ====================
const isNicknameModalVisible = ref(false);
const nicknameConfirmLoading = ref(false);
const editNicknameState = reactive({
    user_id: "",
    username: "",
    remark: "",
});

const showNicknameModal = (record: any) => {
    editNicknameState.user_id = record.user_id;
    editNicknameState.username = record.username;
    editNicknameState.remark = record.remark || "";
    isNicknameModalVisible.value = true;
};

const handleNicknameCancel = () => {
    isNicknameModalVisible.value = false;
};

const handleNicknameOk = async () => {
    const wid = getWorkspaceId();
    if (!wid) {
        message.error("缺少工作区ID");
        return;
    }
    if (!editNicknameState.user_id) return;

    try {
        nicknameConfirmLoading.value = true;
        const res = await updateUserInfo(wid, editNicknameState.user_id, {
            remark: editNicknameState.remark,
        });
        if (res.code === 0) {
            message.success("修改昵称成功");
            isNicknameModalVisible.value = false;
            fetchUsers();
        } else {
            message.error(res.message || "修改昵称失败");
        }
    } catch (error) {
        console.error(error);
        message.error("修改昵称出错");
    } finally {
        nicknameConfirmLoading.value = false;
    }
};

// ==================== 管理员修改用户逻辑 ====================
const isEditUserModalVisible = ref(false);
const editUserConfirmLoading = ref(false);
const editUserFormState = reactive({
    user_id: "",
    username: "",
    remark: "",
    flag: 1,
    workspace_id: ""
});

const showEditUserModal = (record: any) => {
    editUserFormState.user_id = record.user_id;
    editUserFormState.username = record.username;
    editUserFormState.remark = record.remark || "";
    editUserFormState.flag = Number(record.user_type) || EUserType.Web;
    editUserFormState.workspace_id = getWorkspaceId() || "";
    isEditUserModalVisible.value = true;
};

const handleEditUserCancel = () => {
    isEditUserModalVisible.value = false;
};

const handleEditUserOk = async () => {
    if (!editUserFormState.remark) {
        message.warning("请输入用户昵称");
        return;
    }
    if (isAdmin.value && !editUserFormState.workspace_id) {
        message.warning("请选择所属组织");
        return;
    }
    const wid = getWorkspaceId();
    if (!wid) {
        message.error("缺少工作区ID");
        return;
    }

    try {
        editUserConfirmLoading.value = true;
        const res = await updateUserInfo(editUserFormState.workspace_id, editUserFormState.user_id, {
            remark: editUserFormState.remark,
            flag: editUserFormState.flag
        });
        if (res.code === 0) {
            message.success("修改用户成功");
            isEditUserModalVisible.value = false;
            fetchUsers();
        } else {
            message.error(res.message || "修改用户失败");
        }
    } catch (error) {
        console.error(error);
        message.error("修改用户出错");
    } finally {
        editUserConfirmLoading.value = false;
    }
};

const handleSwitchWorkspace = async (workspaceId: string) => {
    if (!workspaceId) return;
    try {
        message.loading({ content: "正在切换组织...", key: "switch_ws", duration: 0 });
        const res = await changeSelfWorkspaceId(workspaceId);
        if (res.code === 0) {
            message.success({ content: "切换组织成功，正在刷新...", key: "switch_ws", duration: 1.5 });
            
            // 保存新的token和workspaceID到localStorage
            if (res.data && res.data.access_token) {
                localStorage.setItem(ELocalStorageKey.Token, res.data.access_token);
            }
            if (res.data && res.data.workspace_id) {
                localStorage.setItem(ELocalStorageKey.WorkspaceId, res.data.workspace_id);
            }
            
            // 顺便把工作空间的名字也同步更新到 localStorage
            const targetWs = workspaceList.value.find(item => item.workspace_id === workspaceId);
            if (targetWs && targetWs.workspace_name) {
                localStorage.setItem(ELocalStorageKey.WorkspaceName, targetWs.workspace_name);
            }
            
            // 1秒后刷新页面以加载新的组织上下文
            setTimeout(() => {
                window.location.reload();
            }, 1000);
        } else {
            message.error({ content: res.message || "切换组织失败", key: "switch_ws", duration: 3 });
        }
    } catch (e) {
        console.error(e);
        message.error({ content: "切换组织出错", key: "switch_ws", duration: 3 });
    }
};

// 初始化密码逻辑
const initPasswordVisible = ref(false);
const initPasswordConfirmLoading = ref(false);
const initPasswordFormRef = ref();
const initPasswordFormState = reactive({
    user_id: "",
    password: "",
});

const openInitPasswordModal = (record: any) => {
    initPasswordFormState.user_id = record.user_id;
    initPasswordFormState.password = "";
    initPasswordVisible.value = true;
};

const handleInitPasswordOk = async () => {
    if (!initPasswordFormState.password) {
        message.warning("请输入新密码");
        return;
    }
    try {
        initPasswordConfirmLoading.value = true;
        let passwordString = "";
        
        if (publicKey.value) {
            const encrypted = rsaEncrypt(initPasswordFormState.password, publicKey.value);
            if (encrypted) {
                passwordString = encrypted.toString();
            } else {
                message.error("加密失败");
                initPasswordConfirmLoading.value = false;
                return;
            }
        } else {
            message.error("无法获取公钥，重置密码失败");
            initPasswordConfirmLoading.value = false;
            return;
        }

        const res = await initUserPassword(initPasswordFormState.user_id, passwordString);
        if (res.code === 0) {
            message.success("初始化密码成功");
            initPasswordVisible.value = false;
            fetchUsers();
        } else {
            message.error(res.message || "初始化密码失败");
        }
    } catch (error) {
        console.error(error);
        message.error("初始化密码出错");
    } finally {
        initPasswordConfirmLoading.value = false;
    }
};
</script>

<template>
    <div class="page-root">
        <!-- Tabs 导航 -->
        <div class="tabs-bar">
            <button :class="['tab-btn', { active: activeTab === 'userManage' }]" @click="activeTab = 'userManage'">
                <svg class="tab-icon" viewBox="0 0 20 20" fill="currentColor">
                    <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
                </svg>
                用户管理
            </button>
            <button :class="['tab-btn', { active: activeTab === 'apiKeyManage' }]" @click="activeTab = 'apiKeyManage'">
                <svg class="tab-icon" viewBox="0 0 20 20" fill="currentColor">
                    <path fill-rule="evenodd"
                        d="M18 8a6 6 0 01-7.743 5.743L10 14l-1 1-1 1H6v2H2v-4l4.257-4.257A6 6 0 1118 8zm-6-4a1 1 0 100 2 2 2 0 012 2 1 1 0 102 0 4 4 0 00-4-4z"
                        clip-rule="evenodd" />
                </svg>
                API密钥管理
            </button>
            <button  :class="['tab-btn', { active: activeTab === 'orgManage' }]" @click="activeTab = 'orgManage'">
                <svg class="tab-icon" viewBox="0 0 20 20" fill="currentColor">
                    <path fill-rule="evenodd"
                        d="M4 4a2 2 0 012-2h8a2 2 0 012 2v12a1 1 0 110 2h-3a1 1 0 01-1-1v-2a1 1 0 00-1-1H9a1 1 0 00-1 1v2a1 1 0 01-1 1H4a1 1 0 110-2V4zm3 1h2v2H7V5zm2 4H7v2h2V9zm2-4h2v2h-2V5zm2 4h-2v2h2V9z"
                        clip-rule="evenodd" />
                </svg>
                组织管理
            </button>
            <button  :class="['tab-btn', { active: activeTab === 'oidcManage' }]" @click="activeTab = 'oidcManage'">
                <svg class="tab-icon" viewBox="0 0 20 20" fill="currentColor">
                    <path fill-rule="evenodd"
                        d="M10 1a4.5 4.5 0 00-4.5 4.5V9H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-.5V5.5A4.5 4.5 0 0010 1zm3 8V5.5a3 3 0 10-6 0V9h6z"
                        clip-rule="evenodd" />
                </svg>
                OIDC管理
            </button>
        </div>

        <!-- 用户管理面板 -->
        <div v-show="activeTab === 'userManage'" class="tab-panel">
            <!-- 顶部工具栏 -->
            <div class="toolbar">
                <div class="toolbar-left">
                    <h2 class="panel-title">用户列表</h2>
                    <span class="record-count">共 {{ pagination.total }} 条</span>
                </div>
                <div class="toolbar-right">
                    <!-- 管理员极速切换组织下拉选择框 -->
                    <div v-if="isAdmin" class="org-quick-switcher" style="margin-right: 12px; display: inline-flex; align-items: center;">
                        <span style="color: rgba(255, 255, 255, 0.45); font-size: 13px; margin-right: 8px;">当前组织:</span>
                        <select :value="getWorkspaceId()" @change="handleSwitchWorkspace(($event.target as HTMLSelectElement).value)" class="form-input" style="width: 160px; background-color: #1e293b; color: #fff; border: 1px solid rgba(255, 255, 255, 0.08); height: 32px; padding: 0 8px; border-radius: 6px; font-size: 12px; cursor: pointer;">
                            <option v-for="ws in workspaceList" :key="ws.workspace_id" :value="ws.workspace_id">
                                {{ ws.workspace_name }}
                            </option>
                        </select>
                    </div>
                    <button v-if="isAdmin" class="btn btn-ghost" @click="goToIpSafe">
                        <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd"
                                d="M2.166 4.999A11.954 11.954 0 0010 1.944 11.954 11.954 0 0017.834 5C18.266 6.5 18.5 8.088 18.5 9.72c0 5.21-3.816 9.582-8.5 10.28C5.316 19.302 1.5 14.93 1.5 9.72c0-1.632.234-3.22.666-4.721zM10 12a2 2 0 100-4 2 2 0 000 4z"
                                clip-rule="evenodd" />
                        </svg>
                        IP安全管理
                    </button>
                    <button class="btn btn-primary" @click="showModal">
                        <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd"
                                d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
                                clip-rule="evenodd" />
                        </svg>
                        新增用户
                    </button>
                </div>
            </div>

            <!-- 表格区域 -->
            <div class="table-card">
                <a-table :columns="columns" :data-source="dataSource" :pagination="false" :loading="loading"
                    @change="handleTableChange" row-key="user_id" :scroll="{ x: '100%', y: 'calc(100vh - 320px)' }">
                    <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'user_type'">
                            <span :class="['type-badge', Number(record.user_type) === EUserType.Pilot ? 'type-badge--pilot' : 'type-badge--web']">
                                {{ formatUserType(record.user_type) }}
                            </span>
                        </template>
                        <template v-if="column.key === 'status'">
                            <span class="status-dot"
                                :style="{ '--dot-color': getFormatStatus(record.status).color, '--dot-bg': getFormatStatus(record.status).bg }">
                                {{ getFormatStatus(record.status).text }}
                            </span>
                        </template>
                        <template v-if="column.key === 'workspace_name'">
                            <span class="text-white">{{ getWorkspaceNameById(getWorkspaceId()) || '未分配' }}</span>
                        </template>
                        <template v-if="column.key === 'create_time'">
                            <span class="text-muted">{{ record.create_time ? new
                                Date(record.create_time).toLocaleString() : '-' }}</span>
                        </template>
                        <template v-if="column.key === 'action'">
                            <div class="action-btns">
                                <button v-if="currentUserInfo && record.user_id === currentUserInfo.user_id" class="action-btn action-btn--enable" @click="showNicknameModal(record)">修改昵称</button>
                                <button v-if="isAdmin" class="action-btn action-btn--enable" @click="showEditUserModal(record)">修改用户</button>
                                <button v-if="isAdmin" class="action-btn action-btn--enable" @click="openInitPasswordModal(record)">初始化密码</button>
                                <button v-if="record.status === 0" class="action-btn action-btn--enable"
                                    @click="handleStatus(record, 1)">启用</button>
                                <button v-if="record.status === 1" class="action-btn action-btn--disable"
                                    @click="handleStatus(record, 0)">禁用</button>
                                <a-popconfirm title="确定删除该用户吗？" ok-text="确定" cancel-text="取消"
                                    @confirm="handleStatus(record, -1)">
                                    <button class="action-btn action-btn--delete">删除</button>
                                </a-popconfirm>
                            </div>
                        </template>
                    </template>
                </a-table>
                <div class="pagination-bar">
                    <a-pagination v-model:current="pagination.current" v-model:page-size="pagination.pageSize"
                        :total="pagination.total" :show-total="(total: number) => `共 ${total} 条记录`" show-quick-jumper
                        @change="handlePageChange" @showSizeChange="handlePageChange" />
                </div>
            </div>
        </div>

        <!-- API 密钥管理面板 -->
        <div v-show="activeTab === 'apiKeyManage'" class="tab-panel">
            <ApiKeyManage />
        </div>

        <!-- 组织管理面板 -->
        <div v-show="activeTab === 'orgManage'" class="tab-panel">
            <!-- 顶部工具栏 -->
            <div class="toolbar">
                <div class="toolbar-left">
                    <h2 class="panel-title">组织列表</h2>
                    <span class="record-count">共 {{ orgPagination.total }} 条</span>
                </div>
                <div class="toolbar-right">
                    <button class="btn btn-primary" @click="showOrgModal('add')">
                        <svg class="btn-icon" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd"
                                d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z"
                                clip-rule="evenodd" />
                        </svg>
                        新增组织
                    </button>
                </div>
            </div>

            <!-- 表格区域 -->
            <div class="table-card">
                <a-table :columns="orgColumns" :data-source="orgDataSource" :pagination="false" :loading="orgLoading"
                    @change="handleOrgTableChange" row-key="workspace_id" :scroll="{ x: '100%', y: 'calc(100vh - 320px)' }">
                    <template #bodyCell="{ column, record }">
                        <template v-if="column.key === 'action'">
                            <div class="action-btns">
                                <button class="action-btn action-btn--enable" @click="showOrgModal('edit', record)">修改</button>
                                <button class="action-btn action-btn--enable" style="background: linear-gradient(135deg, #3b82f6, #1d4ed8);" @click="handleSwitchWorkspace(record.workspace_id)">进入组织</button>
                            </div>
                        </template>
                    </template>
                </a-table>
                <div class="pagination-bar">
                    <a-pagination v-model:current="orgPagination.current" :page-size="orgPagination.pageSize"
                        :total="orgPagination.total" :show-total="(total: number) => `共 ${total} 条记录`" show-quick-jumper
                        @change="handleOrgPageChange" />
                </div>
            </div>
        </div>

        <!-- 新增/修改组织弹窗 -->
        <div v-if="isOrgModalVisible" class="modal-overlay" @click.self="handleOrgCancel">
            <div class="modal-box">
                <div class="modal-header">
                    <h3 class="modal-title">{{ orgModalType === 'add' ? '新增组织' : '修改组织' }}</h3>
                    <button class="modal-close" @click="handleOrgCancel">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <form class="modal-body" @submit.prevent="handleOrgOk">
                    <div class="form-group">
                        <label class="form-label">组织名称 <span class="required">*</span></label>
                        <input v-model="orgFormState.workspace_name" type="text" placeholder="请输入组织名称" autocomplete="off"
                            class="form-input" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">组织描述 <span class="required">*</span></label>
                        <input v-model="orgFormState.workspace_desc" type="text" placeholder="请输入组织描述" autocomplete="off"
                            class="form-input" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">绑定码 <span class="required">*</span></label>
                        <input v-model="orgFormState.bind_code" type="text" placeholder="请输入绑定码" autocomplete="off"
                            class="form-input" :disabled="orgModalType === 'edit'" />
                    </div>
                </form>
                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="handleOrgCancel">取消</button>
                    <button class="btn btn-primary" @click="handleOrgOk" :disabled="orgConfirmLoading">
                        <span v-if="orgConfirmLoading" class="spinner"></span>
                        确定
                    </button>
                </div>
            </div>
        </div>
        <!-- OIDC 管理面板 -->
        <div v-if="activeTab === 'oidcManage'" class="tab-panel">
            <OidcManage />
        </div>
        <!-- 新增用户弹窗 -->
        <div v-if="isModalVisible" class="modal-overlay" @click.self="handleCancel">
            <div class="modal-box">
                <div class="modal-header">
                    <h3 class="modal-title">新增用户</h3>
                    <button class="modal-close" @click="handleCancel">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <form class="modal-body" @submit.prevent="handleOk">
                    <div class="form-group">
                        <label class="form-label">用户名 <span class="required">*</span></label>
                        <input v-model="formState.username" type="text" placeholder="请输入用户名" autocomplete="off"
                            class="form-input" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">密码 <span class="required">*</span></label>
                        <input v-model="formState.password" type="password" placeholder="请输入密码"
                            autocomplete="new-password" class="form-input" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">用户类型</label>
                        <div class="radio-group">
                            <label class="radio-item" :class="{ checked: formState.flag === 2 }">
                                <input type="radio" v-model="formState.flag" :value="2" class="sr-only" />
                                <span class="radio-dot"></span>
                                Pilot端用户
                            </label>
                            <label class="radio-item" :class="{ checked: formState.flag === 1 }">
                                <input type="radio" v-model="formState.flag" :value="1" class="sr-only" />
                                <span class="radio-dot"></span>
                                Web端用户
                            </label>
                        </div>
                    </div>
                    <div v-if="isAdmin" class="form-group">
                        <label class="form-label">所属组织 <span class="required">*</span></label>
                        <select v-model="formState.workspace_id" class="form-input" style="background-color: #1e293b; color: #fff; border: 1px solid rgba(255, 255, 255, 0.08);">
                            <option value="">-- 请选择所属组织 --</option>
                            <option v-for="ws in workspaceList" :key="ws.workspace_id" :value="ws.workspace_id">
                                {{ ws.workspace_name }}
                            </option>
                        </select>
                    </div>
                </form>
                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="handleCancel">取消</button>
                    <button class="btn btn-primary" @click="handleOk" :disabled="confirmLoading">
                        <span v-if="confirmLoading" class="spinner"></span>
                        确定
                    </button>
                </div>
            </div>
        </div>

        <!-- 修改昵称弹窗 -->
        <div v-if="isNicknameModalVisible" class="modal-overlay" @click.self="handleNicknameCancel">
            <div class="modal-box">
                <div class="modal-header">
                    <h3 class="modal-title">修改用户昵称</h3>
                    <button class="modal-close" @click="handleNicknameCancel">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <form class="modal-body" @submit.prevent="handleNicknameOk">
                    <div class="form-group">
                        <label class="form-label">用户名</label>
                        <input v-model="editNicknameState.username" type="text" disabled class="form-input opacity-60 cursor-not-allowed" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">用户昵称 <span class="required">*</span></label>
                        <input v-model="editNicknameState.remark" type="text" placeholder="请输入新的用户昵称" autocomplete="off" class="form-input" />
                    </div>
                </form>
                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="handleNicknameCancel">取消</button>
                    <button class="btn btn-primary" @click="handleNicknameOk" :disabled="nicknameConfirmLoading">
                        <span v-if="nicknameConfirmLoading" class="spinner"></span>
                        确定
                    </button>
                </div>
            </div>
        </div>

        <!-- 修改用户弹窗 -->
        <div v-if="isEditUserModalVisible" class="modal-overlay" @click.self="handleEditUserCancel">
            <div class="modal-box">
                <div class="modal-header">
                    <h3 class="modal-title">修改用户</h3>
                    <button class="modal-close" @click="handleEditUserCancel">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <form class="modal-body" @submit.prevent="handleEditUserOk">
                    <div class="form-group">
                        <label class="form-label">用户名</label>
                        <input v-model="editUserFormState.username" type="text" disabled class="form-input opacity-60 cursor-not-allowed" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">用户昵称 <span class="required">*</span></label>
                        <input v-model="editUserFormState.remark" type="text" placeholder="请输入用户昵称" autocomplete="off" class="form-input" />
                    </div>
                    <div class="form-group">
                        <label class="form-label">用户类型</label>
                        <div class="radio-group">
                            <label class="radio-item" :class="{ checked: editUserFormState.flag === 2 }">
                                <input type="radio" v-model="editUserFormState.flag" :value="2" class="sr-only" />
                                <span class="radio-dot"></span>
                                Pilot端用户
                            </label>
                            <label class="radio-item" :class="{ checked: editUserFormState.flag === 1 }">
                                <input type="radio" v-model="editUserFormState.flag" :value="1" class="sr-only" />
                                <span class="radio-dot"></span>
                                Web端用户
                            </label>
                        </div>
                    </div>
                    <div v-if="isAdmin" class="form-group">
                        <label class="form-label">所属组织 <span class="required">*</span></label>
                        <select v-model="editUserFormState.workspace_id" class="form-input" style="background-color: #1e293b; color: #fff; border: 1px solid rgba(255, 255, 255, 0.08);">
                            <option value="">-- 请选择所属组织 --</option>
                            <option v-for="ws in workspaceList" :key="ws.workspace_id" :value="ws.workspace_id">
                                {{ ws.workspace_name }}
                            </option>
                        </select>
                    </div>
                </form>
                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="handleEditUserCancel">取消</button>
                    <button class="btn btn-primary" @click="handleEditUserOk" :disabled="editUserConfirmLoading">
                        <span v-if="editUserConfirmLoading" class="spinner"></span>
                        确定
                    </button>
                </div>
            </div>
        </div>

        <!-- Init Password Modal -->
        <div v-if="initPasswordVisible" class="modal-overlay" @click.self="initPasswordVisible = false">
            <div class="modal-box">
                <div class="modal-header">
                    <h3 class="modal-title">初始化密码</h3>
                    <button class="modal-close" @click="initPasswordVisible = false">
                        <svg viewBox="0 0 20 20" fill="currentColor" class="w-5 h-5">
                            <path fill-rule="evenodd"
                                d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                                clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
                <form class="modal-body" @submit.prevent="handleInitPasswordOk">
                    <div class="form-group">
                        <label class="form-label">新密码 <span class="required">*</span></label>
                        <input v-model="initPasswordFormState.password" type="password" placeholder="请输入新密码" autocomplete="new-password"
                            class="form-input" />
                    </div>
                </form>
                <div class="modal-footer">
                    <button class="btn btn-ghost" @click="initPasswordVisible = false">取消</button>
                    <button class="btn btn-primary" @click="handleInitPasswordOk" :disabled="initPasswordConfirmLoading">
                        <span v-if="initPasswordConfirmLoading" class="spinner"></span>
                        确定
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

/* ========== Page Root ========== */
.page-root {
    height: 100%;
    background: $bg-page;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

/* ========== Tabs Bar ========== */
.tabs-bar {
    display: flex;
    gap: 4px;
    padding: 16px 24px 0;
    border-bottom: 1px solid $border;
    background: $bg-card;
}

.tab-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 20px;
    font-size: 13px;
    font-weight: 500;
    color: $text-secondary;
    background: transparent;
    border: none;
    border-bottom: 2px solid transparent;
    cursor: pointer;
    transition: color $transition, border-color $transition, background $transition;
    margin-bottom: -1px;

    &:hover {
        color: $text-primary;
        background: $bg-hover;
    }

    &.active {
        color: $accent;
        border-bottom-color: $accent;
    }
}

.tab-icon {
    width: 16px;
    height: 16px;
}

/* ========== Tab Panel ========== */
.tab-panel {
    flex: 1;
    overflow: auto;
    padding: 20px 24px;
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

/* ========== Table Card ========== */
.table-card {
    background: $bg-card;
    border-radius: $radius-lg;
    border: 1px solid $border;
    overflow: hidden;
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
        background: rgba(6, 182, 212, 0.12);
        color: #22d3ee;
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

.text-muted {
    color: $text-secondary;
    font-size: 13px;
}

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

    &--disable {
        background: rgba(251, 191, 36, 0.12);
        color: #fbbf24;

        &:hover {
            background: rgba(251, 191, 36, 0.22);
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
    gap: 6px;
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
        border-color: rgba(255, 255, 255, 0.05);
        cursor: not-allowed;
    }
}

.radio-group {
    display: flex;
    gap: 16px;
}

.radio-item {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    font-size: 13px;
    color: $text-secondary;
    background: $bg-surface;
    border: 1px solid $border;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: all $transition;

    &:hover {
        border-color: rgba(255, 255, 255, 0.15);
    }

    &.checked {
        color: $accent;
        border-color: $accent;
        background: rgba(59, 130, 246, 0.08);
    }
}

.radio-dot {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    border: 2px solid $border;
    transition: all $transition;
    position: relative;

    .checked & {
        border-color: $accent;

        &::after {
            content: '';
            position: absolute;
            inset: 2px;
            border-radius: 50%;
            background: $accent;
        }
    }
}

.sr-only {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border-width: 0;
}

.spinner {
    width: 14px;
    height: 14px;
    border: 2px solid rgba(255, 255, 255, 0.3);
    border-top-color: #fff;
    border-radius: 50%;
    animation: spin 0.6s linear infinite;
    margin-right: 4px;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
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

:deep(.ant-spin-container) {
    &::after {
        background: $bg-card !important;
    }
}
</style>

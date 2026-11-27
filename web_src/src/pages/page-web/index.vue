<template>
  <div class="web-login-container">
    <!-- 动态背景极光光晕 -->
    <div class="ambient-background">
      <div class="glow-orb orb-cyan"></div>
      <div class="glow-orb orb-purple"></div>
      <div class="grid-overlay"></div>
    </div>

    <!-- 登录面板卡片 -->
    <div class="login-card">
      <div class="login-header">
        <div class="brand-logo-badge">
          <div class="drone-logo-icon">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" class="svg-icon">
              <circle cx="6" cy="6" r="3" stroke="#00f2fe" stroke-width="1.5"/>
              <circle cx="18" cy="6" r="3" stroke="#00f2fe" stroke-width="1.5"/>
              <circle cx="6" cy="18" r="3" stroke="#00f2fe" stroke-width="1.5"/>
              <circle cx="18" cy="18" r="3" stroke="#00f2fe" stroke-width="1.5"/>
              <path d="M8.5 8.5L15.5 15.5M15.5 8.5L8.5 15.5" stroke="#00f2fe" stroke-width="1.5" stroke-linecap="round"/>
              <circle cx="12" cy="12" r="2" fill="#00f2fe"/>
            </svg>
          </div>
        </div>
        <h1 class="login-title">{{ webTitle }}</h1>
        <p class="login-subtitle">AETHERIS FLIGHT CORE</p>
      </div>

      <a-form layout="vertical" :model="formState" @finish="onSubmit" class="login-form">
        <a-form-item class="form-item-custom">
          <a-input
            v-model:value="formState.username"
            placeholder="用户名"
            size="large"
            class="hud-input"
            :bordered="false"
          >
            <template #prefix>
              <UserOutlined class="input-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item class="form-item-custom">
          <a-input-password
            v-model:value="formState.password"
            placeholder="密码"
            size="large"
            class="hud-input"
            :bordered="false"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item class="form-item-custom">
          <div class="captcha-group">
            <a-input
              v-model:value="formState.captcha_code"
              placeholder="验证码"
              size="large"
              class="hud-input captcha-input"
              :bordered="false"
            >
              <template #prefix>
                <LockOutlined class="input-icon" />
              </template>
            </a-input>
            <div class="captcha-image-wrapper" @click="refreshCaptcha" title="点击刷新验证码">
              <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
              <div v-else class="captcha-placeholder">加载中...</div>
            </div>
          </div>
        </a-form-item>

        <a-form-item class="action-item">
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            block
            :loading="loading"
            :disabled="!formState.username || !formState.password || !formState.captcha_code"
            class="login-btn"
          >
            登 录
          </a-button>
        </a-form-item>
      </a-form>

      <div class="login-footer">
        <span class="security-text"><span class="dot">•</span> 安全连接已加密</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { getLoginCaptcha, getLoginPublicKey, login, LoginBody } from '@/api/manage'
import { rsaEncrypt } from '@/utils/rsa'
import { ELocalStorageKey, EUserType } from '@/types'
import { useMyStore } from '@/store'

const router = useRouter()
const store = useMyStore()
const webTitle = computed(() => store.state.webTitle)
const loading = ref(false)
const captchaImage = ref('')
const publicKey = ref('')

const formState = reactive<LoginBody>({
  username: '',
  password: '',
  captcha_code: '',
  captcha_key: '',
  flag: EUserType.Web
})

const refreshCaptcha = async () => {
  try {
    const res = await getLoginCaptcha()
    if (res && res.code === 0 && res.data) {
      formState.captcha_key = res.data.captcha_key
      captchaImage.value = res.data.image
    }
  } catch (e) {
    console.error('获取验证码失败', e)
  }
}

const fetchPublicKey = async () => {
  try {
    const res = await getLoginPublicKey()
    if (res && res.data) {
      publicKey.value = res.data
    }
  } catch (e) {
    console.error('获取公钥失败', e)
  }
}

onMounted(() => {
  refreshCaptcha()
  fetchPublicKey()
})

const onSubmit = async () => {
  if (!formState.username || !formState.password || !formState.captcha_code) {
    message.warning('请填写完整的登录信息')
    return
  }

  loading.value = true
  try {
    const encryptedPassword = rsaEncrypt(formState.password, publicKey.value) || formState.password
    const payload: LoginBody = {
      ...formState,
      password: encryptedPassword
    }

    const res = await login(payload)
    if (res && res.code === 0 && res.data) {
      localStorage.setItem(ELocalStorageKey.Token, res.data.access_token)
      if (res.data.workspace_id) {
        localStorage.setItem(ELocalStorageKey.WorkspaceId, res.data.workspace_id)
      }
      if (res.data.user_id) {
        localStorage.setItem(ELocalStorageKey.UserId, res.data.user_id)
      }
      if (res.data.username) {
        localStorage.setItem(ELocalStorageKey.Username, res.data.username)
      }
      localStorage.setItem(ELocalStorageKey.Flag, EUserType.Web.toString())
      message.success('登录成功！')
      router.push('/airHome')
    } else {
      message.error(res?.message || '登录失败，请检查账号密码或验证码')
      refreshCaptcha()
    }
  } catch (err: any) {
    message.error(err?.message || '登录请求发生异常')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.web-login-container {
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #060913;
  overflow: hidden;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;

  .ambient-background {
    position: absolute;
    inset: 0;
    pointer-events: none;

    .glow-orb {
      position: absolute;
      border-radius: 50%;
      filter: blur(120px);
      opacity: 0.35;
    }
    .orb-cyan {
      top: -15%;
      left: 15%;
      width: 550px;
      height: 550px;
      background: radial-gradient(circle, #00f2fe, #0077ff);
    }
    .orb-purple {
      bottom: -15%;
      right: 15%;
      width: 500px;
      height: 500px;
      background: radial-gradient(circle, #4a00e0, #8e2de2);
    }
    .grid-overlay {
      position: absolute;
      inset: 0;
      background-image: radial-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px);
      background-size: 36px 36px;
    }
  }

  .login-card {
    position: relative;
    width: 480px;
    padding: 48px 44px 36px;
    background: rgba(13, 20, 36, 0.85);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border: 1px solid rgba(0, 242, 254, 0.18);
    border-radius: 20px;
    box-shadow: 0 24px 70px rgba(0, 0, 0, 0.65);
    z-index: 10;
  }

  .login-header {
    text-align: center;
    margin-bottom: 32px;

    .brand-logo-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 68px;
      height: 68px;
      background: rgba(8, 14, 28, 0.9);
      border: 1.5px solid rgba(0, 242, 254, 0.45);
      border-radius: 18px;
      margin-bottom: 16px;
      box-shadow: 0 0 24px rgba(0, 242, 254, 0.3);

      .drone-logo-icon {
        width: 38px;
        height: 38px;
        .svg-icon {
          width: 100%;
          height: 100%;
        }
      }
    }

    .login-title {
      font-size: 24px;
      font-weight: 700;
      color: #ffffff;
      margin: 0;
      letter-spacing: 2.5px;
    }

    .login-subtitle {
      font-size: 13px;
      font-weight: 600;
      color: #38bdf8;
      margin-top: 6px;
      letter-spacing: 3px;
    }
  }

  .login-form {
    .form-item-custom {
      margin-bottom: 20px;
    }

    .hud-input {
      background: rgba(18, 28, 48, 0.8) !important;
      border: 1px solid rgba(255, 255, 255, 0.1) !important;
      border-radius: 10px !important;
      color: #ffffff !important;
      padding: 10px 16px;
      transition: all 0.3s ease;

      &:hover, &:focus-within {
        border-color: rgba(0, 242, 254, 0.5) !important;
        box-shadow: 0 0 14px rgba(0, 242, 254, 0.2) !important;
      }

      :deep(input) {
        background: transparent !important;
        color: #ffffff !important;
        font-size: 15px;
        &::placeholder {
          color: #64748b;
        }
      }

      .input-icon {
        color: #38bdf8;
        font-size: 17px;
        margin-right: 10px;
      }
    }

    .captcha-group {
      display: flex;
      gap: 12px;

      .captcha-input {
        flex: 1;
      }

      .captcha-image-wrapper {
        width: 136px;
        height: 46px;
        border-radius: 10px;
        overflow: hidden;
        border: 1px solid rgba(255, 255, 255, 0.12);
        cursor: pointer;
        background: #0f172a;
        display: flex;
        align-items: center;
        justify-content: center;

        img {
          width: 100%;
          height: 100%;
          object-fit: contain;
        }

        .captcha-placeholder {
          font-size: 12px;
          color: #64748b;
        }
      }
    }

    .login-btn {
      height: 48px;
      font-size: 16px;
      font-weight: 600;
      letter-spacing: 8px;
      border-radius: 10px;
      background: linear-gradient(135deg, #0ea5e9, #0284c7);
      border: none;
      box-shadow: 0 4px 18px rgba(14, 165, 233, 0.35);

      &:hover {
        background: linear-gradient(135deg, #38bdf8, #0ea5e9);
        box-shadow: 0 4px 24px rgba(56, 189, 248, 0.45);
      }
    }
  }

  .login-footer {
    text-align: center;
    margin-top: 20px;

    .security-text {
      font-size: 12px;
      color: #38bdf8;
      opacity: 0.85;

      .dot {
        color: #38bdf8;
        margin-right: 4px;
      }
    }
  }
}
</style>

<template>
  <div class="pilot-login-container">
    <!-- Starfield/Atmospheric Background Elements -->
    <div class="ambient-background">
      <div class="glow-orb orb-primary"></div>
      <div class="glow-orb orb-secondary"></div>
      <div class="grid-overlay"></div>
    </div>

    <!-- Glassmorphic HUD Login Card -->
    <div class="hud-card">
      <div class="hud-header">
        <div class="brand-container">
          <img :src="djiLogo" alt="DJI Logo" class="brand-logo" />
        </div>
        <h1 class="hud-title">手持遥控器注册登录</h1>
      </div>

      <a-form layout="vertical" :model="formState" class="auth-form">
        <a-form-item class="form-item-custom">
          <div class="input-wrapper">
            <a-input v-model:value="formState.username" placeholder="用户名" size="large" class="hud-input"
              :bordered="false">
              <template #prefix>
                <UserOutlined class="icon-primary" />
              </template>
            </a-input>
          </div>
        </a-form-item>

        <a-form-item class="form-item-custom">
          <div class="input-wrapper">
            <a-input v-model:value="formState.password" type="password" placeholder="密码" size="large" class="hud-input"
              :bordered="false">
              <template #prefix>
                <LockOutlined class="icon-primary" />
              </template>
            </a-input>
          </div>
        </a-form-item>

        <a-form-item class="form-item-custom">
          <div class="captcha-module">
            <div class="input-wrapper captcha-input-wrapper">
              <a-input v-model:value="formState.captcha_code" placeholder="验证码" size="large" class="hud-input"
                :bordered="false">
                <template #prefix>
                  <LockOutlined class="icon-primary" />
                </template>
              </a-input>
            </div>
            <div class="captcha-display" @click="refreshCaptcha">
              <img :src="captcha_image" alt="captcha" />
            </div>
          </div>
        </a-form-item>

        <a-form-item class="action-item">
          <a-button class="btn-engage" type="primary" html-type="submit" size="large" block
            :disabled="!formState.username || !formState.password || !formState.captcha_code" @click="onSubmit">
            登 录
          </a-button>
        </a-form-item>
      </a-form>

      <div class="hud-footer">
        <div class="status-indicator"></div>
        <span class="status-text">遥控链路已加密</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { message } from 'ant-design-vue'
import { onMounted, reactive, ref, UnwrapRef } from 'vue'
import { CURRENT_CONFIG } from '@/api/http/config'
import { login, LoginBody, refreshToken, getLoginPublicKey, getLoginCaptcha } from '@/api/manage'
import apiPilot from '@/api/pilot-bridge'
import { getRoot } from '@/root'
import router from '@/router'
import { EComponentName, ELocalStorageKey, ERouterName, EUserType } from '@/types'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import djiLogo from '@/assets/icons/dji_logo.png'
import { rsaEncrypt } from '@/utils/rsa'

const root = getRoot()
const absoluteBaseURL = CURRENT_CONFIG.baseURL.startsWith('http')
  ? CURRENT_CONFIG.baseURL
  : window.location.origin + CURRENT_CONFIG.baseURL + (CURRENT_CONFIG.baseURL.endsWith('/') ? '' : '/')

const publicKey = ref('')
const captcha_image = ref('')

const formState: UnwrapRef<LoginBody> = reactive({
  username: 'pilot',
  password: '123456pilot',
  captcha_code: '',
  captcha_key: '',
  flag: EUserType.Pilot,
})

const refreshCaptcha = () => {
  getLoginCaptcha().then((res) => {
    formState.captcha_key = res.data.captcha_key
    captcha_image.value = res.data.image
  })
}

const getPublicKey = () => {
  getLoginPublicKey().then((res) => {
    publicKey.value = res.data
  })
}

const isVerified = ref<boolean>(false)
onMounted(async () => {
  refreshCaptcha()
  getPublicKey()

  verifyLicense()
  if (!isVerified.value) {
    return
  }

  apiPilot.setPlatformMessage('Cloud Api Platform', '', '')

  const token = localStorage.getItem(ELocalStorageKey.Token)
  if (token) {
    await refreshToken({})
      .then(res => {
        apiPilot.setComponentParam(EComponentName.Api, {
          host: absoluteBaseURL,
          token: res.data.access_token
        })
        const jsres = apiPilot.loadComponent(EComponentName.Api, apiPilot.getComponentParam(EComponentName.Api))
        if (!jsres) {
          message.error('加载 API 模块失败。')
          return
        }
        apiPilot.setToken(res.data.access_token)
        localStorage.setItem(ELocalStorageKey.Token, res.data.access_token)
        root.$router.push(ERouterName.PILOT_HOME)
      })
      .catch(err => {
        message.error(err)
      })
  }
})

const onSubmit = async (e: any) => {
  if (!isVerified.value) {
    message.error('请先完成授权证书验证。')
    return
  }

  try {
    const encryptedPassword = rsaEncrypt(formState.password, publicKey.value)
    formState.password = encryptedPassword || ''

    const res = await login(formState)
    console.log('login res:', res)
    if (res.code === 0) {
      apiPilot.setComponentParam(EComponentName.Api, {
        host: absoluteBaseURL,
        token: res.data.access_token
      })
      const jsres = apiPilot.loadComponent(
        EComponentName.Api,
        apiPilot.getComponentParam(EComponentName.Api)
      )
      console.log('load api module res:', jsres)
      apiPilot.setToken(res.data.access_token)
      localStorage.setItem(ELocalStorageKey.Token, res.data.access_token)
      localStorage.setItem(ELocalStorageKey.WorkspaceId, res.data.workspace_id)
      localStorage.setItem(ELocalStorageKey.UserId, res.data.user_id)
      localStorage.setItem(ELocalStorageKey.Username, res.data.username)
      localStorage.setItem(ELocalStorageKey.Flag, EUserType.Pilot.toString())
      message.success('登录成功')
      root.$router.push(ERouterName.PILOT_HOME)
    } else {
      message.error(res.message || '登录失败')
      refreshCaptcha()
    }
  } catch (err: any) {
    message.error(err.message || err || '网络请求错误')
    refreshCaptcha()
  }
}

function verifyLicense() {
  isVerified.value = apiPilot.platformVerifyLicense(CURRENT_CONFIG.appId, CURRENT_CONFIG.appKey, CURRENT_CONFIG.appLicense) &&
    apiPilot.isPlatformVerifySuccess()
  if (isVerified.value) {
    message.success('授权证书验证成功。')
  } else {
    message.error('授权证书验证失败。请检查配置的证书是否正确，或重新申请。')
  }
}
</script>

<style lang="scss" scoped>
// Design System Tokens (The Orbital Frontier)
$bg-vacuum: #0a0f1d;
$surface-high: rgba(18, 25, 41, 0.7);
$primary: #00d4ec;
$primary-glow: rgba(0, 212, 236, 0.35);
$secondary: #7e5bef;
$text-on-surface: #e4e8f7;
$text-variant: #8fa0b5;
$ghost-border: rgba(255, 255, 255, 0.08);

.pilot-login-container {
  font-family: 'Inter', -apple-system, sans-serif;
  position: relative;
  width: 100vw;
  height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: $bg-vacuum;
  overflow: hidden;
  color: $text-on-surface;
}

// Atmospheric Background
.ambient-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  overflow: hidden;

  .glow-orb {
    position: absolute;
    border-radius: 50%;
    filter: blur(120px);
    opacity: 0.12;
    animation: drift 20s infinite alternate ease-in-out;
  }

  .orb-primary {
    top: -15%;
    left: -10%;
    width: 500px;
    height: 500px;
    background: radial-gradient(circle, $primary 0%, transparent 75%);
  }

  .orb-secondary {
    bottom: -15%;
    right: -10%;
    width: 600px;
    height: 600px;
    background: radial-gradient(circle, $secondary 0%, transparent 75%);
    animation-duration: 25s;
  }

  .grid-overlay {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-image: linear-gradient($ghost-border 1px, transparent 1px),
      linear-gradient(90deg, $ghost-border 1px, transparent 1px);
    background-size: 40px 40px;
    opacity: 0.12;
    mask-image: radial-gradient(ellipse at center, transparent 20%, black 100%);
    -webkit-mask-image: radial-gradient(ellipse at center, transparent 20%, black 100%);
  }
}

@keyframes drift {
  0% {
    transform: translate(0, 0);
  }

  100% {
    transform: translate(50px, 30px);
  }
}

// Glassmorphism HUD Card
.hud-card {
  position: relative;
  z-index: 10;
  width: 420px;
  max-width: 90vw;
  padding: 32px 36px;
  background: $surface-high;
  backdrop-filter: blur(25px);
  -webkit-backdrop-filter: blur(25px);
  border-radius: 16px;
  border: 1px solid $ghost-border;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6), inset 0 0 15px rgba(255, 255, 255, 0.03);
  display: flex;
  flex-direction: column;
  gap: 24px;

  // Mobile landscape optimization (like DJI RC screen height limit)
  @media screen and (max-height: 540px) {
    width: 580px;
    padding: 16px 24px;
    gap: 12px;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;

    .hud-header {
      flex: 1;
      text-align: left;
      margin-bottom: 0;
      display: flex;
      flex-direction: column;
      justify-content: center;

      .brand-container {
        margin: 0 0 12px 0;
        width: 48px;
        height: 48px;
      }

      .hud-title {
        font-size: 20px;
        margin: 0 0 4px;
      }

      .hud-subtitle {
        font-size: 10px;
        letter-spacing: 2px;
      }
    }

    .auth-form {
      flex: 1.2;
      display: flex;
      flex-direction: column;
      gap: 8px;

      .form-item-custom {
        margin-bottom: 0px !important;
      }

      .action-item {
        margin-top: 4px;
      }
    }

    .hud-footer {
      display: none;
    }
  }
}

// HUD Header
.hud-header {
  text-align: center;

  .brand-container {
    width: 56px;
    height: 56px;
    margin: 0 auto 16px;
    border-radius: 12px;
    background: linear-gradient(135deg, rgba($primary, 0.15), rgba($secondary, 0.15));
    border: 1px solid rgba($primary, 0.3);
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 0 20px $primary-glow;

    .brand-logo {
      width: 32px;
      height: 32px;
      object-fit: contain;
      filter: drop-shadow(0 0 4px $primary);
    }
  }

  .hud-title {
    font-family: 'Inter', sans-serif;
    font-size: 24px;
    font-weight: 700;
    margin: 0 0 4px;
    letter-spacing: 1px;
    color: #fff;
    text-shadow: 0 0 15px rgba(255, 255, 255, 0.15);
  }

  .hud-subtitle {
    font-size: 11px;
    color: $primary;
    margin: 0;
    font-weight: 600;
    letter-spacing: 3px;
    text-transform: uppercase;
  }
}

// Input wrappers
.auth-form {
  .form-item-custom {
    margin-bottom: 16px;
  }

  .input-wrapper {
    position: relative;
    background: rgba(0, 0, 0, 0.45);
    border-radius: 10px;
    border: 1px solid rgba(255, 255, 255, 0.08);
    transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
    overflow: hidden;

    &:focus-within {
      background: rgba(0, 0, 0, 0.6);
      border-color: rgba($primary, 0.5);
      box-shadow: 0 0 10px rgba($primary, 0.15);
    }

    .hud-input {
      padding: 6px 12px;
      background: transparent !important;

      :deep(input) {
        color: #fff !important;
        background: transparent !important;
        font-family: 'Inter', sans-serif;
        font-size: 14px;
        letter-spacing: 0.5px;

        &:-webkit-autofill {
          -webkit-text-fill-color: #fff !important;
          transition: background-color 5000s ease-in-out 0s;
        }

        &::placeholder {
          color: rgba(255, 255, 255, 0.35);
        }
      }
    }

    .icon-primary {
      color: $primary;
      font-size: 16px;
      margin-right: 6px;
      opacity: 0.8;
    }
  }

  .captcha-module {
    display: flex;
    gap: 12px;
    align-items: stretch;

    .captcha-input-wrapper {
      flex: 1;
    }

    .captcha-display {
      width: 110px;
      height: 38px;
      border-radius: 10px;
      overflow: hidden;
      cursor: pointer;
      border: 1px solid rgba(255, 255, 255, 0.08);
      background: rgba(0, 0, 0, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.25s ease;

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        opacity: 0.85;
        transition: opacity 0.25s;
      }

      &:hover {
        border-color: rgba($primary, 0.5);
        box-shadow: 0 0 10px rgba($primary, 0.15);

        img {
          opacity: 1;
        }
      }
    }
  }

  .action-item {
    margin-top: 10px;
    margin-bottom: 0;

    .btn-engage {
      height: 44px;
      background: linear-gradient(135deg, $primary 0%, rgba($primary, 0.8) 100%);
      border: none;
      border-radius: 10px;
      color: #000;
      font-family: 'Inter', sans-serif;
      font-size: 14px;
      font-weight: 700;
      letter-spacing: 1px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 0 15px rgba($primary, 0.2);
      transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);

      &:hover:not([disabled]) {
        box-shadow: 0 0 25px rgba($primary, 0.45);
        transform: translateY(-1px);
        background: linear-gradient(135deg, #2ae0f8 0%, $primary 100%);
      }

      &:active:not([disabled]) {
        transform: translateY(0);
      }

      &[disabled] {
        background: rgba(255, 255, 255, 0.05) !important;
        color: rgba(255, 255, 255, 0.25) !important;
        box-shadow: none !important;
        border: 1px solid rgba(255, 255, 255, 0.05);
        cursor: not-allowed;
      }
    }
  }
}

// Telemetry Footer
.hud-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;

  .status-indicator {
    width: 6px;
    height: 6px;
    background: $primary;
    border-radius: 50%;
    box-shadow: 0 0 8px $primary;
    animation: pulse 2s infinite cubic-bezier(0.4, 0, 0.6, 1);
  }

  .status-text {
    font-size: 9px;
    color: $text-variant;
    letter-spacing: 1px;
    text-transform: uppercase;
  }
}

@keyframes pulse {

  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }

  50% {
    opacity: 0.5;
    transform: scale(0.85);
  }
}
</style>

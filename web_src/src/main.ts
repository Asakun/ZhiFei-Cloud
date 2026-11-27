import App from './App.vue'
import router from './router'
import { CommonComponents } from './use-common-components'
import 'virtual:svg-icons-register'
import store, { storeKey } from './store'
import { createInstance } from '@/root'
import { useDirectives } from './directives'
import './styles/index.scss'
import './styles/theme.css'
import './styles/common.scss'
import './styles/airControl.scss'
import './styles/antd-picker-dark.scss'
import Antd from 'ant-design-vue'
import './assets/styles/action-groups.css'
import 'cesium/Build/Cesium/Widgets/widgets.css'

const app = createInstance(App)

app.use(store, storeKey)
app.use(router)
app.use(CommonComponents)
app.use(Antd)
app.use(useDirectives)

app.mount('#demo-app')

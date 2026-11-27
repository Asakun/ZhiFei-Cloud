import vue from '@vitejs/plugin-vue'
import cesium from 'vite-plugin-cesium'
// config alias
import path from 'path'
import { ConfigEnv, defineConfig, UserConfigExport } from 'vite'
// Introduce eslint plugin
import viteSvgIcons from 'vite-plugin-svg-icons'
import { viteVConsole } from 'vite-plugin-vconsole'
import VueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from './node_modules/@tailwindcss/vite/dist/index.mjs'

// https://vitejs.dev/config/
export default ({ command, mode }: ConfigEnv): UserConfigExport => defineConfig({
  plugins: [
    vue(),
    cesium(),
    VueDevTools({
      launchEditor: 'idea'
    }),
    tailwindcss(),
    viteSvgIcons({
      // 指定需要缓存的图标文件夹
      iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
      // 指定symbolId格式
      symbolId: 'icon-[dir]-[name]',
    }),
    /*viteVConsole({
      entry: path.resolve(__dirname, './src/main.ts'), // 入口文件
      localEnabled: command === 'serve', // serve开发环境下
      // enabled: command !== 'serve' || mode === 'test', // 打包环境下/发布测试包,
      config: { // vconsole 配置项
        maxLogNumber: 1000,
        theme: 'light'
      }
    }),*/
    // [svgBuilder('./src/assets/icons/')] // All svg under src/icons/svg/ have been imported here, no need to import separately
  ],
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: '@use "@/styles/variables.scss" as *;'
      }
    }
  },
  server: {
    open: true,
    host: '0.0.0.0',
    port: 8081,
    proxy: {
      '/b3dms/': {
        target: 'http://MinIO服务地址',
        changeOrigin: true
      },
      '/llm_source/': {
        target: 'http://MinIO服务地址',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/llm_source/, '')

      },
      // MinIO代理 - 用于绕过CORS限制读取图片EXIF
      '/minio-proxy': {
        target: 'http://MinIO服务地址',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/minio-proxy/, ''),
      },
      '/srsAPI/': {
        target: '替换srs服务',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/srsAPI/, '')
      },
      '/backEnd/': {
        target: 'http://无人机后端服务',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/backEnd/, ''),
      },
      '/backEndWs/': {
        target: 'ws://无人机后端服务',
        changeOrigin: true,
        ws: true,
        rewrite: (path) => path.replace(/^\/backEndWs/, ''),
      }
    }
  },
  envDir: './env',
  resolve: {
    alias: {
      // https://github.com/vitejs/vite/issues/279#issuecomment-635646269
      '@': path.resolve(__dirname, './src'),
    }
  },
  base: '/',
  // 为浏览器环境添加 Node.js 全局变量 polyfill
  // mqtt.js 5.x 需要 'global' 变量
  define: {
    global: 'globalThis',
  },
  build: {
    target: ['es2015'], // 最低支持 es2015
    sourcemap: true
  }
})

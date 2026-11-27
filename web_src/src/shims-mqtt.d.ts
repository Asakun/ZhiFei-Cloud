// mqtt.js 5.x 已原生支持 TypeScript，此文件保留以兼容旧代码引用
// 如果项目中所有 'mqtt/dist/mqtt.min' 引用都已更新为 'mqtt'，可删除此文件
declare module 'mqtt/dist/mqtt.min' {
  import MQTT from 'mqtt'
  export = MQTT
}

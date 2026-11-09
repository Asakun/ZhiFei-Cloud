# 智飞无人机云平台服务端

基于 [DJI 官方 Cloud API Demo](https://github.com/dji-sdk/DJI-Cloud-API-Demo) 二次开发的无人机 / 机场（Dock）云平台服务端实现。通过 DJI Pilot 2 与无人机、机场通信，提供设备管理、航线、直播、媒体、飞行控制等云端能力，并集成了航线 KMZ 工具。

- Java 11 / Spring Boot 2.7.12
- 基于 DJI Cloud API 1.10.0
- MIT License

## 功能特性

| 模块 | 能力 |
| --- | --- |
| 设备管理 | 设备绑定与拓扑、工作空间、RBAC 权限、设备 OSD / 状态 / 事件、HMS 告警、固件 OTA |
| 航线任务 | 航线文件上传与下发、飞行任务管理、KMZ 航线生成与解析 |
| 直播 | Agora / RTMP / RTSP / GB28181 / WHIP 多通道直播 |
| 媒体库 | 媒体文件上传、分组、下载与对象存储（MinIO / 阿里云 OSS / AWS S3） |
| 飞行控制 | 机场起降、返航、DRC 低延迟遥控 |
| 地图 | 地图元素、分组、飞行区管理 |
| 负载 | PSDK 负载管理、文件上传 |
| 认证 | JWT、API Key、RSA 加密登录、OIDC 单点登录 |
| 第三方代理 | EMQX HTTP API 代理 |

## 技术栈

| 组件 | 选型 |
| --- | --- |
| 框架 | Spring Boot 2.7.12 |
| 数据库 | MySQL 8 + MyBatis-Plus + JPA(Hibernate) + Druid |
| 缓存 | Redis（Lettuce） |
| 设备通信 | MQTT（Eclipse Paho + Spring Integration MQTT） |
| 认证鉴权 | Spring Security + JWT + BCrypt |
| 文件存储 | MinIO（默认）/ 阿里云 OSS / AWS S3 |
| 接口文档 | SpringDoc OpenAPI |
| 构建 | Maven 多模块 |

## 快速开始

### 环境要求

- JDK 11
- Maven 3.6+
- MySQL 8
- Redis 5+
- EMQX（MQTT broker，BASIC 1883 / DRC 8083）
- MinIO 或其他兼容 S3 的对象存储（可选，媒体功能需要）

## Docker 部署

```bash
docker compose up -d
```

## 前端构建部署

详见 [`web_src/README.md`](web_src/README.md)。

## 与上游的差异

本项目在 DJI 官方 Demo 基础上主要增加了：

- EMQX HTTP API 代理
- OIDC 单点登录
- 集成 KMZ 航线生成与解析模块
- 优化的认证、权限与错误处理
- 工作组管理
- 负载管理
- 兼容最新机场3、M4T等

## 许可证

本项目采用 [MIT License](LICENSE)。

### 上游与第三方来源

- 本项目基于 [dji-sdk/DJI-Cloud-API-Demo](https://github.com/dji-sdk/DJI-Cloud-API-Demo)（MIT License，Copyright (c) 2022 DJI-SDK）二次开发，原始版权声明已保留在 `LICENSE` 中。
- `dj-uav` 模块来自 [SongJian-99/dj-uav](https://github.com/SongJian-99/dj-uav)。

DJI、DJI Pilot 2 等名称与标识归大疆创新所有，本项目与 DJI 无隶属关系。

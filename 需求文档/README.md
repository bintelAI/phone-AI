# Aries AI - 需求文档目录

## 文档索引

| 序号 | 文档名称 | 内容说明 |
|------|----------|----------|
| 1 | [项目概述与核心特性](./1.项目概述与核心特性.md) | 项目简介、核心亮点、技术栈、开源协议 |
| 2 | [业务功能分析](./2.业务功能分析.md) | 核心业务流程、用户角色、功能清单、非功能性需求 |
| 3 | [架构设计](./3.架构设计.md) | 整体架构分层、模块组织、核心组件、导航路由、关键技术设计 |
| 4 | [项目目录结构与模块分析](./4.项目目录结构与模块分析.md) | 详细目录结构、各模块文件清单 |
| 5 | [数据模型与设计模式](./5.数据模型与设计模式.md) | 核心数据模型、设计模式应用、数据流、错误处理 |
| 6 | [远程控制模式改造设计文档](./6.远程控制模式改造设计文档.md) | 登录→WebSocket→任务下发→手机自动执行的远程控制架构设计 |

---

## 项目概览

- **项目名称**：Aries AI（Phone Agent）
- **版本**：v1.4.2-xyla.alpha
- **类型**：开源 Android UI 自动化引擎
- **协议**：AGPL-3.0
- **技术栈**：Kotlin + Jetpack Compose + Koin + Room + Shizuku + MNN
- **最低版本**：Android 11 (API 30)
- **包名**：com.ai.phoneagent

## 项目结构

```
phone-AI/
├── app/                           # 主应用 (Kotlin + Jetpack Compose)
├── core/                          # 核心模块
│   ├── common/                    # 通用工具
│   ├── designsystem/              # 设计系统
│   ├── prompt/                    # Prompt 模板
│   └── shizuku/                   # Shizuku 桥接
├── feature/                       # 功能模块
│   ├── settings/                  # 设置
│   └── updates/                   # 更新
├── Aries-site/                    # 官网 & 文档
├── telemetry-worker/              # Cloudflare Workers 遥测
├── accessibility/                 # 无障碍服务源码
└── xyla/                          # AI 预设配置
```

---

*本文档由 AI 辅助分析生成，基于项目 v1.4.2-xyla.alpha 版本的源码结构。*
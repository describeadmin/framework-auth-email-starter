# 更新日志

本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的组织方式，
版本号遵循 [SemVer](https://semver.org/lang/zh-CN/)。

每个版本固定分 **Breaking Changes / New Features / Bug Fixes** 三类
（见组织编码规范第 5 节）。没有内容的类别保留标题并写「无」，
这样使用者不必怀疑是遗漏还是确实没有。

> **本插件的版本线与框架独立**。插件发 1.3.0 完全可能仍然只要求框架 1.0.0，
> 因此每个版本都必须写明适配的框架版本，见下方各条目的「框架要求」。

## 0.2.0 (2026-08-31)

describeadmin 的邮箱验证码（无密码）登录插件：实现 `AuthProvider` SPI，
可选提供 `NotifyChannel(channel="email")` 供其他场景复用同一个发信能力。
首个发布版本，版本号与 framework 0.2.0 对齐（本轮四个插件统一按框架号发布）。

**框架要求：0.2.0 及以上**（依赖 framework-security-starter 0.2.0 的 `AuthProvider`
装配点与 `describeadmin.security.permit-all` 白名单机制，以及 framework-notify-starter
的 `NotifyChannel` 契约；启动期用 `FrameworkVersion.requireCompatible` 自检）

### Breaking Changes

无（首个版本）。

### New Features

- `EmailCodeAuthProvider`（`type = "email"`）：`POST /api/auth/login` 传
  `{"type":"email","email":"...","code":"..."}` 即可登录，与内置用户名口令方式共存。
- `POST /api/auth/email/code` 发送验证码：**无论邮箱是否已注册都返回成功**，未注册邮箱
  不真正投递——与核心 `UsernamePasswordAuthProvider` 不区分「用户不存在 / 口令错误」是
  同一种防账号枚举姿势。
- SMTP 连接参数**沿用 Spring Boot 原生 `spring.mail.*`**，本插件不重新定义，只在
  `JavaMailSender` 之上包一层业务逻辑——避免「两处配置各说各话、改了没生效」。
- 防刷三道闸：`send-interval`（同邮箱两次发码最小间隔，默认 60s）、`code-ttl`（默认 5m）、
  `max-verify-attempts`（同一验证码错误尝试上限，默认 5，超过即拒）。
- 可选 `EmailNotifyChannel`（`NotifyChannel(channel="email")`，`notify-channel.enabled`
  默认 `true`）：把同一个 `JavaMailSender` 暴露给密码重置提醒等通用发信场景，
  关掉不影响登录能力。
- 配置项前缀 `describeadmin.auth.email`：`enabled` / `code-length` / `code-ttl` /
  `send-interval` / `max-verify-attempts` / `from-address` / `subject` /
  `notify-channel.enabled`。运行时开关关掉后行为与「没引这个 jar」完全一致。
- 启动期 `FrameworkVersion.requireCompatible` 兼容性自检。

### Bug Fixes

无（首个版本）。

### 仓库

- 独立成仓、独立版本线、独立发布（原方案 3.1.1 的既定拓扑）。
- POM **不继承 `framework-parent`**，改为 `import framework-bom`——这正是业务方消费框架的
  姿势，插件用同一套姿势才能提前暴露业务方会遇到的问题。
- CI 按框架版本矩阵跑完整测试；`FrameworkAuthEmailAutoConfigurationTest` 覆盖
  「不引 / 未配 SMTP = 核心默认行为不变」与「配了 SMTP = 邮箱登录接入且与 password 共存」
  两条路径（docs/registry.md 准入规范第 8 条）。
- 发布晚于 framework 0.2.0：`import` 的 `framework-bom` 必须是一个真实存在的已发布版本，
  因此本插件的首发版本直接从 0.2.0 起。

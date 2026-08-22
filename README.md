# framework-auth-email-starter

邮箱验证码（无密码）登录插件，实现 `describeadmin` 框架的 `AuthProvider` SPI，
可选提供 `NotifyChannel(channel="email")` 供其他场景复用同一个发信能力。

适配框架最低版本：**0.2.0**（见 `FrameworkAuthEmailAutoConfiguration.REQUIRED_FRAMEWORK_VERSION`）。

## 引入

```xml
<dependency>
  <groupId>io.github.describeadmin</groupId>
  <artifactId>framework-auth-email-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version> <!-- 未发布到 Central 前需显式版本号，framework-bom 不仲裁插件版本 -->
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

## 必须配置的两件事

1. **SMTP 参数**（Spring Boot 原生 `spring.mail.*`，本插件不重新定义）：

   ```yaml
   spring:
     mail:
       host: smtp.example.com
       port: 465
       username: no-reply@example.com
       password: ${SMTP_PASSWORD}
       properties:
         mail:
           smtp:
             auth: true
             ssl:
               enable: true
   ```

   本地开发建议用 [MailHog](https://github.com/mailhog/MailHog)（`docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog`，
   `spring.mail.host=localhost`、`port=1025`、`smtp.auth=false`），有 Web UI 直接肉眼查看发出的验证码邮件。

2. **把发验证码端点加进白名单**——核心的 `FrameworkSecurityAutoConfiguration.BUILT_IN_PERMIT_ALL`
   不包含插件路径（分层硬边界），不加会直接 401 且报错不会指向这里：

   ```yaml
   describeadmin:
     security:
       permit-all:
         - /api/auth/email/code
   ```

## 配置项（前缀 `describeadmin.auth.email`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `enabled` | `true` | 运行时开关，关闭后等同于未引入本插件 |
| `code-length` | `6` | 验证码长度（纯数字） |
| `code-ttl` | `5m` | 验证码有效期 |
| `send-interval` | `60s` | 同一邮箱两次发码的最小间隔 |
| `max-verify-attempts` | `5` | 同一验证码允许的错误尝试次数 |
| `from-address` | 空 | 覆盖 `spring.mail.username` 作为 From 头 |
| `subject` | `登录验证码` | 验证码邮件主题 |
| `notify-channel.enabled` | `true` | 是否额外注册 `NotifyChannel(channel="email")` |

## 端点

- `POST /api/auth/email/code` — 发送验证码，body: `{"email": "..."}`；无论邮箱是否已注册都返回成功（防账号枚举）。
- `POST /api/auth/login` — 沿用核心端点，body: `{"type": "email", "email": "...", "code": "..."}`。

## 未注册邮箱与账号枚举

发码接口不区分"邮箱是否已注册"：未注册邮箱同样返回成功，但不会真正投递邮件。
这与核心 `UsernamePasswordAuthProvider` 不区分"用户不存在"与"密码错误"是同一种安全姿势。

## 测试

```bash
mvn test
```

- `EmailCodeServiceTest`/`EmailNotifyChannelTest`：用 [GreenMail](https://greenmail-mail-test.github.io/greenmail/)（纯 JVM 内假 SMTP 服务器，无需 Docker）验证真实的发信/收信链路。
- `FrameworkAuthEmailAutoConfigurationTest`：验证"不引/未配置 SMTP = 核心默认行为不变"与"配置了 SMTP = 邮箱登录接入且与内置 password 方式共存"两条路径，覆盖 docs/registry.md 准入规范第 8 条。

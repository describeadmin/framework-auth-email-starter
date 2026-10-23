# framework-auth-email-starter

describeadmin 的可选插件。邮箱验证码登录：实现框架的 `AuthProvider`，登录页自动出现「邮箱登录」入口；可选提供 `email` 消息通道。

**文档：[https://describeadmin.github.io/docs/how-to/auth-email](https://describeadmin.github.io/docs/how-to/auth-email)**，全部配置项见 [插件配置](https://describeadmin.github.io/docs/reference/configuration/plugins)。

## 引入

```xml
<dependency>
  <groupId>io.github.describeadmin</groupId>
  <artifactId>framework-auth-email-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

版本号要显式写：`framework-bom` 不管插件的版本，插件有自己的版本线。

另外引入 `org.springframework.boot:spring-boot-starter-mail`（版本由 Spring Boot 管理）。SMTP 用 Spring Boot 的标准配置：

```yaml
spring:
  mail:
    host: smtp.example.gov.cn
    port: 465
    username: no-reply@example.gov.cn
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: true
```

前端不用改：登录页按 `/api/auth/providers` 的返回决定是否显示邮箱登录。

运行时开关是 `describeadmin.auth.email.enabled`（默认 `true`），关掉后与没有引入这个 jar 完全一样。

## 兼容性

| 插件版本 | 最低框架版本 |
|---|---|
| 1.0.0 | 1.0.0 |

插件启动时检查框架版本：框架比上表要求的旧、或主版本不同（0.x 与 1.x 不互通），应用直接拒绝启动并说明原因。
CI 对表里的每个框架版本各跑一遍完整测试。版本对应关系的总表见 [插件目录](https://describeadmin.github.io/docs/reference/plugins)。

## 开发

```bash
mvn clean test          # 用 GreenMail（JVM 内的 SMTP 服务器）验证真实收发信，不需要 Docker
mvn clean test -Ddescribeadmin.version=1.0.0
```

构建只要求 JDK ≥ 17（enforcer 把关，产物固定为 Java 17 字节码）；用 `mvn -v` 看 Maven 实际用的 JDK。
框架有尚未发布到 Central 的版本时，先 `mvn -f ../framework/pom.xml clean install -DskipTests -Dgpg.skip=true` 装到本地仓库。

本插件不继承 `framework-parent`，而是 import `framework-bom`，与业务方的用法相同。插件开发规范见
[编写插件](https://describeadmin.github.io/docs/dev/plugin-dev)，发布照 [发布手册](https://describeadmin.github.io/docs/dev/release) 执行。

## 变更记录与许可证

[CHANGELOG.md](./CHANGELOG.md) · [Apache-2.0](./LICENSE)

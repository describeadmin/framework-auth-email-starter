package io.github.describeadmin.auth.email.autoconfigure;

import io.github.describeadmin.auth.email.core.EmailCodeAuthProvider;
import io.github.describeadmin.auth.email.core.EmailCodeController;
import io.github.describeadmin.auth.email.core.EmailCodeSendThrottle;
import io.github.describeadmin.auth.email.core.EmailCodeService;
import io.github.describeadmin.auth.email.core.EmailCodeVerifyGuard;
import io.github.describeadmin.auth.email.core.EmailNotifyChannel;
import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.FrameworkVersion;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.autoconfigure.FrameworkSecurityAutoConfiguration;
import io.github.describeadmin.system.autoconfigure.FrameworkSystemAutoConfiguration;
import io.github.describeadmin.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * framework-auth-email-starter 的自动配置。
 *
 * <p><b>装配顺序与 framework-cache-redis-starter 的关键差异</b>：{@code CacheProvider}/
 * {@code TokenStore} 是"单一默认实现 + {@code @ConditionalOnMissingBean} 整体替换"模型，
 * 插件必须显式声明 {@code before}，否则会被核心已注册的默认实现挡掉（见
 * {@code FrameworkCacheRedisAutoConfiguration} 的类注释）。而 {@link io.github.describeadmin.security.api.AuthProvider}
 * 与核心的 {@code NotifyChannel} 一样是<b>多实现共存</b>模型——{@code AuthProviderRegistry}
 * 收集的是 {@code List<AuthProvider>}，只在<b>所有</b> {@code @AutoConfiguration} 类贡献完
 * Bean 定义之后才会真正实例化，与"谁先注册谁生效"的条件求值时序无关。
 * 因此本类<b>不需要、也不应该</b>声明 {@code before}/{@code beforeName}——那是照抄
 * 单例覆盖模型插件的错误示范。真正的唯一性校验交给
 * {@code AuthProviderRegistry} 的构造函数（重复的 {@code type()} 会在启动期被拒绝）。
 *
 * <p>唯一必须声明的顺序约束是 {@link MailSenderAutoConfiguration}：本类的多个 Bean 方法
 * 用 {@code @ConditionalOnBean(JavaMailSender.class)} 判断"是否已配置 SMTP"，这是条件求值
 * 时序敏感的单例式判断（同 {@code RedisCacheProvider} 对 {@code StringRedisTemplate} 的用法），
 * 必须确保 Spring Boot 原生的邮件自动配置先跑完。{@code FrameworkSecurityAutoConfiguration}/
 * {@code FrameworkSystemAutoConfiguration} 则纯粹是文档性的 {@code after}。
 */
@AutoConfiguration(after = {MailSenderAutoConfiguration.class,
        FrameworkSecurityAutoConfiguration.class, FrameworkSystemAutoConfiguration.class})
@ConditionalOnProperty(prefix = "describeadmin.auth.email", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FrameworkAuthEmailProperties.class)
public class FrameworkAuthEmailAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FrameworkAuthEmailAutoConfiguration.class);

    /**
     * 本插件要求的最低框架版本。手工声明而不是从插件自身版本推导——插件独立成仓后，
     * 插件版本与框架版本不再有对应关系。用到了 0.2.0 内新增的
     * {@code AuthUserLoader.loadByUserId()}（框架 F 项已交付）与
     * {@code SysUserService.findByEmail()}/{@code assertMobileEmailAvailable()}。
     */
    public static final String REQUIRED_FRAMEWORK_VERSION = "0.2.0";

    public FrameworkAuthEmailAutoConfiguration() {
        // 放在构造函数里：条件都满足、真的要装配本插件时才检查，插件未激活时不因版本
        // 不匹配把应用打死
        FrameworkVersion.requireCompatible("framework-auth-email-starter", REQUIRED_FRAMEWORK_VERSION);
    }

    @Bean
    @ConditionalOnMissingBean(EmailCodeSendThrottle.class)
    public EmailCodeSendThrottle emailCodeSendThrottle(CacheProvider cache, FrameworkAuthEmailProperties properties) {
        return new EmailCodeSendThrottle(cache, properties.getSendInterval());
    }

    @Bean
    @ConditionalOnMissingBean(EmailCodeVerifyGuard.class)
    public EmailCodeVerifyGuard emailCodeVerifyGuard(CacheProvider cache, FrameworkAuthEmailProperties properties) {
        return new EmailCodeVerifyGuard(cache, properties.getMaxVerifyAttempts(), properties.getCodeTtl());
    }

    /**
     * {@code @ConditionalOnBean(JavaMailSender.class)} 而不是 {@code @ConditionalOnClass}：
     * {@code spring-boot-starter-mail} 一旦引入，{@code JavaMailSender} 接口类<b>始终</b>在
     * classpath 上，但只有业务方配置了 {@code spring.mail.host} 时 Spring Boot 原生的
     * {@code MailSenderAutoConfiguration} 才会真正产出 Bean——这里要捕捉的正是
     * "类在但没配置"这个中间态，与 {@code RedisCacheProvider} 对
     * {@code @ConditionalOnBean(StringRedisTemplate.class)} 的用法是同一道理。
     */
    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnMissingBean(EmailCodeService.class)
    public EmailCodeService emailCodeService(CacheProvider cache, SysUserService userService,
                                             JavaMailSender mailSender, EmailCodeSendThrottle throttle,
                                             EmailCodeVerifyGuard verifyGuard,
                                             FrameworkAuthEmailProperties properties) {
        return new EmailCodeService(cache, userService, mailSender, throttle, verifyGuard, properties);
    }

    /**
     * type()="email" 的 {@code AuthProvider}。<b>无条件注册</b>（只受本插件自身的
     * {@code @ConditionalOnProperty} 与前置的 {@code EmailCodeService} 是否存在控制），
     * 不使用 {@code @ConditionalOnMissingBean(AuthProvider.class)}——理由见类注释。
     */
    @Bean
    @ConditionalOnBean(EmailCodeService.class)
    public EmailCodeAuthProvider emailCodeAuthProvider(EmailCodeService codeService,
                                                       SysUserService userService,
                                                       AuthUserLoader authUserLoader) {
        return new EmailCodeAuthProvider(codeService, userService, authUserLoader);
    }

    @Bean
    @ConditionalOnBean(EmailCodeService.class)
    public EmailCodeController emailCodeController(EmailCodeService codeService) {
        return new EmailCodeController(codeService);
    }

    /**
     * 可选的 email 通知渠道。默认随本插件一起启用（{@code notify-channel.enabled} 默认 true），
     * 可用 {@code describeadmin.auth.email.notify-channel.enabled=false} 单独关掉，
     * 不影响邮箱验证码登录本身。
     */
    @Bean
    @ConditionalOnBean(JavaMailSender.class)
    @ConditionalOnProperty(prefix = "describeadmin.auth.email.notify-channel", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public EmailNotifyChannel emailNotifyChannel(JavaMailSender mailSender, FrameworkAuthEmailProperties properties) {
        return new EmailNotifyChannel(mailSender, properties);
    }

    /**
     * 诊断性 Bean：插件已启用但检测不到 {@code JavaMailSender} 时打印告警，而不是
     * 静默地什么都不做——不然的话"引了插件却拿不到邮箱登录"这类问题会毫无线索，
     * 与 docs/registry.md 准入规范第 3 条描述的"引了却没生效，启动毫无异常"是同一类风险，
     * 只是这次的成因是业务方忘了配置 {@code spring.mail.host}，而不是装配顺序。
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public InitializingBean emailAuthMisconfigurationWarning() {
        return () -> log.warn("describeadmin.auth.email.enabled=true 但未检测到 JavaMailSender，"
                + "请确认已配置 spring.mail.host 等 SMTP 参数，否则邮箱验证码将始终发送失败");
    }
}

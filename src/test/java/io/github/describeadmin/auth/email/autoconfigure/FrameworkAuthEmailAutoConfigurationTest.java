package io.github.describeadmin.auth.email.autoconfigure;

import io.github.describeadmin.auth.email.core.EmailCodeAuthProvider;
import io.github.describeadmin.auth.email.core.EmailCodeController;
import io.github.describeadmin.auth.email.core.EmailCodeService;
import io.github.describeadmin.auth.email.core.EmailNotifyChannel;
import io.github.describeadmin.cache.autoconfigure.FrameworkCacheAutoConfiguration;
import io.github.describeadmin.common.api.FrameworkVersion;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.autoconfigure.FrameworkSecurityAutoConfiguration;
import io.github.describeadmin.security.core.AuthProviderRegistry;
import io.github.describeadmin.system.mapper.SysUserMapper;
import io.github.describeadmin.system.service.SysUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 插件装配机制的验证——回答"把这个 jar 放进 classpath，它到底有没有接管"，
 * 而不是"Redis/邮件发送写得对不对"（那由其他测试类负责）。
 *
 * <p>与 {@code FrameworkCacheRedisAutoConfigurationTest} 的关键差异：本插件的
 * {@code AuthProvider} 是多实现共存模型，因此这里验证的不是"谁接管了单例"，
 * 而是"该注册的 Bean 有没有被 {@code AuthProviderRegistry} 收集到"。
 *
 * <p>{@link SysUserService}/{@link AuthUserLoader} 用 mock 替身直接注册——本类要验证的是
 * 装配顺序与条件开关，不需要真的拉起 MyBatis/DataSource 去满足
 * {@code FrameworkSystemAutoConfiguration} 的完整依赖链。{@link SysUserMapper} 的 mock
 * 同样必须注册：{@code SysUserService} 继承自 MyBatis-Plus 的 {@code ServiceImpl}，
 * 其 {@code baseMapper} 字段是 {@code @Autowired} 的——即便 {@code SysUserService} 本身
 * 是 mock 出来的实例，只要它被注册为 Spring Bean，容器仍会对它执行属性注入，
 * 找不到 {@code SysUserMapper} 类型的 Bean 就会在装配阶段失败。
 */
@DisplayName("插件装配")
class FrameworkAuthEmailAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MailSenderAutoConfiguration.class,
                    FrameworkCacheAutoConfiguration.class,
                    FrameworkSecurityAutoConfiguration.class,
                    FrameworkAuthEmailAutoConfiguration.class))
            .withBean(AuthUserLoader.class, () -> mock(AuthUserLoader.class))
            .withBean(SysUserMapper.class, () -> mock(SysUserMapper.class))
            .withBean(SysUserService.class, () -> mock(SysUserService.class))
            .withPropertyValues("spring.mail.host=localhost");

    @Test
    @DisplayName("插件声明的最低框架版本，与它实际构建所依赖的框架是自洽的")
    void declaredRequirementIsSatisfiedByTheFrameworkItBuildsAgainst() {
        assertThat(FrameworkVersion.current()).isNotEqualTo(FrameworkVersion.UNKNOWN);
        assertThatNoException().isThrownBy(() -> FrameworkVersion.requireCompatible(
                "framework-auth-email-starter",
                FrameworkAuthEmailAutoConfiguration.REQUIRED_FRAMEWORK_VERSION));
    }

    @Test
    @DisplayName("框架比插件要求的旧时，启动即失败而不是等到运行期报错")
    void incompatibleFrameworkFailsFast() {
        assertThatThrownBy(() -> FrameworkVersion.requireCompatible(
                "framework-auth-email-starter", "99.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("framework-auth-email-starter");
    }

    @Test
    @DisplayName("不引本插件（或未配置 SMTP）时，核心默认行为不变——不引发任何启动异常")
    void withoutJavaMailSenderDoesNotRegisterEmailBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        FrameworkCacheAutoConfiguration.class,
                        FrameworkSecurityAutoConfiguration.class,
                        FrameworkAuthEmailAutoConfiguration.class))
                .withBean(AuthUserLoader.class, () -> mock(AuthUserLoader.class))
                .withBean(SysUserMapper.class, () -> mock(SysUserMapper.class))
                .withBean(SysUserService.class, () -> mock(SysUserService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EmailCodeAuthProvider.class);
                    assertThat(context).doesNotHaveBean(EmailCodeService.class);
                    assertThat(context).doesNotHaveBean(EmailCodeController.class);
                    assertThat(context).doesNotHaveBean(EmailNotifyChannel.class);
                    // 未装配邮箱插件时，/api/auth/providers 只有内置的 password 一种
                    assertThat(context.getBean(AuthProviderRegistry.class).availableTypes())
                            .containsExactly("password");
                });
    }

    @Test
    @DisplayName("配置了 SMTP 后，邮箱登录接入——与内置 password 方式共存，而不是互相替换")
    void withJavaMailSenderRegistersEmailAuthProviderAlongsidePassword() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmailCodeAuthProvider.class);
            assertThat(context.getBean(EmailCodeAuthProvider.class).type()).isEqualTo("email");

            // 多实现共存模型的核心断言：两种登录方式必须都在，不是谁覆盖谁
            assertThat(context.getBean(AuthProviderRegistry.class).availableTypes())
                    .containsExactlyInAnyOrder("password", "email");
        });
    }

    @Test
    @DisplayName("运行时开关关掉后完全不装配，等同于没引这个 jar")
    void disabledFallsBackToAbsent() {
        runner.withPropertyValues("describeadmin.auth.email.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(EmailCodeAuthProvider.class);
            assertThat(context).doesNotHaveBean(EmailCodeService.class);
            assertThat(context.getBean(AuthProviderRegistry.class).availableTypes())
                    .containsExactly("password");
        });
    }

    @Test
    @DisplayName("可以只关掉 email 通知渠道，不影响验证码登录本身")
    void notifyChannelCanBeOptedOutIndependently() {
        runner.withPropertyValues("describeadmin.auth.email.notify-channel.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(EmailCodeAuthProvider.class);
            assertThat(context).doesNotHaveBean(EmailNotifyChannel.class);
        });
    }

    @Test
    @DisplayName("email 通知渠道默认随插件一起启用")
    void notifyChannelEnabledByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(EmailNotifyChannel.class));
    }
}

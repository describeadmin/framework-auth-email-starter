package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.auth.email.AbstractGreenMailTest;
import io.github.describeadmin.auth.email.autoconfigure.FrameworkAuthEmailProperties;
import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.cache.core.InMemoryCacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.service.SysUserService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EmailCodeService} 的发送与校验行为。
 *
 * <p>{@link SysUserService} 是具体类而非接口（MyBatis-Plus {@code BaseService} 的既有形状），
 * 用 Mockito mock 掉——本类要验证的是"发码/校验的业务逻辑"，不是"如何查库"，
 * 查库交给核心自己的测试负责。
 */
@DisplayName("邮箱验证码：发送与校验")
class EmailCodeServiceTest extends AbstractGreenMailTest {

    private static final String CODE_KEY_PREFIX = "describeadmin:auth:email:code:";

    private CacheProvider cache;
    private SysUserService userService;
    private FrameworkAuthEmailProperties properties;
    private EmailCodeService service;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheProvider(1000);
        userService = mock(SysUserService.class);
        properties = new FrameworkAuthEmailProperties();
        properties.setCodeLength(6);
        properties.setCodeTtl(Duration.ofMinutes(5));
        properties.setSendInterval(Duration.ofSeconds(60));
        properties.setMaxVerifyAttempts(3);
        service = newService(properties);
    }

    private EmailCodeService newService(FrameworkAuthEmailProperties props) {
        JavaMailSenderImpl mailSender = mailSender();
        EmailCodeSendThrottle throttle = new EmailCodeSendThrottle(cache, props.getSendInterval());
        EmailCodeVerifyGuard guard = new EmailCodeVerifyGuard(cache, props.getMaxVerifyAttempts(), props.getCodeTtl());
        return new EmailCodeService(cache, userService, mailSender, throttle, guard, props);
    }

    private void registerUser(String email, long userId) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setEmail(email);
        when(userService.findByEmail(email)).thenReturn(user);
    }

    private String storedCode(String email) {
        return cache.get(CODE_KEY_PREFIX + email, String.class)
                .orElseThrow(() -> new AssertionError("验证码未写入缓存: " + email));
    }

    @Test
    @DisplayName("已注册邮箱：发码后收到一封含验证码的邮件")
    void sendCodeDeliversMailForRegisteredEmail() throws Exception {
        registerUser("alice@example.com", 1L);

        service.sendCode("alice@example.com");

        MimeMessage[] received = receivedMessages();
        assertThat(received).hasSize(1);
        // 用 Part#getContent() 而不是 GreenMailUtil#getBody：前者由 JavaMail 保证同时完成
        // Content-Transfer-Encoding 与字符集解码，验证码正文含中文时后者曾返回未解码的
        // base64 原文
        assertThat(received[0].getContent()).asString().contains(storedCode("alice@example.com"));
    }

    @Test
    @DisplayName("未注册邮箱：不真正发信，但接口视角仍然成功，避免账号枚举")
    void sendCodeSkipsUnregisteredEmailSilently() {
        when(userService.findByEmail("nobody@example.com")).thenReturn(null);

        assertThatNoException().isThrownBy(() -> service.sendCode("nobody@example.com"));

        assertThat(receivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("限流窗口内重复发码：第二次被拒绝，只收到一封")
    void sendCodeIsThrottled() {
        registerUser("alice@example.com", 1L);

        service.sendCode("alice@example.com");

        assertThatThrownBy(() -> service.sendCode("alice@example.com")).isInstanceOf(BizException.class);
        assertThat(receivedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("邮箱大小写不影响限流与查库，与登录失败计数的既有处理方式一致")
    void emailIsCaseInsensitive() {
        registerUser("alice@example.com", 1L);

        service.sendCode("Alice@Example.com");

        assertThatThrownBy(() -> service.sendCode("ALICE@EXAMPLE.COM")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("空邮箱直接拒绝")
    void rejectsBlankEmail() {
        assertThatThrownBy(() -> service.sendCode("")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.sendCode(null)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("验证码正确：校验通过，且验证码单次有效")
    void verifySucceedsThenCodeIsConsumed() {
        registerUser("alice@example.com", 1L);
        service.sendCode("alice@example.com");
        String code = storedCode("alice@example.com");

        assertThatNoException().isThrownBy(() -> service.verify("alice@example.com", code));
        // 单次有效：用过之后同一个码不能再验一次
        assertThatThrownBy(() -> service.verify("alice@example.com", code)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("验证码错误：拒绝")
    void verifyRejectsWrongCode() {
        registerUser("alice@example.com", 1L);
        service.sendCode("alice@example.com");

        assertThatThrownBy(() -> service.verify("alice@example.com", "000000"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("没有发过码就直接校验：拒绝，而不是空指针")
    void verifyWithoutSendingRejects() {
        assertThatThrownBy(() -> service.verify("never-sent@example.com", "123456"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("超过错误次数上限后，即便验证码本身仍在有效期内也直接拒绝")
    void verifyLocksAfterTooManyFailures() {
        registerUser("alice@example.com", 1L);
        service.sendCode("alice@example.com");
        String code = storedCode("alice@example.com");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.verify("alice@example.com", "wrong"))
                    .isInstanceOf(BizException.class);
        }

        // 这次传的验证码是对的，但已经超过尝试次数上限
        assertThatThrownBy(() -> service.verify("alice@example.com", code)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("验证码过期后拒绝")
    void verifyRejectsExpiredCode() throws Exception {
        FrameworkAuthEmailProperties shortProps = new FrameworkAuthEmailProperties();
        shortProps.setCodeLength(6);
        shortProps.setCodeTtl(Duration.ofMillis(200));
        shortProps.setSendInterval(Duration.ofMillis(10));
        shortProps.setMaxVerifyAttempts(5);
        EmailCodeService shortLived = newService(shortProps);
        registerUser("bob@example.com", 2L);

        shortLived.sendCode("bob@example.com");
        String code = storedCode("bob@example.com");

        Thread.sleep(400);

        assertThatThrownBy(() -> shortLived.verify("bob@example.com", code)).isInstanceOf(BizException.class);
    }
}

package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.auth.email.autoconfigure.FrameworkAuthEmailProperties;
import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.service.SysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 邮箱验证码的发送与校验，{@link EmailCodeAuthProvider} 的支撑服务。
 *
 * <p><b>防账号枚举</b>：邮箱不存在时仍返回"成功"（不真正发信），不对外暴露"这个邮箱是否已注册"
 * 这个信道——延续核心 {@code UsernamePasswordAuthProvider} 用 {@code DUMMY_HASH} 抹平
 * 存在性时序差异的同一种安全姿势，只是这里更直接：既然不发信，攻击者连时序差异都观察不到。
 *
 * <p><b>验证码单次有效</b>：验证成功后立即从 {@link CacheProvider} 摘除，防止同一个码被重放。
 */
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    private static final String CODE_KEY_PREFIX = "describeadmin:auth:email:code:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CacheProvider cache;
    private final SysUserService userService;
    private final JavaMailSender mailSender;
    private final EmailCodeSendThrottle throttle;
    private final EmailCodeVerifyGuard verifyGuard;
    private final FrameworkAuthEmailProperties properties;

    public EmailCodeService(CacheProvider cache, SysUserService userService, JavaMailSender mailSender,
                            EmailCodeSendThrottle throttle, EmailCodeVerifyGuard verifyGuard,
                            FrameworkAuthEmailProperties properties) {
        this.cache = cache;
        this.userService = userService;
        this.mailSender = mailSender;
        this.throttle = throttle;
        this.verifyGuard = verifyGuard;
        this.properties = properties;
    }

    /**
     * 生成验证码、写入缓存，并（如果邮箱已注册）发信。
     *
     * <p>不存在的邮箱同样"成功"返回，见类注释的防枚举说明。
     */
    public void sendCode(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BizException(ResultCode.BAD_REQUEST, "邮箱不能为空");
        }
        String normalized = normalize(email);
        throttle.assertCanSend(normalized);

        String code = generateCode();
        cache.put(codeKey(normalized), code, properties.getCodeTtl());

        SysUser user = userService.findByEmail(normalized);
        if (user == null) {
            log.debug("邮箱未注册，验证码已生成但不发送，避免账号枚举");
            return;
        }
        deliver(normalized, code);
    }

    /**
     * 校验验证码。失败时抛出 {@link BizException}，不返回布尔值——
     * 调用方（{@link EmailCodeAuthProvider}）需要的是"认证失败就抛异常"这一契约，
     * 与核心 {@code AuthProvider.authenticate()} 的既有形状一致。
     */
    public void verify(String email, String code) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new BizException(ResultCode.AUTH_FAILED, "邮箱或验证码不能为空");
        }
        String normalized = normalize(email);

        // 在比对之前拦截：达到错误次数上限后不应再消耗一次比对，与 LoginAttemptGuard 同一姿势
        verifyGuard.assertNotLocked(normalized);

        String expected = cache.get(codeKey(normalized), String.class).orElse(null);
        if (expected == null) {
            throw new BizException(ResultCode.AUTH_FAILED, "验证码不存在或已过期");
        }
        if (!expected.equals(code)) {
            verifyGuard.recordFailure(normalized);
            throw new BizException(ResultCode.AUTH_FAILED, "验证码错误");
        }

        // 验证码单次有效：用过之后立即失效，防止被重放；同时清零失败计数
        cache.evict(codeKey(normalized));
        verifyGuard.reset(normalized);
    }

    private void deliver(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        if (StringUtils.hasText(properties.getFromAddress())) {
            message.setFrom(properties.getFromAddress());
        }
        message.setSubject(properties.getSubject());
        message.setText("您的登录验证码是：" + code + "，"
                + properties.getCodeTtl().toMinutes() + " 分钟内有效。如非本人操作，请忽略本邮件。");
        mailSender.send(message);
    }

    private String generateCode() {
        int length = properties.getCodeLength();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String codeKey(String normalizedEmail) {
        return CODE_KEY_PREFIX + normalizedEmail;
    }

    /** 包内可见，供 {@link EmailCodeAuthProvider} 用同一套归一化规则查库。 */
    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

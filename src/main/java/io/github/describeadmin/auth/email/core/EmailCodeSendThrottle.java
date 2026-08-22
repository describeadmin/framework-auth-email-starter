package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;

import java.time.Duration;
import java.util.Locale;

/**
 * 邮箱验证码的发送限流：同一邮箱在 {@code interval} 内只能发一次。
 *
 * <p>手法与核心 {@code LoginAttemptGuard} 一致——原子自增、仅在键不存在时设置存活时间、
 * 不因为持续调用而续期。不同的是这里的 key 前缀与判定阈值都是本插件私有的，
 * 不与核心的登录失败计数混在一起，也不跨模块依赖 {@code framework-security-starter} 的
 * {@code core} 包（那是另一个 starter 的内部实现，插件不应该依赖别人的 core）。
 */
public class EmailCodeSendThrottle {

    private static final String KEY_PREFIX = "describeadmin:auth:email:send:";

    private final CacheProvider cache;
    private final Duration interval;

    public EmailCodeSendThrottle(CacheProvider cache, Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("发送间隔必须为正数，当前为: " + interval);
        }
        this.cache = cache;
        this.interval = interval;
    }

    /**
     * 在真正发信之前调用。达到限流阈值时抛异常，不再消耗一次真实的 SMTP 发信。
     *
     * @param email 邮箱地址，大小写不敏感
     */
    public void assertCanSend(String email) {
        long count = cache.increment(keyOf(email), 1L, interval);
        if (count > 1) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "发送过于频繁，请 " + interval.toSeconds() + " 秒后再试");
        }
    }

    private static String keyOf(String email) {
        // 统一大小写：邮箱地址大小写不敏感是约定俗成的行为，同 LoginAttemptGuard 对用户名的处理
        return KEY_PREFIX + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }
}

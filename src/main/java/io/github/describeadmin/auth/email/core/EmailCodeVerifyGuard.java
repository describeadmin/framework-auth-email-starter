package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;

import java.time.Duration;
import java.util.Locale;

/**
 * 邮箱验证码的防暴力破解：同一个验证码允许的错误尝试次数上限。
 *
 * <p>超过上限后即便验证码本身仍在有效期内也直接拒绝，强制调用方重新获取一个新验证码——
 * 6 位数字验证码的枚举空间远小于密码，若不限制尝试次数，{@link EmailCodeSendThrottle}
 * 的发送限流形同虚设（攻击者只需拿到一次验证码机会就足够暴力枚举）。
 */
public class EmailCodeVerifyGuard {

    private static final String KEY_PREFIX = "describeadmin:auth:email:fail:";

    private final CacheProvider cache;
    private final int maxAttempts;
    private final Duration ttl;

    /**
     * @param maxAttempts 允许的错误尝试次数
     * @param ttl         失败计数的存活时间，取与验证码相同的有效期——验证码过期后，
     *                    对应的失败计数没有继续存在的意义
     */
    public EmailCodeVerifyGuard(CacheProvider cache, int maxAttempts, Duration ttl) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("允许的错误尝试次数必须为正数，当前为: " + maxAttempts);
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("存活时间必须为正数，当前为: " + ttl);
        }
        this.cache = cache;
        this.maxAttempts = maxAttempts;
        this.ttl = ttl;
    }

    /** 在比对验证码之前调用。已达上限时直接拒绝，不再消耗一次比对。 */
    public void assertNotLocked(String email) {
        long failures = cache.get(keyOf(email), Long.class).orElse(0L);
        if (failures >= maxAttempts) {
            throw new BizException(ResultCode.AUTH_FAILED, "验证码错误次数过多，请重新获取验证码");
        }
    }

    /** 验证码比对失败后调用。 */
    public void recordFailure(String email) {
        cache.increment(keyOf(email), 1L, ttl);
    }

    /** 验证码比对成功后调用，清零计数。 */
    public void reset(String email) {
        cache.evict(keyOf(email));
    }

    private static String keyOf(String email) {
        return KEY_PREFIX + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }
}

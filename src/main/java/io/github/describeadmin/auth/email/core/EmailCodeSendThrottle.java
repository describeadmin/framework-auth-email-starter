package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.cache.api.CacheProvider;
import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * 邮箱验证码的发送限流，两个维度：
 * <ul>
 *   <li><b>邮箱</b>：同一邮箱在 {@code interval} 内只能发一次</li>
 *   <li><b>IP</b>：同一来源 IP 在 {@code ipWindow} 内最多发 {@code ipLimit} 次</li>
 * </ul>
 *
 * <p><b>为什么只按邮箱不够</b>：发码端点免登录，攻击者换着邮箱发，每个邮箱都只发一次，
 * 邮箱维度永远不触发——等于借本系统的 SMTP 账号对任意邮箱做邮件轰炸，打爆发信额度或被投诉拉黑。
 * IP 维度挡的是单一来源的这种批量发送。IP 只取 {@code request.getRemoteAddr()}，
 * 不信任 {@code X-Forwarded-For}（同核心 CLAUDE.md 4.14 第 1 条）。
 *
 * <p>手法与核心 {@code LoginAttemptGuard} 一致——原子自增、仅在键不存在时设置存活时间、
 * 不因为持续调用而续期。不同的是这里的 key 前缀与判定阈值都是本插件私有的，
 * 不与核心的登录失败计数混在一起，也不跨模块依赖 {@code framework-security-starter} 的
 * {@code core} 包（那是另一个 starter 的内部实现，插件不应该依赖别人的 core）。
 */
public class EmailCodeSendThrottle {

    private static final String KEY_PREFIX = "describeadmin:auth:email:send:";
    private static final String IP_KEY_PREFIX = "describeadmin:auth:email:send-ip:";

    private final CacheProvider cache;
    private final Duration interval;
    private final int ipLimit;
    private final Duration ipWindow;

    /** 只按邮箱限流，不启用 IP 维度。 */
    public EmailCodeSendThrottle(CacheProvider cache, Duration interval) {
        this(cache, interval, 0, null);
    }

    /**
     * @param ipLimit  同一 IP 在 {@code ipWindow} 内的发送上限；{@code <= 0} 表示关闭 IP 维度
     * @param ipWindow IP 维度的固定窗口，启用时必须为正数
     */
    public EmailCodeSendThrottle(CacheProvider cache, Duration interval, int ipLimit, Duration ipWindow) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("发送间隔必须为正数，当前为: " + interval);
        }
        if (ipLimit > 0 && (ipWindow == null || ipWindow.isZero() || ipWindow.isNegative())) {
            throw new IllegalArgumentException("IP 限流时间窗必须为正数，当前为: " + ipWindow);
        }
        this.cache = cache;
        this.interval = interval;
        this.ipLimit = ipLimit;
        this.ipWindow = ipWindow;
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

    /**
     * IP 维度，在 {@link #assertCanSend(String)} 之前调用。IP 维度关闭或拿不到 IP 时直接放行。
     *
     * <p>每次请求都计数，不论邮箱是否已注册、是否真的发了信——否则攻击者能借"未注册邮箱不发信"
     * 绕过计数，而发码接口对外又刻意不区分这两种情况。
     */
    public void assertCanSendFrom(String clientIp) {
        if (ipLimit <= 0 || !StringUtils.hasText(clientIp)) {
            return;
        }
        if (cache.increment(IP_KEY_PREFIX + clientIp, 1L, ipWindow) > ipLimit) {
            throw new BizException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
    }

    private static String keyOf(String email) {
        // 统一大小写：邮箱地址大小写不敏感是约定俗成的行为，同 LoginAttemptGuard 对用户名的处理
        return KEY_PREFIX + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }
}

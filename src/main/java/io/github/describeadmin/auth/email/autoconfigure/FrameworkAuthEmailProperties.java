package io.github.describeadmin.auth.email.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * framework-auth-email-starter 的配置项，前缀 {@code describeadmin.auth.email}。
 *
 * <p><b>刻意不重新定义 SMTP 连接参数</b>（host/port/username/password）——那些用 Spring Boot
 * 原生的 {@code spring.mail.*}，本插件只在 {@code JavaMailSender} 之上再包一层业务逻辑。
 * 两处配置各说各话时，"改了没生效"是最难查的一类问题（同 {@code RedisTokenStore} 的令牌
 * 有效期直接取 {@code FrameworkSecurityProperties} 而不是自立一套默认值，同一个理由）。
 */
@ConfigurationProperties(prefix = "describeadmin.auth.email")
public class FrameworkAuthEmailProperties {

    /**
     * 是否启用本插件。
     *
     * <p>关闭后行为等同于没有引入本插件——两层开关里的运行时那层。
     */
    private boolean enabled = true;

    /** 验证码长度（纯数字）。 */
    private int codeLength = 6;

    /** 验证码有效期。 */
    private Duration codeTtl = Duration.ofMinutes(5);

    /** 同一邮箱两次发码的最小间隔，防止被刷。 */
    private Duration sendInterval = Duration.ofSeconds(60);

    /** 同一个验证码允许的错误尝试次数，超过后即便验证码本身仍在有效期内也直接拒绝。 */
    private int maxVerifyAttempts = 5;

    /**
     * 发件地址，覆盖 {@code spring.mail.username} 作为 From 头。
     *
     * <p>留空时使用 {@code JavaMailSenderImpl} 的默认行为（通常是 SMTP 账号本身）。
     */
    private String fromAddress;

    /** 验证码邮件的主题。 */
    private String subject = "登录验证码";

    /** 可选的 email 通知渠道（{@code NotifyChannel(channel="email")}）。 */
    private final NotifyChannelConfig notifyChannel = new NotifyChannelConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public Duration getCodeTtl() {
        return codeTtl;
    }

    public void setCodeTtl(Duration codeTtl) {
        this.codeTtl = codeTtl;
    }

    public Duration getSendInterval() {
        return sendInterval;
    }

    public void setSendInterval(Duration sendInterval) {
        this.sendInterval = sendInterval;
    }

    public int getMaxVerifyAttempts() {
        return maxVerifyAttempts;
    }

    public void setMaxVerifyAttempts(int maxVerifyAttempts) {
        this.maxVerifyAttempts = maxVerifyAttempts;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public NotifyChannelConfig getNotifyChannel() {
        return notifyChannel;
    }

    /** 前缀 {@code describeadmin.auth.email.notify-channel}。 */
    public static class NotifyChannelConfig {

        /**
         * 是否额外注册一个 {@code NotifyChannel(channel="email")}，供其他场景
         * （如密码重置提醒）复用同一个 {@code JavaMailSender} 发邮件。
         *
         * <p>与验证码发送互不影响：验证码走 {@code EmailCodeService} 的独立路径，
         * 关掉本开关不影响登录能力，只是少了这一个通用发信渠道。
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

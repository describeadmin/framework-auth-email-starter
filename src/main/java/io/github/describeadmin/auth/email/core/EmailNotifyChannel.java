package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.auth.email.autoconfigure.FrameworkAuthEmailProperties;
import io.github.describeadmin.notify.api.NotifyChannel;
import io.github.describeadmin.notify.api.NotifyMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * 可选的 email 通知渠道，复用本插件已经具备的 {@link JavaMailSender}，
 * 供"验证码登录"之外的场景（密码重置提醒、账号禁用通知……）发邮件。
 *
 * <p><b>与验证码发送的路径互不交叉</b>：{@link EmailCodeService} 发验证码不经过
 * {@link io.github.describeadmin.notify.core.NotifyDispatcher}——验证码是高频、模板固定
 * 的路径，不该依赖一个更通用、更慢的分发层。本类只服务于"其他场景"，两条开关
 * （{@code describeadmin.auth.email.enabled} 与
 * {@code describeadmin.auth.email.notify-channel.enabled}）可以独立开合。
 *
 * <p><b>无条件注册</b>（本 Bean 本身不做 {@code @ConditionalOnMissingBean(NotifyChannel.class)}）——
 * {@code NotifyChannel} 是多实现共存模型，冲突检测交给 {@code NotifyDispatcher} 的构造函数
 * （重复的 {@link #channel()} 标识会在启动期被拒绝）。
 */
public class EmailNotifyChannel implements NotifyChannel {

    /** 本渠道的标识。 */
    public static final String CHANNEL = "email";

    private final JavaMailSender mailSender;
    private final FrameworkAuthEmailProperties properties;

    public EmailNotifyChannel(JavaMailSender mailSender, FrameworkAuthEmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void send(NotifyMessage message) {
        if (message.receivers().isEmpty()) {
            // NotifyMessage 契约本身不要求必须有接收方，本渠道视为无事可做而非错误
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(message.receivers().toArray(new String[0]));
        if (StringUtils.hasText(properties.getFromAddress())) {
            mail.setFrom(properties.getFromAddress());
        }
        if (StringUtils.hasText(message.title())) {
            mail.setSubject(message.title());
        }
        mail.setText(message.content());
        mailSender.send(mail);
    }
}

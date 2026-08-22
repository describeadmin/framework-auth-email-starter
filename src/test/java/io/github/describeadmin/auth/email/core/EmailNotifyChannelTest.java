package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.auth.email.AbstractGreenMailTest;
import io.github.describeadmin.auth.email.autoconfigure.FrameworkAuthEmailProperties;
import io.github.describeadmin.notify.api.NotifyMessage;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmailNotifyChannel} 的契约验证——真实投递到 GreenMail，不 mock {@code JavaMailSender}。
 */
@DisplayName("email 通知渠道")
class EmailNotifyChannelTest extends AbstractGreenMailTest {

    @Test
    @DisplayName("channel() 固定为 email，供 NotifyDispatcher 路由")
    void channelIsEmail() {
        EmailNotifyChannel channel = new EmailNotifyChannel(mailSender(), new FrameworkAuthEmailProperties());

        assertThat(channel.channel()).isEqualTo("email");
    }

    @Test
    @DisplayName("发送后 GreenMail 收到一封标题、正文、收件人都匹配的邮件")
    void sendDeliversMail() throws Exception {
        EmailNotifyChannel channel = new EmailNotifyChannel(mailSender(), new FrameworkAuthEmailProperties());

        channel.send(new NotifyMessage("密码已重置", "您的密码已被管理员重置，请重新登录。",
                List.of("alice@example.com")));

        MimeMessage[] received = receivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("密码已重置");
        // 用 Part#getContent() 而不是 GreenMailUtil#getBody：前者由 JavaMail 保证同时完成
        // Content-Transfer-Encoding 与字符集解码，后者在非 ASCII 正文下曾返回未解码的
        // base64 原文——同 CLAUDE.md 3.6 那条"断言要比对具体值"的字符集陷阱是同一类问题，
        // 只是这次发生在邮件 MIME 编码而不是数据库/文件编码上
        assertThat(received[0].getContent()).asString().contains("您的密码已被管理员重置");
        assertThat(received[0].getAllRecipients()).hasSize(1)
                .extracting(Object::toString)
                .containsExactly("alice@example.com");
    }

    @Test
    @DisplayName("没有收件人时视为无事可做，不发信也不报错")
    void sendWithNoReceiversIsNoop() {
        EmailNotifyChannel channel = new EmailNotifyChannel(mailSender(), new FrameworkAuthEmailProperties());

        channel.send(new NotifyMessage("标题", "正文", List.of()));

        assertThat(receivedMessages()).isEmpty();
    }

    @Test
    @DisplayName("可以同时发给多个收件人")
    void sendToMultipleReceivers() throws Exception {
        EmailNotifyChannel channel = new EmailNotifyChannel(mailSender(), new FrameworkAuthEmailProperties());

        channel.send(new NotifyMessage("通知", "内容", List.of("a@example.com", "b@example.com")));

        // GreenMail 按收件邮箱存储：同一封邮件投给 2 个收件人会在收件箱视角下出现 2 条记录，
        // 但每条记录的 To 头都完整列出全部收件人——这是 GreenMail 的存储模型，不是发送出了两封信
        MimeMessage[] received = receivedMessages();
        assertThat(received).hasSize(2);
        assertThat(received[0].getAllRecipients()).hasSize(2);
    }
}

package io.github.describeadmin.auth.email;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * 起一个真实的假 SMTP 服务器（GreenMail）供本模块测试使用。
 *
 * <p><b>为什么不 mock {@code JavaMailSender}</b>：本插件的价值全在"发出去的是不是一封能被
 * 真实 SMTP 协议收到的邮件"——mock 掉 {@code JavaMailSender} 等于把要验证的部分假设成立了，
 * 与 {@code framework-cache-redis-starter} 用 Testcontainers 起真实 Redis 而不 mock
 * {@code RedisTemplate} 是同一个理由。GreenMail 是纯 JVM 内的假 SMTP 实现，不需要 Docker，
 * 因此这里用类级共享实例（{@code @BeforeEach} 重启）而不是像 {@code AbstractRedisTest}
 * 那样用静态单例——启动成本本身就很低，没有必要跨测试类共享。
 */
public abstract class AbstractGreenMailTest {

    private GreenMail greenMail;

    @BeforeEach
    void startGreenMail() {
        greenMail = new GreenMail(ServerSetupTest.SMTP);
        greenMail.start();
    }

    @AfterEach
    void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    /** 配置好指向 GreenMail 的 {@link JavaMailSenderImpl}，供被测代码直接使用。 */
    protected JavaMailSenderImpl mailSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        return sender;
    }

    /** 当前 GreenMail 收到的全部邮件。 */
    protected MimeMessage[] receivedMessages() {
        return greenMail.getReceivedMessages();
    }
}

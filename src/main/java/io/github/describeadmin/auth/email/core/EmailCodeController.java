package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.common.api.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 发送邮箱验证码。
 *
 * <p><b>为什么不改核心的 {@code AuthController}</b>：发验证码是本插件独有的能力形状
 * （限流策略、SMTP 细节），CLAUDE.md §4.2 "框架核心代码里不允许出现任何具体实现的名字"——
 * 即便"邮箱"本身不算厂商名，核心也不该为这一种登录方式的专属动作开洞。
 *
 * <p><b>必须由业务方自行加入白名单</b>：本端点需要在未登录状态下调用（登录前才需要发码），
 * 但核心的 {@code FrameworkSecurityAutoConfiguration.BUILT_IN_PERMIT_ALL} 是框架内置列表，
 * 不允许出现插件专属路径——这是分层的硬边界，核心不知道有本插件存在。业务方需要在
 * {@code application.yml} 里追加：
 * <pre>
 * describeadmin:
 *   security:
 *     permit-all:
 *       - /api/auth/email/code
 * </pre>
 * 不追加会直接收到 401，且报错信息不会指向这一步——本插件的 README 必须提醒这一点。
 */
@RestController
@RequestMapping("/api/auth/email")
public class EmailCodeController {

    private final EmailCodeService codeService;

    public EmailCodeController(EmailCodeService codeService) {
        this.codeService = codeService;
    }

    /**
     * 发送验证码到指定邮箱。
     *
     * <p>无论邮箱是否已注册，只要没有触发限流，本接口都返回成功——不对外暴露
     * "这个邮箱是否已注册"这个信道，见 {@link EmailCodeService} 类注释。
     */
    @PostMapping("/code")
    public Result<Void> sendCode(@RequestBody Map<String, String> body) {
        codeService.sendCode(body.get("email"));
        return Result.ok();
    }
}

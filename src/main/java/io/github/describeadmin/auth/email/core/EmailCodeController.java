package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
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
 * <p>本端点要在未登录状态下调用（登录前才需要发码）。免登录由插件注册的
 * {@code PermitAllContributor} 贡献，见 {@code FrameworkAuthEmailAutoConfiguration#emailCodePermitAll}；
 * 不写进核心的 {@code BUILT_IN_PERMIT_ALL}，核心不知道有本插件存在。
 */
@RestController
@RequestMapping("/api/auth/email")
public class EmailCodeController {

    /** 发码端点的完整路径，与下方 {@code @RequestMapping} + {@code @PostMapping} 拼出来的一致。 */
    public static final String SEND_CODE_PATH = "/api/auth/email/code";

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
    public Result<Void> sendCode(@RequestBody Map<String, String> body, HttpServletRequest request) {
        // 限流只认直连地址，不信任 X-Forwarded-For（客户端可随意伪造）
        codeService.sendCode(body.get("email"), request.getRemoteAddr());
        return Result.ok();
    }
}

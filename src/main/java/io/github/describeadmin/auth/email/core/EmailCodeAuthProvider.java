package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.common.api.ResultCode;
import io.github.describeadmin.security.api.AuthProvider;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.AuthUser;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.service.SysUserService;

/**
 * 邮箱验证码（无密码）登录。
 *
 * <p>取 userId 的路径命中 docs/registry.md 准入规范第 10 条第一种情形——凭证（邮箱）
 * 已经是核心 {@code sys_user} 的常规字段：直接注入 {@link SysUserService} 查
 * {@code findByEmail}，拿到 userId 后调核心 {@link AuthUserLoader#loadByUserId}
 * 拼出角色/权限/数据权限/首页路径俱全的 {@link AuthUser}，本插件不需要、也不应该
 * 自建任何用户映射表，更不会重新查 {@code sys_role}/{@code sys_user_role} 自己拼一遍。
 */
public class EmailCodeAuthProvider implements AuthProvider {

    public static final String TYPE = "email";

    private final EmailCodeService codeService;
    private final SysUserService userService;
    private final AuthUserLoader authUserLoader;

    public EmailCodeAuthProvider(EmailCodeService codeService, SysUserService userService,
                                 AuthUserLoader authUserLoader) {
        this.codeService = codeService;
        this.userService = userService;
        this.authUserLoader = authUserLoader;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public LoginUser authenticate(AuthRequest request) {
        String email = request.getString("email");
        String code = request.getString("code");

        // 校验通过即代表"这个邮箱收到了正确的验证码"——邮箱不存在时 EmailCodeService
        // 从不真正发信，攻击者拿不到验证码，因此这一步失败已经隐含了大部分的账号枚举防护
        codeService.verify(email, code);

        SysUser user = userService.findByEmail(EmailCodeService.normalize(email));
        // 正常流程下不会走到这里为空：能验证通过说明验证码确实发给了这个邮箱对应的账号。
        // 仍然防御性判空，不假设上游一定正确（账号可能在发码之后、验证之前被删除）
        if (user == null) {
            throw new BizException(ResultCode.AUTH_FAILED, "认证失败");
        }

        AuthUser authUser = authUserLoader.loadByUserId(user.getId())
                .orElseThrow(() -> new BizException(ResultCode.AUTH_FAILED, "认证失败"));

        if (!authUser.isEnabled()) {
            throw new BizException(ResultCode.AUTH_FAILED, "账号已被禁用");
        }
        return authUser.toLoginUser(TYPE);
    }
}

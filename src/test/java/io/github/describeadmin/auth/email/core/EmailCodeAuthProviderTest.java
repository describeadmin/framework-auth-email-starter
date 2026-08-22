package io.github.describeadmin.auth.email.core;

import io.github.describeadmin.common.api.BizException;
import io.github.describeadmin.security.api.AuthRequest;
import io.github.describeadmin.security.api.AuthUser;
import io.github.describeadmin.security.api.AuthUserLoader;
import io.github.describeadmin.security.api.LoginUser;
import io.github.describeadmin.system.entity.SysUser;
import io.github.describeadmin.system.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link EmailCodeAuthProvider} 的认证流程。发码/校验的具体逻辑已由
 * {@link EmailCodeServiceTest} 覆盖，本类只验证"验证码通过之后如何拼出 LoginUser"这一段。
 */
@DisplayName("邮箱验证码登录：AuthProvider")
class EmailCodeAuthProviderTest {

    private EmailCodeService codeService;
    private SysUserService userService;
    private AuthUserLoader authUserLoader;
    private EmailCodeAuthProvider provider;

    @BeforeEach
    void setUp() {
        codeService = mock(EmailCodeService.class);
        userService = mock(SysUserService.class);
        authUserLoader = mock(AuthUserLoader.class);
        provider = new EmailCodeAuthProvider(codeService, userService, authUserLoader);
    }

    private AuthRequest request(String email, String code) {
        return new AuthRequest("email", Map.of("email", email, "code", code));
    }

    @Test
    @DisplayName("type() 固定为 email，供 /api/auth/providers 与请求路由使用")
    void typeIsEmail() {
        assertThat(provider.type()).isEqualTo("email");
        assertThat(provider.supports("email")).isTrue();
        assertThat(provider.supports("password")).isFalse();
    }

    @Test
    @DisplayName("验证码通过、账号存在且启用：返回完整的 LoginUser，authType 为 email")
    void authenticateSucceeds() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(userService.findByEmail("alice@example.com")).thenReturn(user);

        AuthUser authUser = new AuthUser(1L, "alice", null, "小爱", true,
                Set.of("ADMIN"), Set.of("system:user:list"));
        when(authUserLoader.loadByUserId(1L)).thenReturn(Optional.of(authUser));

        LoginUser result = provider.authenticate(request("alice@example.com", "123456"));

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getNickname()).isEqualTo("小爱");
        assertThat(result.getAuthType()).isEqualTo("email");
        assertThat(result.getRoles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("验证码本身不对：codeService.verify 抛出的异常直接透传，不继续查库")
    void authenticateFailsWhenCodeInvalid() {
        doThrow(new BizException(io.github.describeadmin.common.api.ResultCode.AUTH_FAILED, "验证码错误"))
                .when(codeService).verify(eq("alice@example.com"), any());

        assertThatThrownBy(() -> provider.authenticate(request("alice@example.com", "wrong")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("验证码通过但账号已不存在：认证失败而不是空指针")
    void authenticateFailsWhenUserDisappeared() {
        when(userService.findByEmail("ghost@example.com")).thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(request("ghost@example.com", "123456")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("loadByUserId 返回空：认证失败")
    void authenticateFailsWhenLoadByUserIdEmpty() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(userService.findByEmail("alice@example.com")).thenReturn(user);
        when(authUserLoader.loadByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(request("alice@example.com", "123456")))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("账号已被禁用：认证失败，即便验证码正确")
    void authenticateFailsWhenAccountDisabled() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(userService.findByEmail("alice@example.com")).thenReturn(user);

        AuthUser disabledUser = new AuthUser(1L, "alice", null, "小爱", false, Set.of(), Set.of());
        when(authUserLoader.loadByUserId(1L)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> provider.authenticate(request("alice@example.com", "123456")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("禁用");
    }

    @Test
    @DisplayName("查库前对邮箱做大小写归一化，与发码时的归一化规则一致")
    void queriesWithNormalizedEmail() {
        // 只给全小写形式打桩：若 provider 用原始大小写去查库会得到 null，
        // 从而暴露"没有归一化"这个错误
        SysUser user = new SysUser();
        user.setId(1L);
        user.setEmail("alice@example.com");
        when(userService.findByEmail("alice@example.com")).thenReturn(user);
        when(authUserLoader.loadByUserId(1L)).thenReturn(Optional.of(
                new AuthUser(1L, "alice", null, "小爱", true, Set.of(), Set.of())));

        LoginUser result = provider.authenticate(request("Alice@Example.com", "123456"));

        assertThat(result.getUserId()).isEqualTo(1L);
    }
}

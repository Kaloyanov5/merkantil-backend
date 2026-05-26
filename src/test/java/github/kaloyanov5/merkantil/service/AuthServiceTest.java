package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.request.LoginRequest;
import github.kaloyanov5.merkantil.dto.request.RegisterRequest;
import github.kaloyanov5.merkantil.dto.response.AuthResponse;
import github.kaloyanov5.merkantil.entity.Role;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.exception.RateLimitedException;
import github.kaloyanov5.merkantil.exception.TwoFactorRequiredException;
import github.kaloyanov5.merkantil.repository.UserRepository;
import github.kaloyanov5.merkantil.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private SecurityContextRepository securityContextRepository;
    @Mock private LoginSessionService loginSessionService;
    @Mock private PersistentTokenBasedRememberMeServices rememberMeServices;
    @Mock private EmailService emailService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private RateLimiterService rateLimiterService;

    @InjectMocks
    private AuthService authService;

    @Mock private HttpServletRequest httpRequest;
    @Mock private HttpServletResponse httpResponse;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ---------- REGISTER ----------

    @Test
    @DisplayName("register: creates user, seeds $10,000 balance, sends verification email")
    void register_success_sendsVerificationEmail() {
        RegisterRequest req = new RegisterRequest("Ana", "Test", "ana@example.com", "password123");

        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L);
            return u;
        });

        AuthResponse response = authService.register(req);

        assertThat(response.message()).contains("verify");
        assertThat(response.user().email()).isEqualTo("ana@example.com");
        assertThat(response.user().balance()).isEqualByComparingTo("10000");

        verify(emailService).sendVerificationEmail(eq("ana@example.com"), any(String.class));
        verify(valueOps).set(startsWith("email:verify:"), eq("42"), any());
    }

    @Test
    @DisplayName("register: duplicate email throws")
    void register_duplicateEmail_throws() {
        RegisterRequest req = new RegisterRequest(null, null, "taken@example.com", "password123");

        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    // ---------- LOGIN RATE LIMITING ----------

    @Test
    @DisplayName("login: throws RateLimitedException when attempt counter at threshold")
    void login_rateLimited_throws() {
        LoginRequest req = new LoginRequest("victim@example.com", "wrong", false);

        doThrow(new RateLimitedException(600L))
                .when(rateLimiterService).check(eq("login:victim@example.com"), anyInt(), any());

        assertThatThrownBy(() -> authService.login(req, httpRequest, httpResponse))
                .isInstanceOf(RateLimitedException.class)
                .extracting("retryAfterSeconds").isEqualTo(600L);

        // Should NOT attempt to authenticate while rate-limited
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("login: bad credentials increments attempts counter")
    void login_badCredentials_incrementsAttempts() {
        LoginRequest req = new LoginRequest("user@example.com", "wrong", false);

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req, httpRequest, httpResponse))
                .isInstanceOf(BadCredentialsException.class);

        // Failed login must be recorded against the rate limiter
        verify(rateLimiterService).penalize(eq("login:user@example.com"), any());
    }

    // ---------- 2FA ----------

    @Test
    @DisplayName("login: 2FA enabled user → throws TwoFactorRequiredException (no session created)")
    void login_2faEnabled_throwsTwoFactorRequired() {
        LoginRequest req = new LoginRequest("2fa@example.com", "correct", false);

        User user = new User();
        user.setId(7L);
        user.setEmail("2fa@example.com");
        user.setRole(Role.USER);
        user.setTwoFactorEnabled(true);
        user.setBanned(false);
        user.setPassword("hashed");

        when(valueOps.get(any())).thenReturn(null); // not rate-limited

        CustomUserDetails principal = CustomUserDetails.from(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, "correct", principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(req, httpRequest, httpResponse))
                .isInstanceOf(TwoFactorRequiredException.class)
                .extracting("tempToken").asString().isNotBlank();

        // 2FA email should be sent
        verify(emailService).send2faEmail(eq("2fa@example.com"), any(String.class));
        // No session should be created and no login record stored
        verify(loginSessionService, never()).saveSession(any(), any(), any());
        verify(httpRequest, never()).getSession(true);
    }

    @Test
    @DisplayName("verify2fa: invalid temp token throws")
    void verify2fa_invalidToken_throws() {
        when(valueOps.get("2fa:pending:bogus")).thenReturn(null);

        assertThatThrownBy(() -> authService.verify2fa("bogus", "123456", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    @DisplayName("verify2fa: wrong code increments attempts and throws")
    void verify2fa_wrongCode_incrementsAttempts() {
        when(valueOps.get("2fa:pending:tok123")).thenReturn("7");
        when(valueOps.get("2fa:otp:7")).thenReturn("000000"); // correct code

        assertThatThrownBy(() -> authService.verify2fa("tok123", "999999", httpRequest, httpResponse))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired code");

        verify(rateLimiterService).penalize(eq("2fa:7"), any());
    }

    // ---------- PASSWORD RESET ----------

    @Test
    @DisplayName("forgotPassword: unknown email returns silently (no enumeration)")
    void forgotPassword_unknownEmail_silentlySucceeds() {
        when(userRepository.existsByEmail("ghost@example.com")).thenReturn(false);

        authService.forgotPassword("ghost@example.com");

        // No email sent, no Redis writes — does not reveal account existence
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
        verify(valueOps, never()).set(any(), any(), any());
    }

    @Test
    @DisplayName("forgotPassword: known email sends reset code")
    void forgotPassword_knownEmail_sendsCode() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        authService.forgotPassword("user@example.com");

        verify(emailService).sendPasswordResetEmail(eq("user@example.com"), any(String.class));
        verify(valueOps).set(eq("password:reset:user@example.com"), any(String.class), any());
    }

    @Test
    @DisplayName("resetPassword: rate-limited when too many wrong codes")
    void resetPassword_rateLimited_throws() {
        doThrow(new RateLimitedException(300L))
                .when(rateLimiterService).check(eq("reset:user@example.com"), anyInt(), any());

        assertThatThrownBy(() -> authService.resetPassword("user@example.com", "123456", "newpass"))
                .isInstanceOf(RateLimitedException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword: success with correct code updates password")
    void resetPassword_validCode_succeeds() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("oldhash");
        user.setRole(Role.USER);
        user.setBalance(BigDecimal.ZERO);
        user.setBanned(false);

        when(valueOps.get("password:reset:user@example.com")).thenReturn("123456");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("newhash");

        authService.resetPassword("user@example.com", "123456", "newpass");

        assertThat(user.getPassword()).isEqualTo("newhash");
        verify(redisTemplate).delete("password:reset:user@example.com");
    }
}

package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.exception.TwoFactorRequiredException;
import github.kaloyanov5.merkantil.repository.UserRepository;
import github.kaloyanov5.merkantil.dto.response.AuthResponse;
import github.kaloyanov5.merkantil.dto.request.LoginRequest;
import github.kaloyanov5.merkantil.dto.request.RegisterRequest;
import github.kaloyanov5.merkantil.dto.response.UserResponse;
import github.kaloyanov5.merkantil.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSessionService loginSessionService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;
    private final RateLimiterService rateLimiterService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String VERIFY_PREFIX = "email:verify:";
    private static final Duration VERIFY_TTL = Duration.ofHours(24);

    private static final String RESET_PREFIX = "password:reset:";
    private static final Duration RESET_TTL = Duration.ofMinutes(15);

    private static final String TWO_FA_OTP_PREFIX = "2fa:otp:";
    private static final String TWO_FA_PENDING_PREFIX = "2fa:pending:";
    private static final Duration TWO_FA_TTL = Duration.ofMinutes(5);

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    /** Caps on auth-endpoint hits per source IP / per target email. */
    private static final int MAX_LOGIN_PER_IP = 30;
    private static final Duration LOGIN_IP_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_REGISTER_PER_IP = 5;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int MAX_VERIFY_EMAIL_PER_IP = 10;
    private static final Duration VERIFY_EMAIL_WINDOW = Duration.ofHours(1);
    private static final int MAX_FORGOT_PER_EMAIL = 3;
    private static final int MAX_FORGOT_PER_IP = 10;
    private static final Duration FORGOT_WINDOW = Duration.ofHours(1);

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        // Per-IP throttle to prevent scripted account-farms (each new user
        // gets a $10k seeded balance, so creation has a real cost).
        if (clientIp != null) {
            rateLimiterService.enforce("register:" + clientIp, MAX_REGISTER_PER_IP, REGISTER_WINDOW);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setBalance(java.math.BigDecimal.valueOf(10000));
        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFY_PREFIX + token, savedUser.getId().toString(), VERIFY_TTL);
        emailService.sendVerificationEmail(savedUser.getEmail(), token);

        return new AuthResponse("User registered successfully. Please check your email to verify your account.", mapToUserResponse(savedUser));
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // Per-IP throttle in parallel with the email-keyed limiter — blocks
        // credential-stuffing campaigns that rotate emails on a single IP and
        // attacker-driven lockout-by-burning-attempts of arbitrary victims.
        String clientIp = httpRequest.getRemoteAddr();
        if (clientIp != null) {
            rateLimiterService.enforce("login-ip:" + clientIp, MAX_LOGIN_PER_IP, LOGIN_IP_WINDOW);
        }

        String rateKey = "login:" + request.email().toLowerCase();
        rateLimiterService.check(rateKey, MAX_ATTEMPTS, ATTEMPT_WINDOW);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            rateLimiterService.penalize(rateKey, ATTEMPT_WINDOW);
            throw e;
        }

        // Password was correct — clear attempts even if 2FA is still pending
        rateLimiterService.clear(rateKey);

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // If 2FA is enabled, send OTP and pause login — session not created yet
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            String tempToken = UUID.randomUUID().toString();
            // Bind the temp-token to the requesting client IP so an attacker
            // who intercepts the tempToken cannot redeem it from a different
            // device/location. Stored as "<userId>|<clientIp>".
            redisTemplate.opsForValue().set(TWO_FA_OTP_PREFIX + user.getId(), code, TWO_FA_TTL);
            redisTemplate.opsForValue().set(TWO_FA_PENDING_PREFIX + tempToken,
                    user.getId() + "|" + (clientIp != null ? clientIp : ""), TWO_FA_TTL);
            emailService.send2faEmail(user.getEmail(), code);
            throw new TwoFactorRequiredException(tempToken);
        }

        // Rotate session ID to prevent session fixation
        jakarta.servlet.http.HttpSession oldSession = httpRequest.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        httpRequest.getSession(true);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // Save login session record (IP, device info)
        loginSessionService.saveSession(user.getId(), httpRequest.getSession().getId(), httpRequest);

        // Set remember-me cookie if requested
        if (request.rememberMe()) {
            // Wrap request to inject the remember-me parameter since we use JSON (not form)
            HttpServletRequestWrapper rememberMeRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getParameter(String name) {
                    if ("remember-me".equals(name)) return "true";
                    return super.getParameter(name);
                }
            };
            rememberMeServices.loginSuccess(rememberMeRequest, httpResponse, authentication);
        }

        return new AuthResponse("User logged in successfully", mapToUserResponse(user));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionId = request.getSession(false) != null ? request.getSession(false).getId() : null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        rememberMeServices.logout(request, response, auth);
        new SecurityContextLogoutHandler().logout(request, response, auth);
        if (sessionId != null) {
            loginSessionService.deleteSession(sessionId);
        }
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails principal) {
            return userRepository.findById(principal.getId())
                    .orElseThrow(() -> new IllegalStateException("User not found"));
        }
        throw new IllegalStateException("User not authenticated");
    }

    public AuthResponse verify2fa(String tempToken, String code, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String pending = redisTemplate.opsForValue().get(TWO_FA_PENDING_PREFIX + tempToken);
        if (pending == null) {
            throw new IllegalArgumentException("Invalid or expired session, please log in again");
        }

        // Stored format is "<userId>|<clientIp>". Reject the verify if the
        // current client IP differs from the one that initiated the login —
        // an intercepted tempToken cannot be redeemed from another device.
        String[] parts = pending.split("\\|", 2);
        String userId = parts[0];
        String boundIp = parts.length > 1 ? parts[1] : null;
        String currentIp = httpRequest.getRemoteAddr();
        if (boundIp != null && !boundIp.equals(currentIp)) {
            log.warn("2FA verify denied — tempToken bound to {} but request from {}", boundIp, currentIp);
            redisTemplate.delete(TWO_FA_PENDING_PREFIX + tempToken);
            throw new IllegalArgumentException("Invalid or expired session, please log in again");
        }

        rateLimiterService.check("2fa:" + userId, MAX_ATTEMPTS, ATTEMPT_WINDOW);

        String storedCode = redisTemplate.opsForValue().get(TWO_FA_OTP_PREFIX + userId);
        if (storedCode == null || !storedCode.equals(code)) {
            rateLimiterService.penalize("2fa:" + userId, ATTEMPT_WINDOW);
            throw new IllegalArgumentException("Invalid or expired code");
        }

        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalStateException("User not found"));

        CustomUserDetails userDetails = CustomUserDetails.from(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        loginSessionService.saveSession(user.getId(), httpRequest.getSession().getId(), httpRequest);

        redisTemplate.delete(TWO_FA_PENDING_PREFIX + tempToken);
        redisTemplate.delete(TWO_FA_OTP_PREFIX + userId);
        rateLimiterService.clear("2fa:" + userId);

        return new AuthResponse("Login successful", mapToUserResponse(user));
    }

    @Transactional
    public void enable2fa(String currentPassword) {
        User user = getCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disable2fa(String currentPassword) {
        User user = getCurrentUser();
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
    }

    public void forgotPassword(String email, String clientIp) {
        // Throttle both per-email (block targeted inbox-bombing of a single
        // user) and per-IP (block fan-out account-enumeration via timing
        // signals on the email-send call).
        rateLimiterService.enforce("forgot:" + email.toLowerCase(), MAX_FORGOT_PER_EMAIL, FORGOT_WINDOW);
        if (clientIp != null) {
            rateLimiterService.enforce("forgot-ip:" + clientIp, MAX_FORGOT_PER_IP, FORGOT_WINDOW);
        }

        // Don't reveal whether the email exists — silently return if not found
        if (!userRepository.existsByEmail(email)) {
            return;
        }
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        redisTemplate.opsForValue().set(RESET_PREFIX + email, code, RESET_TTL);
        emailService.sendPasswordResetEmail(email, code);
    }

    @Transactional
    public void resetPassword(String email, String code, String newPassword) {
        rateLimiterService.check("reset:" + email, MAX_ATTEMPTS, ATTEMPT_WINDOW);

        String key = RESET_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null || !storedCode.equals(code)) {
            rateLimiterService.penalize("reset:" + email, ATTEMPT_WINDOW);
            throw new IllegalArgumentException("Invalid or expired reset code");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redisTemplate.delete(key);
        rateLimiterService.clear("reset:" + email);
    }

    @Transactional
    public void verifyEmail(String token, String clientIp) {
        if (clientIp != null) {
            // Cap brute-force attempts against the UUID-shaped verification token
            rateLimiterService.enforce("verify-email:" + clientIp, MAX_VERIFY_EMAIL_PER_IP, VERIFY_EMAIL_WINDOW);
        }
        String key = VERIFY_PREFIX + token;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid or expired verification token");
        }
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setEmailVerified(true);
        userRepository.save(user);
        redisTemplate.delete(key);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getBalance(), user.getCreatedAt(), user.getEmailVerified(), user.getTwoFactorEnabled(), user.getBanned());
    }
}

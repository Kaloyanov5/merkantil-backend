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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSessionService loginSessionService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String VERIFY_PREFIX = "email:verify:";
    private static final Duration VERIFY_TTL = Duration.ofHours(24);

    private static final String RESET_PREFIX = "password:reset:";
    private static final Duration RESET_TTL = Duration.ofMinutes(15);

    private static final String TWO_FA_OTP_PREFIX = "2fa:otp:";
    private static final String TWO_FA_PENDING_PREFIX = "2fa:pending:";
    private static final Duration TWO_FA_TTL = Duration.ofMinutes(5);

    private static final String ATTEMPT_PREFIX = "auth:attempts:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }
        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setBalance(java.math.BigDecimal.valueOf(10000));
        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFY_PREFIX + token, savedUser.getId().toString(), VERIFY_TTL);
        emailService.sendVerificationEmail(savedUser.getEmail(), token);

        return new AuthResponse("User registered successfully. Please check your email to verify your account.", mapToUserResponse(savedUser));
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // If 2FA is enabled, send OTP and pause login — session not created yet
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
            String tempToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(TWO_FA_OTP_PREFIX + user.getId(), code, TWO_FA_TTL);
            redisTemplate.opsForValue().set(TWO_FA_PENDING_PREFIX + tempToken, user.getId().toString(), TWO_FA_TTL);
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
        if (request.isRememberMe()) {
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
        String userId = redisTemplate.opsForValue().get(TWO_FA_PENDING_PREFIX + tempToken);
        if (userId == null) {
            throw new IllegalArgumentException("Invalid or expired session, please log in again");
        }

        checkRateLimit("2fa:" + userId);

        String storedCode = redisTemplate.opsForValue().get(TWO_FA_OTP_PREFIX + userId);
        if (storedCode == null || !storedCode.equals(code)) {
            incrementAttempts("2fa:" + userId);
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
        redisTemplate.delete(ATTEMPT_PREFIX + "2fa:" + userId);

        return new AuthResponse("Login successful", mapToUserResponse(user));
    }

    @Transactional
    public void enable2fa() {
        User user = getCurrentUser();
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disable2fa() {
        User user = getCurrentUser();
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
    }

    public void forgotPassword(String email) {
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
        checkRateLimit("reset:" + email);

        String key = RESET_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);
        if (storedCode == null || !storedCode.equals(code)) {
            incrementAttempts("reset:" + email);
            throw new IllegalArgumentException("Invalid or expired reset code");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redisTemplate.delete(key);
        redisTemplate.delete(ATTEMPT_PREFIX + "reset:" + email);
    }

    @Transactional
    public void verifyEmail(String token) {
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

    private void checkRateLimit(String identifier) {
        String key = ATTEMPT_PREFIX + identifier;
        String attempts = redisTemplate.opsForValue().get(key);
        if (attempts != null && Integer.parseInt(attempts) >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Too many attempts. Please try again later.");
        }
    }

    private void incrementAttempts(String identifier) {
        String key = ATTEMPT_PREFIX + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ATTEMPT_WINDOW);
        }
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getBalance(), user.getCreatedAt(), user.getEmailVerified(), user.getTwoFactorEnabled(), user.getBanned());
    }
}

package github.kaloyanov5.merkantil.identity.service;

import github.kaloyanov5.merkantil.common.ratelimit.RateLimiterService;
import github.kaloyanov5.merkantil.identity.controller.dto.request.ChangePasswordRequest;
import github.kaloyanov5.merkantil.identity.controller.dto.response.UserResponse;
import github.kaloyanov5.merkantil.identity.model.User;
import github.kaloyanov5.merkantil.identity.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionService loginSessionService;
    private final RateLimiterService rateLimiterService;

    /** Caps directory-style email lookups per looking user (anti-enumeration). */
    private static final int MAX_LOOKUPS_PER_WINDOW = 30;
    private static final Duration LOOKUP_WINDOW = Duration.ofHours(1);

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must differ from current password");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Revoke all active sessions across all devices
        loginSessionService.revokeAllSessions(user.getId());
    }

    @Deprecated
    public Map<String, String> lookupByEmail(String email, Long lookerId) {
        // Aggressive per-caller throttle so a single logged-in attacker cannot
        // iterate the userbase by email and harvest first/last-name PII.
        rateLimiterService.enforce("lookup:" + lookerId, MAX_LOOKUPS_PER_WINDOW, LOOKUP_WINDOW);
        return userRepository.findByEmail(email)
                .map(u -> Map.of("firstName", u.getFirstName(), "lastName", u.getLastName()))
                .orElse(null);
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserResponse(user);
    }

    public Page<UserResponse> getAllUsers(int page, int size, String sortBy, String direction) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(sortDirection, sortBy));
        return userRepository.findAll(pageable).map(this::mapToUserResponse);
    }

    public Page<UserResponse> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                query, query, query, pageable).map(this::mapToUserResponse);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getBalance(),
                user.getCreatedAt(),
                user.getEmailVerified(),
                user.getTwoFactorEnabled(),
                user.getBanned()
        );
    }
}

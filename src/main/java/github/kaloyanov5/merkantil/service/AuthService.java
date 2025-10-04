package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.repository.UserRepository;
import github.kaloyanov5.merkantil.request.AuthResponse;
import github.kaloyanov5.merkantil.request.LoginRequest;
import github.kaloyanov5.merkantil.request.RegisterRequest;
import github.kaloyanov5.merkantil.request.UserResponse;
import github.kaloyanov5.merkantil.security.CustomUserDetails;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setBalance(10000.0); // Initial balance

        User savedUser = userRepository.save(user);

        UserResponse userResponse = mapToUserResponse(savedUser);
        return new AuthResponse("User registered successfully", userResponse);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        UserResponse userResponse = mapToUserResponse(user);
        return new AuthResponse("User logged in successfully", userResponse);
    }

    public void logout() {
        SecurityContextHolder.clearContext();
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUser();
        }
        throw new IllegalStateException("User not authenticated");
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBalance(),
                user.getCreatedAt()
        );
    }
}

package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.User;
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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSessionService loginSessionService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;

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
        return new AuthResponse("User registered successfully", mapToUserResponse(savedUser));
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

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

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getBalance(), user.getCreatedAt());
    }
}

package github.kaloyanov5.merkantil.controller;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.dto.response.AuthResponse;
import github.kaloyanov5.merkantil.dto.request.ForgotPasswordRequest;
import github.kaloyanov5.merkantil.dto.request.LoginRequest;
import github.kaloyanov5.merkantil.dto.request.RegisterRequest;
import github.kaloyanov5.merkantil.dto.request.ResetPasswordRequest;
import github.kaloyanov5.merkantil.dto.request.TwoFactorVerifyRequest;
import github.kaloyanov5.merkantil.exception.TwoFactorRequiredException;
import github.kaloyanov5.merkantil.dto.response.UserResponse;
import github.kaloyanov5.merkantil.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, logout and retrieving the current authenticated user")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with the provided registration details and returns an authentication response")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input or email already in use")
    })
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Authenticates a user with email and password, creates a session and returns an authentication response")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Validation error in request body"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password")
    })
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse
    ) {
        try {
            AuthResponse response = authService.login(request, httpRequest, httpResponse);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Invalidates the current user session and clears the authentication context")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged out successfully")
    })
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Forgot password", description = "Sends a 6-digit reset code to the user's email address. Always returns 200 to avoid revealing whether the email exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset code sent if email exists")
    })
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(Map.of("message", "If that email is registered you will receive a reset code shortly"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets the user's password using the 6-digit code sent to their email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired reset code")
    })
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verifies the user's email address using the token sent to their inbox")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    })
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        try {
            authService.verifyEmail(token);
            return ResponseEntity.ok(Map.of("message", "Email verified successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the profile information of the currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile returned successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    public ResponseEntity<?> getCurrentUser() {
        try {
            User user = authService.getCurrentUser();
            UserResponse response = new UserResponse(
                    user.getId(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getEmail(),
                    user.getBalance(),
                    user.getCreatedAt(),
                    user.getEmailVerified()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not authenticated"));
        }
    }
}

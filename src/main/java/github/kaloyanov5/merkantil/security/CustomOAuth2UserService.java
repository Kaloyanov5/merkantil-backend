package github.kaloyanov5.merkantil.security;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String email = oauth2User.getAttribute("email");
        String googleId = oauth2User.getAttribute("sub");
        String firstName = oauth2User.getAttribute("given_name");
        String lastName = oauth2User.getAttribute("family_name");

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not provided by Google");
        }

        User user = userRepository.findByEmail(email)
                .map(existing -> {
                    if (existing.getGoogleId() != null) {
                        // Already linked — just return for login.
                        return existing;
                    }
                    // Local account exists but isn't linked yet. Only auto-link
                    // when the local email was verified out-of-band — otherwise
                    // this is a takeover vector: someone could have registered
                    // locally with the victim's email and never verified, and a
                    // Google sign-in by the real owner would attach to (and
                    // grant access to) the imposter's account. See H2.
                    if (!Boolean.TRUE.equals(existing.getEmailVerified())) {
                        log.warn("OAuth login denied for {} — local account exists but email is not verified", email);
                        throw new OAuth2AuthenticationException(
                                "An unverified local account exists for this email. Please log in with your password and verify the email, or contact support to link Google manually.");
                    }
                    existing.setGoogleId(googleId);
                    userRepository.save(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setFirstName(firstName != null ? firstName : "User");
                    newUser.setLastName(lastName != null ? lastName : "");
                    // Random password — account can only be accessed via OAuth
                    newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                    newUser.setBalance(BigDecimal.valueOf(10000));
                    newUser.setEmailVerified(true); // Google already verified the email
                    newUser.setGoogleId(googleId);
                    log.info("Creating new user via Google OAuth: {}", email);
                    return userRepository.save(newUser);
                });

        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new OAuth2AuthenticationException("Account is banned");
        }

        return new CustomOAuth2User(oauth2User, user);
    }
}

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
                    if (existing.getGoogleId() == null) {
                        existing.setGoogleId(googleId);
                        userRepository.save(existing);
                    }
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

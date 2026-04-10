package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.response.LoginSessionResponse;
import github.kaloyanov5.merkantil.entity.LoginSession;
import github.kaloyanov5.merkantil.repository.LoginSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua_parser.Client;
import ua_parser.Parser;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginSessionService {

    private final LoginSessionRepository loginSessionRepository;
    private final SessionRepository<?> sessionRepository;

    private final Parser uaParser = new Parser();

    public void saveSession(Long userId, String sessionId, HttpServletRequest request) {
        if (loginSessionRepository.findBySessionId(sessionId).isPresent()) {
            return;
        }
        LoginSession session = new LoginSession();
        session.setUserId(userId);
        session.setSessionId(sessionId);
        session.setIp(extractIp(request));
        session.setDeviceInfo(parseUserAgent(request.getHeader("User-Agent")));
        loginSessionRepository.save(session);
    }

    @Transactional
    public List<LoginSessionResponse> getActiveSessions(Long userId, String currentSessionId) {
        return loginSessionRepository.findByUserId(userId).stream()
                .filter(ls -> {
                    try {
                        boolean exists = sessionRepository.findById(ls.getSessionId()) != null;
                        if (!exists) {
                            loginSessionRepository.deleteBySessionId(ls.getSessionId());
                        }
                        return exists;
                    } catch (Exception e) {
                        log.warn("Removing stale session {} (store error: {})", ls.getSessionId(), e.getMessage());
                        loginSessionRepository.deleteBySessionId(ls.getSessionId());
                        return false;
                    }
                })
                .map(ls -> new LoginSessionResponse(
                        ls.getSessionId(),
                        ls.getIp(),
                        ls.getDeviceInfo(),
                        ls.getCreatedAt(),
                        ls.getSessionId().equals(currentSessionId)
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, String sessionId) {
        loginSessionRepository.findBySessionId(sessionId).ifPresent(ls -> {
            if (!ls.getUserId().equals(userId)) {
                throw new IllegalArgumentException("Session not found");
            }
            sessionRepository.deleteById(sessionId);
            loginSessionRepository.deleteBySessionId(sessionId);
        });
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        loginSessionRepository.findByUserId(userId)
                .forEach(ls -> sessionRepository.deleteById(ls.getSessionId()));
        loginSessionRepository.deleteByUserId(userId);
    }

    @Transactional
    public void deleteSession(String sessionId) {
        loginSessionRepository.deleteBySessionId(sessionId);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String parseUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        try {
            Client client = uaParser.parse(userAgent);
            String browser = client.userAgent.family;
            String os = client.os.family;
            return browser + " on " + os;
        } catch (Exception e) {
            log.warn("Failed to parse user agent: {}", userAgent);
            return "Unknown device";
        }
    }
}

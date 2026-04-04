package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
    List<LoginSession> findByUserId(Long userId);
    Optional<LoginSession> findBySessionId(String sessionId);
    void deleteBySessionId(String sessionId);
    void deleteByUserId(Long userId);
}

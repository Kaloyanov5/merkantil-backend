package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // For admin features
    List<User> findByCreatedAtBefore(LocalDateTime date);
}

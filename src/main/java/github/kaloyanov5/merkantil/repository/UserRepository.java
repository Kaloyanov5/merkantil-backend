package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    Page<User> findAll(Pageable pageable);

    // Search functionality
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :date")
    Long countUsersCreatedAfter(@Param("date") LocalDateTime date);

    // For admin features
    List<User> findByCreatedAtBefore(LocalDateTime date);
}

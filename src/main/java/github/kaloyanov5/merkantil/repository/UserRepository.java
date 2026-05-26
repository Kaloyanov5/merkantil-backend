package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Read a user with a row-level write lock (SELECT ... FOR UPDATE). Use from
     * money-mutating code paths (orders, deposits, transfers) to serialize
     * concurrent operations on the same user's balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Page<User> findAll(Pageable pageable);

    Page<User> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String firstName, String lastName, String email, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :date")
    Long countUsersCreatedAfter(@Param("date") LocalDateTime date);

    List<User> findByCreatedAtBefore(LocalDateTime date);
}

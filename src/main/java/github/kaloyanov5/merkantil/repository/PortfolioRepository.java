package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.Portfolio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserId(Long userId);
    Optional<Portfolio> findByUserIdAndSymbol(Long userId, String symbol);

    /**
     * Locked variant of {@link #findByUserIdAndSymbol} used by order placement,
     * fill and cancel paths so concurrent operations on the same position
     * cannot both pass quantity / cost-basis checks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :userId AND p.symbol = :symbol")
    Optional<Portfolio> findByUserIdAndSymbolForUpdate(@Param("userId") Long userId, @Param("symbol") String symbol);

    // check if user owns stock
    boolean existsByUserIdAndSymbol(Long userId, String symbol);

    // get all users holding a specific stock
    List<Portfolio> findBySymbol(String symbol);

    // delete holding (when quantity reaches 0)
    void deleteByUserIdAndSymbol(Long userId, String symbol);

    // total user investment
    @Query("SELECT SUM(p.quantity * p.averageBuyPrice) FROM Portfolio p WHERE p.user.id = :user_id")
    Double calculateTotalInvestment(@Param("user_id") Long userId);
}

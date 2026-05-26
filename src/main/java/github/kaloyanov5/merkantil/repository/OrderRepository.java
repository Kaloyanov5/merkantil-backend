package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.Order;
import github.kaloyanov5.merkantil.entity.OrderStatus;
import github.kaloyanov5.merkantil.entity.OrderType;
import github.kaloyanov5.merkantil.entity.Side;
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

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByTimestampDesc(Long userId);

    /**
     * Locked variant of {@code findById} used by cancel and limit-fill paths to
     * serialize order-status transitions and prevent a manual cancel from
     * racing the scheduler's fill (or two scheduler ticks from double-filling
     * the same order).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    // filter by order type
    List<Order> findByUserIdAndType(Long userId, Side type);
    List<Order> findByUserIdAndOrderType(Long userId, OrderType orderType);

    // get orders for specific stock
    Page<Order> findByUserIdAndSymbol(Long userId, String symbol, Pageable pageable);
    List<Order> findBySymbol(String symbol);

    // date range queries
    List<Order> findByUserIdAndTimestampBetween(Long userId, LocalDateTime start, LocalDateTime end);

    // pagination support
    Page<Order> findByUserId(Long userId, Pageable pageable);

    // statistics
    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :user_id")
    Long countByUserId(@Param("user_id") Long userId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :user_id AND o.type = :side")
    Long countByUserIdAndType(@Param("user_id") Long userId, @Param("side") Side side);

    Long user(User user);

    @Query("SELECT o FROM Order o WHERE o.symbol IN :symbols AND o.status = github.kaloyanov5.merkantil.entity.OrderStatus.OPEN AND o.orderType = github.kaloyanov5.merkantil.entity.OrderType.LIMIT")
    List<Order> findOpenLimitOrdersForSymbols(@Param("symbols") List<String> symbols);

    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
}

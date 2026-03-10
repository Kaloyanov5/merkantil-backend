package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.Side;
import github.kaloyanov5.merkantil.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // get user's transaction history
    List<Transaction> findByUserId(Long userId);
    List<Transaction> findByUserIdOrderByTimestampDesc(Long userId);

    // filter by transaction type
    List<Transaction> findByUserIdAndType(Long userId, Side type);

    // get transactions for specific stock
    List<Transaction> findByUserIdAndStockSymbol(Long userId, String stockSymbol);
    List<Transaction> findByStockSymbol(String stockSymbol);

    // get transactions from specific order
    List<Transaction> findByOrderId(Long orderId);

    // date range queries
    List<Transaction> findByUserIdAndTimestampBetween(Long userId, LocalDateTime start, LocalDateTime end);

    // Portfolio reconstruction: get all transactions up to a specific date (inclusive)
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId " +
            "AND DATE(t.timestamp) <= :date " +
            "ORDER BY t.timestamp ASC")
    List<Transaction> findByUserIdAndDateBeforeOrEqual(@Param("userId") Long userId, @Param("date") LocalDate date);

    // Get distinct symbols traded by user up to a specific date
    @Query("SELECT DISTINCT t.stockSymbol FROM Transaction t WHERE t.user.id = :userId " +
            "AND DATE(t.timestamp) <= :date")
    List<String> findDistinctSymbolsByUserIdUpToDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    // pagination
    Page<Transaction> findByUserId(Long userId, Pageable pageable);
    Page<Transaction> findByUserIdAndType(Long userId, Side type, Pageable pageable);

    // analytics queries
    @Query("SELECT SUM(t.totalAmount) FROM Transaction t WHERE t.user.id = :userId AND t.type = :side")
    Double sumTotalAmountByUserIdAndType(@Param("userId") Long userId, @Param("side") Side side);

    @Query("SELECT SUM(t.quantity) FROM Transaction t WHERE t.user.id = :userId AND t.stockSymbol = :symbol AND t.type = :side")
    Integer sumQuantityByUserIdAndSymbolAndType(@Param("userId") Long userId, @Param("symbol") String symbol, @Param("side") Side side);

    // transaction volume
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :userId")
    Long countByUserId(@Param("userId") Long userId);

    /*
    Most traded stocks
    @Query("SELECT t.stockSymbol, COUNT(t) as tradeCount FROM Transaction t WHERE t.user.id = :userId GROUP BY t.stockSymbol ORDER BY tradeCount DESC")
    List<Object[]> findMostTradedStocks(@Param("userId") Long userId, Pageable pageable);
     */
}

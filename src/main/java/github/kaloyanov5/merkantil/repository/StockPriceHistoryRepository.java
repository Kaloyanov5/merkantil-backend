package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.StockPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {
    List<StockPriceHistory> findBySymbolOrderByDateDesc(String symbol);

    List<StockPriceHistory> findBySymbolAndDateBetweenOrderByDateAsc(
            String symbol, LocalDate startDate, LocalDate endDate);

    Optional<StockPriceHistory> findBySymbolAndDate(String symbol, LocalDate date);

    @Query("SELECT sph FROM StockPriceHistory sph WHERE sph.symbol = :symbol " +
            "ORDER BY sph.date DESC LIMIT :limit")
    List<StockPriceHistory> findRecentHistory(@Param("symbol") String symbol,
                                              @Param("limit") int limit);

    // Find most recent close price on or before a specific date (for historical reconstruction)
    @Query("SELECT sph FROM StockPriceHistory sph WHERE sph.symbol = :symbol " +
            "AND sph.date <= :date ORDER BY sph.date DESC LIMIT 1")
    Optional<StockPriceHistory> findMostRecentPriceOnOrBefore(@Param("symbol") String symbol,
                                                               @Param("date") LocalDate date);

    // Check if we have data for a specific date
    boolean existsBySymbolAndDate(String symbol, LocalDate date);
}
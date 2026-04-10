package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findBySymbol(String symbol);

    List<Stock> findBySymbolIn(List<String> symbols);

    Page<Stock> findByIsActiveTrue(Pageable pageable);

    Page<Stock> findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(
            String symbol, String name, Pageable pageable);

    Page<Stock> findBySector(String sector, Pageable pageable);

    long countByIsActiveTrue();

    @Query("SELECT DISTINCT s.sector FROM Stock s WHERE s.sector IS NOT NULL ORDER BY s.sector")
    List<String> findAllSectors();

    @Query("SELECT s FROM Stock s WHERE s.isActive = true ORDER BY s.volume DESC")
    List<Stock> findTopByVolume(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.isActive = true ORDER BY " +
            "((s.currentPrice - s.previousClose) / s.previousClose) DESC")
    List<Stock> findTopGainers(Pageable pageable);

    @Query("SELECT s FROM Stock s WHERE s.isActive = true ORDER BY " +
            "((s.currentPrice - s.previousClose) / s.previousClose) ASC")
    List<Stock> findTopLosers(Pageable pageable);
}
package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<WatchlistItem, Long> {

    List<WatchlistItem> findByUserId(Long userId);

    Optional<WatchlistItem> findByUserIdAndStockSymbol(Long userId, String stockSymbol);

    boolean existsByUserIdAndStockSymbol(Long userId, String stockSymbol);

    void deleteByUserIdAndStockSymbol(Long userId, String stockSymbol);
}

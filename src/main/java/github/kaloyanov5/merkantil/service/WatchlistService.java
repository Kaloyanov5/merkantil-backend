package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.response.StockQuoteResponse;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.entity.WatchlistItem;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.repository.WatchlistRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;

    @Transactional
    public void addToWatchlist(User user, String symbol) {
        String upperSymbol = symbol.toUpperCase();

        if (stockRepository.findBySymbol(upperSymbol).isEmpty()) {
            throw new IllegalArgumentException("Stock not found: " + symbol);
        }
        if (watchlistRepository.existsByUserIdAndStockSymbol(user.getId(), upperSymbol)) {
            throw new IllegalArgumentException(upperSymbol + " is already in your watchlist");
        }

        WatchlistItem item = new WatchlistItem();
        item.setUser(user);
        item.setStockSymbol(upperSymbol);
        watchlistRepository.save(item);
    }

    @Transactional
    public void removeFromWatchlist(User user, String symbol) {
        String upperSymbol = symbol.toUpperCase();
        if (!watchlistRepository.existsByUserIdAndStockSymbol(user.getId(), upperSymbol)) {
            throw new IllegalArgumentException(upperSymbol + " is not in your watchlist");
        }
        watchlistRepository.deleteByUserIdAndStockSymbol(user.getId(), upperSymbol);
    }

    public List<StockQuoteResponse> getWatchlist(User user) {
        List<String> symbols = watchlistRepository.findByUserId(user.getId()).stream()
                .map(WatchlistItem::getStockSymbol)
                .collect(Collectors.toList());

        if (symbols.isEmpty()) {
            return List.of();
        }

        return stockService.getMultipleQuotes(symbols);
    }
}

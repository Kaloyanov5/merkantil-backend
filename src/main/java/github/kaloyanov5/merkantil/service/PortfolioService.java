package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.alpaca.AlpacaSnapshot;
import github.kaloyanov5.merkantil.dto.response.PortfolioResponse;
import github.kaloyanov5.merkantil.entity.Portfolio;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.PortfolioRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final AlpacaApiService alpacaApiService;

    /**
     * Get user's portfolio with current values
     */
    public List<PortfolioResponse> getUserPortfolio(Long userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        return portfolios.stream()
                .map(this::mapToPortfolioResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get portfolio summary (total value, gains, etc.)
     */
    public PortfolioSummary getPortfolioSummary(Long userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        double totalValue = 0.0;
        double totalCost = 0.0;

        for (Portfolio portfolio : portfolios) {
            Double currentPrice = getCurrentPrice(portfolio.getSymbol());
            double positionValue = portfolio.getQuantity() * currentPrice;
            double positionCost = portfolio.getQuantity() * portfolio.getAverageBuyPrice();

            totalValue += positionValue;
            totalCost += positionCost;
        }

        double totalGain = totalValue - totalCost;
        double totalGainPercent = totalCost > 0 ? (totalGain / totalCost) * 100 : 0.0;

        return new PortfolioSummary(
                portfolios.size(),
                totalValue,
                totalCost,
                totalGain,
                totalGainPercent
        );
    }

    /**
     * Get portfolio position for specific stock
     */
    public PortfolioResponse getPosition(Long userId, String symbol) {
        Portfolio portfolio = portfolioRepository.findByUserIdAndSymbol(userId, symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("No position found for " + symbol));

        return mapToPortfolioResponse(portfolio);
    }

    /**
     * Get current price for a stock
     */
    private Double getCurrentPrice(String symbol) {
        // First try to get from database (updated by scheduler)
        Stock stock = stockRepository.findBySymbol(symbol).orElse(null);
        if (stock != null && stock.getCurrentPrice() != null) {
            return stock.getCurrentPrice();
        }

        // Fallback to Alpaca API
        AlpacaSnapshot snapshot = alpacaApiService.getSnapshot(symbol);
        if (snapshot != null && snapshot.getLatestTrade() != null) {
            return snapshot.getLatestTrade().getPrice();
        }

        // Last resort: use average buy price (not accurate but prevents errors)
        log.warn("Unable to fetch current price for {}, using fallback", symbol);
        return 0.0;
    }

    /**
     * Map Portfolio entity to response DTO
     */
    private PortfolioResponse mapToPortfolioResponse(Portfolio portfolio) {
        String stockName = stockRepository.findBySymbol(portfolio.getSymbol())
                .map(Stock::getName)
                .orElse(null);

        Double currentPrice = getCurrentPrice(portfolio.getSymbol());
        Double currentValue = portfolio.getQuantity() * currentPrice;
        Double totalCost = portfolio.getQuantity() * portfolio.getAverageBuyPrice();
        Double unrealizedGain = currentValue - totalCost;
        Double unrealizedGainPercent = totalCost > 0 ? (unrealizedGain / totalCost) * 100 : 0.0;

        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getSymbol(),
                stockName,
                portfolio.getQuantity(),
                portfolio.getAverageBuyPrice(),
                currentPrice,
                currentValue,
                totalCost,
                unrealizedGain,
                unrealizedGainPercent
        );
    }

    // Inner record class for portfolio summary
    public record PortfolioSummary(
            int totalPositions,
            double totalValue,
            double totalCost,
            double totalGain,
            double totalGainPercent
    ) {
    }
}
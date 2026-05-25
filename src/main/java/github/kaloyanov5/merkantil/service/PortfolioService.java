package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveSnapshotTicker;
import github.kaloyanov5.merkantil.dto.response.PortfolioResponse;
import github.kaloyanov5.merkantil.entity.Portfolio;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.PortfolioRepository;
import github.kaloyanov5.merkantil.repository.StockRepository;
import github.kaloyanov5.merkantil.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final StockRepository stockRepository;
    private final MassiveApiService massiveApiService;

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

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            BigDecimal currentPrice = getCurrentPrice(portfolio.getSymbol());
            BigDecimal positionValue = currentPrice != null
                    ? MoneyUtil.multiply(currentPrice, portfolio.getQuantity()) : BigDecimal.ZERO;
            BigDecimal positionCost = MoneyUtil.multiply(portfolio.getAverageBuyPrice(), portfolio.getQuantity());

            totalValue = totalValue.add(positionValue);
            totalCost = totalCost.add(positionCost);
        }

        BigDecimal totalGain = totalValue.subtract(totalCost);
        double totalGainPercent = MoneyUtil.isPositive(totalCost)
                ? totalGain.divide(totalCost, 6, MoneyUtil.ROUNDING)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

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
    private BigDecimal getCurrentPrice(String symbol) {
        // First try to get from database (updated by scheduler)
        Stock stock = stockRepository.findBySymbol(symbol).orElse(null);
        if (stock != null && stock.getCurrentPrice() != null) {
            return stock.getCurrentPrice();
        }

        // Fallback to Massive API
        MassiveSnapshotTicker snapshot = massiveApiService.getSnapshot(symbol);
        if (snapshot != null && snapshot.lastTrade() != null) {
            return MoneyUtil.of(snapshot.lastTrade().price());
        }

        log.warn("Unable to fetch current price for {}", symbol);
        return null;
    }

    /**
     * Map Portfolio entity to response DTO
     */
    private PortfolioResponse mapToPortfolioResponse(Portfolio portfolio) {
        String stockName = stockRepository.findBySymbol(portfolio.getSymbol())
                .map(Stock::getName)
                .orElse(null);

        BigDecimal currentPrice = getCurrentPrice(portfolio.getSymbol());
        BigDecimal totalCost = MoneyUtil.multiply(portfolio.getAverageBuyPrice(), portfolio.getQuantity());
        BigDecimal currentValue = currentPrice != null
                ? MoneyUtil.multiply(currentPrice, portfolio.getQuantity()) : null;
        BigDecimal unrealizedGain = currentValue != null ? currentValue.subtract(totalCost) : null;
        Double unrealizedGainPercent = (unrealizedGain != null && MoneyUtil.isPositive(totalCost))
                ? unrealizedGain.divide(totalCost, 6, MoneyUtil.ROUNDING)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : null;

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
            BigDecimal totalValue,
            BigDecimal totalCost,
            BigDecimal totalGain,
            double totalGainPercent
    ) {
    }
}

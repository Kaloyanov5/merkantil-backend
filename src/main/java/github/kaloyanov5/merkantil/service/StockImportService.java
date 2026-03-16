package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.MassiveTickerDetail;
import github.kaloyanov5.merkantil.entity.Stock;
import github.kaloyanov5.merkantil.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockImportService {

    private final MassiveApiService massiveApiService;
    private final StockRepository stockRepository;

    /**
     * Import all tradable stocks from Massive
     * This will fetch all active US equities and save them to database
     */
    public ImportResult importStocksFromMassive() {
        log.info("Starting stock import from Massive...");

        List<MassiveTickerDetail> assets = massiveApiService.getAssets();

        if (assets == null || assets.isEmpty()) {
            log.error("No assets returned from Massive API");
            return new ImportResult(0, 0, 0, "Failed to fetch assets from Massive");
        }

        int totalAssets = assets.size();
        int imported = 0;
        int updated = 0;
        int skipped = 0;

        for (MassiveTickerDetail asset : assets) {
            try {
                // Only import active stocks with valid data
                if (asset.getActive() != null && asset.getActive()
                        && asset.getTicker() != null && !asset.getTicker().isBlank()
                        && asset.getName() != null && !asset.getName().isBlank()) {
                    boolean isNew = importStock(asset);
                    if (isNew) {
                        imported++;
                    } else {
                        updated++;
                    }
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Error importing stock {}: {}", asset.getTicker(), e.getMessage());
                skipped++;
            }
        }

        String message = String.format(
                "Import completed: %d total assets, %d imported, %d updated, %d skipped",
                totalAssets, imported, updated, skipped
        );

        log.info(message);
        return new ImportResult(totalAssets, imported, updated, message);
    }

    /**
     * Import top N most popular stocks
     */
    public ImportResult importTopStocks(int limit) {
        log.info("Importing top {} stocks from predefined list...", limit);

        // Predefined list of popular stocks
        String[] popularStocks = {
                "AAPL",  // Apple
                "MSFT",  // Microsoft
                "GOOGL", // Google
                "AMZN",  // Amazon
                "TSLA",  // Tesla
                "NVDA",  // NVIDIA
                "META",  // Meta (Facebook)
                "INTC"   // Intel
        };

        int count = 0;
        int imported = 0;
        int failed = 0;

        for (String symbol : popularStocks) {
            if (count >= limit) break;

            try {
                MassiveTickerDetail asset = massiveApiService.getAsset(symbol);
                if (asset != null && Boolean.TRUE.equals(asset.getActive())) {
                    importStock(asset);
                    imported++;
                    count++;
                } else {
                    log.warn("Stock {} not active or not found", symbol);
                    failed++;
                }
            } catch (Exception e) {
                log.error("Error importing stock {}: {}", symbol, e.getMessage());
                failed++;
            }
        }

        String message = String.format("Imported %d stocks, %d failed", imported, failed);
        log.info(message);
        return new ImportResult(popularStocks.length, imported, 0, message);
    }

    /**
     * Import a single stock by symbol
     */
    public boolean importSingleStock(String symbol) {
        log.info("Importing stock: {}", symbol);

        MassiveTickerDetail asset = massiveApiService.getAsset(symbol.toUpperCase());
        if (asset == null) {
            log.error("Stock not found: {}", symbol);
            return false;
        }

        if (asset.getActive() == null || !asset.getActive()) {
            log.error("Stock {} is not active", symbol);
            return false;
        }

        importStock(asset);
        log.info("Successfully imported stock: {}", symbol);
        return true;
    }

    /**
     * Import or update a stock from Massive ticker detail
     */
    @Transactional
    public boolean importStock(MassiveTickerDetail asset) {
        if (asset.getTicker() == null || asset.getTicker().isBlank()
                || asset.getName() == null || asset.getName().isBlank()) {
            log.debug("Skipping stock with missing ticker or name: {}", asset.getTicker());
            return false;
        }

        Stock stock = stockRepository.findBySymbol(asset.getTicker())
                .orElse(new Stock());

        boolean isNew = stock.getId() == null;

        stock.setSymbol(asset.getTicker());
        stock.setName(asset.getName());
        stock.setExchange(MassiveApiService.mapExchangeCode(asset.getPrimaryExchange()));
        stock.setCurrency(asset.getCurrencyName() != null ? asset.getCurrencyName().toUpperCase() : "USD");

        // Use SIC description as sector if available
        if (stock.getSector() == null) {
            if (asset.getSicDescription() != null) {
                stock.setSector(asset.getSicDescription());
            } else {
                stock.setSector(guessSector(asset.getTicker()));
            }
        }

        if (asset.getMarketCap() != null) {
            stock.setMarketCap(asset.getMarketCap());
        }

        stock.setIsActive(true);
        stock.setLastUpdated(LocalDateTime.now());

        stockRepository.save(stock);
        return isNew;
    }

    /**
     * Basic sector guessing (will change it!!!!!)
     */
    private String guessSector(String symbol) {
        // basic guess
        if (symbol.matches("(AAPL|MSFT|GOOGL|META|NVDA|INTC|AMD|ORCL|CSCO|ADBE|CRM)")) {
            return "Technology";
        } else if (symbol.matches("(JPM|BAC|V|MA|GS|MS)")) {
            return "Financial Services";
        } else if (symbol.matches("(JNJ|UNH|PFE|TMO|ABT)")) {
            return "Healthcare";
        } else if (symbol.matches("(AMZN|WMT|COST|HD|NKE)")) {
            return "Consumer Cyclical";
        } else if (symbol.matches("(KO|PEP|PG)")) {
            return "Consumer Defensive";
        } else if (symbol.matches("(TSLA)")) {
            return "Automotive";
        }
        return "Unknown";
    }

    /**
     * Result class for import operations
     */
    public record ImportResult(
            int totalProcessed,
            int imported,
            int updated,
            String message
    ) {
    }
}

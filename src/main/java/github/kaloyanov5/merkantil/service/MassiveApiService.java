package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.massive.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MassiveApiService {

    private final WebClient client;

    @Value("${massive.api.key}")
    private String apiKey;

    public MassiveApiService(
            @Value("${massive.api.base-url}") String baseUrl,
            @Value("${massive.api.timeout}") int timeout
    ) {
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB for large responses
                .build();
    }

    // ========================
    // SNAPSHOT ENDPOINTS
    // ========================

    /**
     * Get real-time snapshot for a single stock.
     * GET /v2/snapshot/locale/us/markets/stocks/tickers/{stocksTicker}
     */
    public MassiveSnapshotTicker getSnapshot(String symbol) {
        try {
            log.info("Fetching snapshot for: {}", symbol);

            MassiveSnapshotResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/snapshot/locale/us/markets/stocks/tickers/{ticker}")
                            .queryParam("apiKey", apiKey)
                            .build(symbol.toUpperCase()))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Massive.com API error for snapshot {}: HTTP {}", symbol, clientResponse.statusCode());
                                return clientResponse.bodyToMono(String.class)
                                        .map(body -> new RuntimeException("API error: " + body));
                            })
                    .bodyToMono(MassiveSnapshotResponse.class)
                    .block();

            if (response == null) {
                log.warn("Null response for snapshot {}", symbol);
                return null;
            }

            if (response.getTicker() == null) {
                log.warn("No snapshot ticker data for {} (status: {})", symbol, response.getStatus());
                return null;
            }

            log.debug("Snapshot for {}: lastTrade={}, day={}, prevDay={}",
                    symbol,
                    response.getTicker().getLastTrade() != null ? response.getTicker().getLastTrade().getPrice() : "null",
                    response.getTicker().getDay() != null ? response.getTicker().getDay().getClose() : "null",
                    response.getTicker().getPrevDay() != null ? response.getTicker().getPrevDay().getClose() : "null");

            return response.getTicker();
        } catch (Exception e) {
            log.error("Error fetching snapshot for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Get multiple snapshots at once.
     * GET /v2/snapshot/locale/us/markets/stocks/tickers?tickers=AAPL,MSFT,...
     */
    public Map<String, MassiveSnapshotTicker> getMultipleSnapshots(List<String> symbols) {
        try {
            String tickersParam = symbols.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.joining(","));
            log.info("Fetching snapshots for: {}", tickersParam);

            MassiveMultiSnapshotResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/snapshot/locale/us/markets/stocks/tickers")
                            .queryParam("tickers", tickersParam)
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(MassiveMultiSnapshotResponse.class)
                    .block();

            if (response == null || response.getTickers() == null) {
                log.warn("No multi-snapshot data returned");
                return Map.of();
            }

            return response.getTickers().stream()
                    .filter(t -> t.getTicker() != null)
                    .collect(Collectors.toMap(
                            MassiveSnapshotTicker::getTicker,
                            t -> t,
                            (a, b) -> a // in case of duplicates, keep first
                    ));
        } catch (Exception e) {
            log.error("Error fetching multiple snapshots: {}", e.getMessage());
            return Map.of();
        }
    }

    // ========================
    // HISTORICAL BARS
    // ========================

    /**
     * Get historical bars (OHLCV data).
     * GET /v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}
     */
    public List<MassiveBar> getHistoricalBars(String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Fetching historical bars for {} from {} to {}", symbol, startDate, endDate);

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

            MassiveAggregatesResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/aggs/ticker/{ticker}/range/1/day/{from}/{to}")
                            .queryParam("adjusted", true)
                            .queryParam("sort", "asc")
                            .queryParam("limit", 50000)
                            .queryParam("apiKey", apiKey)
                            .build(symbol.toUpperCase(),
                                    startDate.format(formatter),
                                    endDate.format(formatter)))
                    .retrieve()
                    .bodyToMono(MassiveAggregatesResponse.class)
                    .block();

            if (response == null || response.getResults() == null) {
                log.warn("No bars response for {}", symbol);
                return new ArrayList<>();
            }

            List<MassiveBar> bars = response.getResults();

            if (bars.isEmpty()) {
                log.warn("No bars data for {} in response", symbol);
                return new ArrayList<>();
            }

            log.info("Fetched {} bars for {}", bars.size(), symbol);
            return new ArrayList<>(bars);

        } catch (Exception e) {
            log.error("Error fetching historical bars for {}: {}", symbol, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    // ========================
    // LAST TRADE / QUOTE
    // ========================

    /**
     * Get latest trade.
     * GET /v2/last/trade/{ticker}
     */
    public MassiveLastTrade getLatestTrade(String symbol) {
        try {
            MassiveLastTradeResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/last/trade/{ticker}")
                            .queryParam("apiKey", apiKey)
                            .build(symbol.toUpperCase()))
                    .retrieve()
                    .bodyToMono(MassiveLastTradeResponse.class)
                    .block();

            return response != null ? response.getResults() : null;
        } catch (Exception e) {
            log.error("Error fetching latest trade for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Get latest quote (NBBO).
     * GET /v2/last/nbbo/{ticker}
     */
    public MassiveLastQuote getLatestQuote(String symbol) {
        try {
            MassiveLastQuoteResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/last/nbbo/{ticker}")
                            .queryParam("apiKey", apiKey)
                            .build(symbol.toUpperCase()))
                    .retrieve()
                    .bodyToMono(MassiveLastQuoteResponse.class)
                    .block();

            return response != null ? response.getResults() : null;
        } catch (Exception e) {
            log.error("Error fetching latest quote for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    // ========================
    // TICKER / ASSET REFERENCE
    // ========================

    /**
     * Get list of active stock tickers.
     * GET /v3/reference/tickers?market=stocks&active=true&limit=1000
     */
    public List<MassiveTickerDetail> getAssets() {
        try {
            log.info("Fetching tradable assets from Massive.com");

            List<MassiveTickerDetail> allTickers = new ArrayList<>();
            String nextUrl = null;
            boolean firstRequest = true;

            // Paginate through results
            while (firstRequest || nextUrl != null) {
                firstRequest = false;
                MassiveTickerListResponse response;

                if (nextUrl != null) {
                    // next_url already contains apiKey, fetch directly
                    final String url = nextUrl;
                    response = client.get()
                            .uri(url + "&apiKey=" + apiKey)
                            .retrieve()
                            .bodyToMono(MassiveTickerListResponse.class)
                            .block();
                } else {
                    response = client.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/v3/reference/tickers")
                                    .queryParam("market", "stocks")
                                    .queryParam("active", true)
                                    .queryParam("limit", 1000)
                                    .queryParam("apiKey", apiKey)
                                    .build())
                            .retrieve()
                            .bodyToMono(MassiveTickerListResponse.class)
                            .block();
                }

                if (response != null && response.getResults() != null) {
                    allTickers.addAll(response.getResults());
                    nextUrl = response.getNextUrl();
                } else {
                    break;
                }

                // Safety limit to prevent infinite loops
                if (allTickers.size() > 15000) {
                    log.warn("Reached safety limit of 15000 tickers, stopping pagination");
                    break;
                }
            }

            log.info("Fetched {} total tickers from Massive.com", allTickers.size());
            return allTickers;
        } catch (Exception e) {
            log.error("Error fetching assets: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get specific ticker details.
     * GET /v3/reference/tickers/{ticker}
     */
    public MassiveTickerDetail getAsset(String symbol) {
        try {
            MassiveTickerResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/reference/tickers/{ticker}")
                            .queryParam("apiKey", apiKey)
                            .build(symbol.toUpperCase()))
                    .retrieve()
                    .bodyToMono(MassiveTickerResponse.class)
                    .block();

            return response != null ? response.getResults() : null;
        } catch (Exception e) {
            log.error("Error fetching asset {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    // ========================
    // MARKET STATUS
    // ========================

    /**
     * Check if US stock market is currently open.
     * GET /v1/marketstatus/now
     */
    public boolean isMarketOpen() {
        try {
            MassiveMarketStatusResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/marketstatus/now")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(MassiveMarketStatusResponse.class)
                    .block();

            if (response == null) {
                return false;
            }

            // Check if either NYSE or NASDAQ is open
            if (response.getExchanges() != null) {
                String nyse = response.getExchanges().get("nyse");
                String nasdaq = response.getExchanges().get("nasdaq");
                return "open".equalsIgnoreCase(nyse) || "open".equalsIgnoreCase(nasdaq);
            }

            return "open".equalsIgnoreCase(response.getMarket());
        } catch (Exception e) {
            log.error("Error checking market status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get detailed market status: OPEN, PRE_MARKET, AFTER_HOURS, or CLOSED.
     */
    public Map<String, String> getDetailedMarketStatus() {
        try {
            MassiveMarketStatusResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/marketstatus/now")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(MassiveMarketStatusResponse.class)
                    .block();

            if (response == null) {
                return Map.of("status", "CLOSED");
            }

            String status;

            // Check if main exchanges are open
            boolean exchangesOpen = false;
            if (response.getExchanges() != null) {
                String nyse = response.getExchanges().get("nyse");
                String nasdaq = response.getExchanges().get("nasdaq");
                exchangesOpen = "open".equalsIgnoreCase(nyse) || "open".equalsIgnoreCase(nasdaq);
            }

            if (exchangesOpen || "open".equalsIgnoreCase(response.getMarket())) {
                status = "OPEN";
            } else if (response.getEarlyHours() != null && response.getEarlyHours()) {
                status = "PRE_MARKET";
            } else if (response.getAfterHours() != null && response.getAfterHours()) {
                status = "AFTER_HOURS";
            } else {
                status = "CLOSED";
            }

            return Map.of(
                    "status", status,
                    "serverTime", response.getServerTime() != null ? response.getServerTime() : ""
            );
        } catch (Exception e) {
            log.error("Error checking detailed market status: {}", e.getMessage());
            return Map.of("status", "CLOSED");
        }
    }

    // ========================
    // PREVIOUS DAY BAR
    // ========================

    /**
     * Get previous trading day's bar.
     * GET /v2/aggs/ticker/{ticker}/prev
     */
    public MassiveBar getPreviousDayBar(String symbol) {
        try {
            MassiveAggregatesResponse response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/aggs/ticker/{ticker}/prev")
                            .queryParam("adjusted", true)
                            .queryParam("apiKey", apiKey)
                            .build(symbol.toUpperCase()))
                    .retrieve()
                    .bodyToMono(MassiveAggregatesResponse.class)
                    .block();

            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                return response.getResults().getFirst();
            }
            return null;
        } catch (Exception e) {
            log.error("Error fetching previous day bar for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    // ========================
    // MARKET CALENDAR
    // ========================

    /**
     * Fetch upcoming market holidays.
     * GET /v1/marketstatus/upcoming
     */
    public List<MassiveMarketHoliday> getUpcomingHolidays() {
        try {
            List<MassiveMarketHoliday> response = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/marketstatus/upcoming")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToFlux(MassiveMarketHoliday.class)
                    .collectList()
                    .block();

            return response != null ? response : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error fetching upcoming market holidays: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========================
    // UTILITY
    // ========================

    /**
     * Convert Unix milliseconds timestamp to LocalDate.
     */
    public static LocalDate millisToLocalDate(Long millis) {
        if (millis == null) return null;
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.of("America/New_York"))
                .toLocalDate();
    }

    /**
     * Map Massive.com MIC exchange codes to readable names.
     */
    public static String mapExchangeCode(String micCode) {
        if (micCode == null) return "Unknown";
        return switch (micCode) {
            case "XNAS" -> "NASDAQ";
            case "XNYS" -> "NYSE";
            case "XASE" -> "NYSE American";
            case "ARCX" -> "NYSE Arca";
            case "BATS" -> "CBOE BZX";
            case "XCHI" -> "Chicago Stock Exchange";
            default -> micCode;
        };
    }
}



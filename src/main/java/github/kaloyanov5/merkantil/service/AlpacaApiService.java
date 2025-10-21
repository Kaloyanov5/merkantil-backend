package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.alpaca.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AlpacaApiService {

    private final WebClient tradingClient;
    private final WebClient dataClient;

    @Value("${alpaca.api.key-id}")
    private String apiKeyId;

    @Value("${alpaca.api.secret-key}")
    private String secretKey;

    public AlpacaApiService(
            @Value("${alpaca.api.base-url}") String baseUrl,
            @Value("${alpaca.api.data-url}") String dataUrl,
            @Value("${alpaca.api.timeout}") int timeout
    ) {
        this.tradingClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.dataClient = WebClient.builder()
                .baseUrl(dataUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Get real-time snapshot for a stock
     */
    @Cacheable(value = "alpacaSnapshots", key = "#symbol", unless = "#result == null")
    public AlpacaSnapshot getSnapshot(String symbol) {
        try {
            log.info("Fetching snapshot for: {}", symbol);

            return dataClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/{symbol}/snapshot")
                            .build(symbol.toUpperCase()))
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(AlpacaSnapshot.class)
                    .block();
        } catch (Exception e) {
            log.error("Error fetching snapshot for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Get multiple snapshots at once
     */
    public Map<String, AlpacaSnapshot> getMultipleSnapshots(List<String> symbols) {
        try {
            String symbolsParam = String.join(",", symbols);
            log.info("Fetching snapshots for: {}", symbolsParam);

            return dataClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/snapshots")
                            .queryParam("symbols", symbolsParam)
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, AlpacaSnapshot>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Error fetching multiple snapshots: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Get historical bars (OHLCV data)
     */
    @Cacheable(value = "alpacaBars", key = "#symbol + '-' + #startDate + '-' + #endDate")
    public Map<String, List<AlpacaBar>> getHistoricalBars(String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Fetching historical bars for {} from {} to {}", symbol, startDate, endDate);

            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

            return dataClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/{symbol}/bars")
                            .queryParam("start", startDate.format(formatter))
                            .queryParam("end", endDate.format(formatter))
                            .queryParam("timeframe", "1Day")
                            .queryParam("limit", 10000)
                            .build(symbol.toUpperCase()))
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, List<AlpacaBar>>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Error fetching historical bars for {}: {}", symbol, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Get latest trade
     */
    public AlpacaTrade getLatestTrade(String symbol) {
        try {
            Map<String, AlpacaTrade> result = dataClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/{symbol}/trades/latest")
                            .build(symbol.toUpperCase()))
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, AlpacaTrade>>() {})
                    .block();

            return result != null ? result.get("trade") : null;
        } catch (Exception e) {
            log.error("Error fetching latest trade for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Get latest quote
     */
    public AlpacaQuote getLatestQuote(String symbol) {
        try {
            Map<String, AlpacaQuote> result = dataClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/stocks/{symbol}/quotes/latest")
                            .build(symbol.toUpperCase()))
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, AlpacaQuote>>() {})
                    .block();

            return result != null ? result.get("quote") : null;
        } catch (Exception e) {
            log.error("Error fetching latest quote for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Get list of tradable assets
     */
    public List<AlpacaAsset> getAssets() {
        try {
            log.info("Fetching tradable assets");

            return tradingClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/assets")
                            .queryParam("status", "active")
                            .queryParam("asset_class", "us_equity")
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToFlux(AlpacaAsset.class)
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.error("Error fetching assets: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get specific asset
     */
    public AlpacaAsset getAsset(String symbol) {
        try {
            return tradingClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/assets/{symbol}")
                            .build(symbol.toUpperCase()))
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(AlpacaAsset.class)
                    .block();
        } catch (Exception e) {
            log.error("Error fetching asset {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Check if market is open
     */
    public boolean isMarketOpen() {
        try {
            Map<String, Object> clock = tradingClient.get()
                    .uri("/v2/clock")
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            return clock != null && Boolean.TRUE.equals(clock.get("is_open"));
        } catch (Exception e) {
            log.error("Error checking market status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get market calendar
     */
    public List<Map<String, Object>> getMarketCalendar(LocalDate start, LocalDate end) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;

            return tradingClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/calendar")
                            .queryParam("start", start.format(formatter))
                            .queryParam("end", end.format(formatter))
                            .build())
                    .header("APCA-API-KEY-ID", apiKeyId)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .collectList()
                    .block();
        } catch (Exception e) {
            log.error("Error fetching market calendar: {}", e.getMessage());
            return List.of();
        }
    }
}
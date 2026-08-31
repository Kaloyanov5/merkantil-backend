package github.kaloyanov5.merkantil.marketdata.error;

import github.kaloyanov5.merkantil.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum MarketDataError implements ErrorCode {
    STOCK_NOT_FOUND("MKTD-5001", "Stock not found", HttpStatus.NOT_FOUND),
    QUOTE_UNAVAILABLE("MKTD-5002", "Unable to fetch a quote for this symbol", HttpStatus.NOT_FOUND),
    WATCHLIST_DUPLICATE("MKTD-5003", "This stock is already in your watchlist", HttpStatus.CONFLICT),
    WATCHLIST_ENTRY_NOT_FOUND("MKTD-5004", "This stock is not in your watchlist", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

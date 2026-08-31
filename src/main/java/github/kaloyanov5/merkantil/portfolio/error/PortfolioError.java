package github.kaloyanov5.merkantil.portfolio.error;

import github.kaloyanov5.merkantil.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum PortfolioError implements ErrorCode {
    POSITION_NOT_FOUND("PORT-4001", "No position found for this symbol", HttpStatus.NOT_FOUND);

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

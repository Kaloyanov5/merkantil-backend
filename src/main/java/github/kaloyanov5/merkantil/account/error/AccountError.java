package github.kaloyanov5.merkantil.account.error;

import github.kaloyanov5.merkantil.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum AccountError implements ErrorCode {
    INVALID_AMOUNT("ACCT-2001", "The transaction amount provided is invalid", HttpStatus.BAD_REQUEST),
    PAYMENT_METHOD_NOT_FOUND("ACCT-2002", "The requested payment method was not found", HttpStatus.NOT_FOUND),
    CARD_EXPIRED("ACCT-2003", "Card has expired", HttpStatus.UNPROCESSABLE_ENTITY),
    INSUFFICIENT_FUNDS("ACCT-2004", "Insufficient funds to complete the transaction", HttpStatus.UNPROCESSABLE_ENTITY),
    CANNOT_TRANSFER_TO_SELF("ACCT-2005", "Cannot transfer funds to yourself", HttpStatus.BAD_REQUEST),
    MAX_PAYMENT_METHODS_REACHED("ACCT-2006", "Maximum payment methods reached. Remove one before adding another", HttpStatus.CONFLICT);

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

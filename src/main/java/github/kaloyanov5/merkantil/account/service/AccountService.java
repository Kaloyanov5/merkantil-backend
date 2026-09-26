package github.kaloyanov5.merkantil.account.service;

import github.kaloyanov5.merkantil.account.error.AccountError;
import github.kaloyanov5.merkantil.common.error.AppException;
import github.kaloyanov5.merkantil.common.error.CommonError;
import github.kaloyanov5.merkantil.account.controller.dto.request.TransferRequest;
import github.kaloyanov5.merkantil.account.controller.dto.response.BalanceResponse;
import github.kaloyanov5.merkantil.account.controller.dto.response.WalletTransactionResponse;
import github.kaloyanov5.merkantil.account.model.PaymentMethod;
import github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimited;
import github.kaloyanov5.merkantil.identity.model.User;
import github.kaloyanov5.merkantil.account.model.WalletTransaction;
import github.kaloyanov5.merkantil.account.model.enums.WalletTransactionType;
import github.kaloyanov5.merkantil.account.repository.PaymentMethodRepository;
import github.kaloyanov5.merkantil.identity.repository.UserRepository;
import github.kaloyanov5.merkantil.account.repository.WalletTransactionRepository;
import github.kaloyanov5.merkantil.notification.service.EmailService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final EmailService emailService;

    /** Throttle peer-to-peer transfers per sender. */
    private static final int MAX_TRANSFERS_PER_WINDOW = 10;
    private static final long TRANSFER_RATE_WINDOW_MINUTES = 1;
    private static final BigDecimal MAX_DEPOSIT = BigDecimal.valueOf(25_000);
    private static final BigDecimal MAX_WITHDRAWAL = BigDecimal.valueOf(10_000);

    public BalanceResponse getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND));
        return new BalanceResponse(user.getId(), user.getBalance());
    }

    @Transactional
    public BalanceResponse deposit(Long userId, BigDecimal amount, Long paymentMethodId) {
        // Endpoint is ROLE_ADMIN gated at the controller; no per-user self-match
        // check here because admins credit any user's account.
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(AccountError.INVALID_AMOUNT, "Deposit amount must be positive");
        }
        if (amount.compareTo(MAX_DEPOSIT) > 0) {
            throw new AppException(AccountError.INVALID_AMOUNT, "Deposit amount cannot exceed $25,000 per transaction");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new AppException(CommonError.ACCOUNT_SUSPENDED);
        }

        PaymentMethod paymentMethod = null;
        if (paymentMethodId != null) {
            paymentMethod = paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(paymentMethodId, userId)
                    .orElseThrow(() -> new AppException(AccountError.PAYMENT_METHOD_NOT_FOUND));

            YearMonth expiry = YearMonth.of(paymentMethod.getExpiryYear(), paymentMethod.getExpiryMonth());
            if (expiry.isBefore(YearMonth.now())) {
                throw new AppException(AccountError.CARD_EXPIRED, "Card ending in " + paymentMethod.getLast4() + " has expired");
            }
        }

        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setType(WalletTransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setPaymentMethod(paymentMethod);
        walletTransactionRepository.save(tx);

        return new BalanceResponse(user.getId(), user.getBalance());
    }

    @Transactional
    public BalanceResponse withdraw(Long userId, BigDecimal amount) {
        // Endpoint is ROLE_ADMIN gated at the controller; admins withdraw on
        // behalf of any user, so no self-match check here.
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(AccountError.INVALID_AMOUNT, "Withdrawal amount must be positive");
        }
        if (amount.compareTo(MAX_WITHDRAWAL) > 0) {
            throw new AppException(AccountError.INVALID_AMOUNT, "Withdrawal amount cannot exceed $10,000 per transaction");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(user.getBanned())) {
            throw new AppException(CommonError.ACCOUNT_SUSPENDED);
        }

        if (user.getBalance().compareTo(amount) < 0) {
            throw new AppException(AccountError.INSUFFICIENT_FUNDS);
        }

        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setType(WalletTransactionType.WITHDRAWAL);
        tx.setAmount(amount);
        walletTransactionRepository.save(tx);

        return new BalanceResponse(user.getId(), user.getBalance());
    }

    @Transactional
    @RateLimited(bucket = "transfer", key = "#senderId", limit = MAX_TRANSFERS_PER_WINDOW, duration = TRANSFER_RATE_WINDOW_MINUTES)
    public BalanceResponse transfer(Long senderId, TransferRequest request) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(AccountError.INVALID_AMOUNT, "Transfer amount must be positive");
        }

        // Resolve recipient (unlocked) just to obtain the id for the lock-ordered fetch
        User recipientLookup = userRepository.findByEmail(request.recipientEmail())
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND, "Recipient not found"));

        if (senderId.equals(recipientLookup.getId())) {
            throw new AppException(AccountError.CANNOT_TRANSFER_TO_SELF);
        }

        // Acquire pessimistic locks in id order to avoid A->B / B->A deadlocks
        Long firstId = Math.min(senderId, recipientLookup.getId());
        Long secondId = Math.max(senderId, recipientLookup.getId());
        User first = userRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND));
        User second = userRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new AppException(CommonError.USER_NOT_FOUND));
        User sender = firstId.equals(senderId) ? first : second;
        User recipient = firstId.equals(senderId) ? second : first;

        if (Boolean.TRUE.equals(sender.getBanned())) {
            throw new AppException(CommonError.ACCOUNT_SUSPENDED);
        }
        if (Boolean.TRUE.equals(recipient.getBanned())) {
            throw new AppException(CommonError.ACCOUNT_SUSPENDED, "Recipient account is suspended");
        }

        if (sender.getBalance().compareTo(request.amount()) < 0) {
            throw new AppException(AccountError.INSUFFICIENT_FUNDS);
        }

        sender.setBalance(sender.getBalance().subtract(request.amount()));
        recipient.setBalance(recipient.getBalance().add(request.amount()));
        userRepository.save(sender);
        userRepository.save(recipient);

        WalletTransaction outTx = new WalletTransaction();
        outTx.setUser(sender);
        outTx.setType(WalletTransactionType.TRANSFER_OUT);
        outTx.setAmount(request.amount());
        outTx.setNote(recipient.getEmail());
        outTx.setDescription(request.description());
        walletTransactionRepository.save(outTx);

        WalletTransaction inTx = new WalletTransaction();
        inTx.setUser(recipient);
        inTx.setType(WalletTransactionType.TRANSFER_IN);
        inTx.setAmount(request.amount());
        inTx.setNote(sender.getEmail());
        inTx.setDescription(request.description());
        walletTransactionRepository.save(inTx);

        try {
            emailService.sendTransferReceivedEmail(
                    recipient.getEmail(), sender.getEmail(),
                    request.amount(), request.description());
        } catch (Exception e) {
            log.warn("Failed to send transfer email to {}: {}", recipient.getEmail(), e.getMessage());
        }

        return new BalanceResponse(sender.getId(), sender.getBalance());
    }

    public Page<WalletTransactionResponse> getWalletHistory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return walletTransactionRepository.findByUserIdOrderByTimestampDesc(userId, pageable)
                .map(tx -> new WalletTransactionResponse(
                        tx.getId(),
                        tx.getType().name(),
                        tx.getAmount(),
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().getLast4() : null,
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().getCardType() : null,
                        tx.getNote(),
                        tx.getDescription(),
                        tx.getTimestamp()
                ));
    }
}

package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.dto.request.ChangePasswordRequest;
import github.kaloyanov5.merkantil.dto.request.TransferRequest;
import github.kaloyanov5.merkantil.dto.response.BalanceResponse;
import github.kaloyanov5.merkantil.dto.response.UserResponse;
import github.kaloyanov5.merkantil.dto.response.WalletTransactionResponse;
import github.kaloyanov5.merkantil.entity.PaymentMethod;
import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.entity.WalletTransaction;
import github.kaloyanov5.merkantil.entity.WalletTransactionType;
import github.kaloyanov5.merkantil.repository.PaymentMethodRepository;
import github.kaloyanov5.merkantil.repository.UserRepository;
import github.kaloyanov5.merkantil.repository.WalletTransactionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.YearMonth;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionService loginSessionService;

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must differ from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all active sessions across all devices
        loginSessionService.revokeAllSessions(user.getId());
    }

    public Map<String, String> lookupByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(u -> Map.of("firstName", u.getFirstName(), "lastName", u.getLastName()))
                .orElse(null);
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return mapToUserResponse(user);
    }

    public Page<UserResponse> getAllUsers(int page, int size, String sortBy, String direction) {
        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return userRepository.findAll(pageable).map(this::mapToUserResponse);
    }

    public Page<UserResponse> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                query, query, query, pageable).map(this::mapToUserResponse);
    }

    public BalanceResponse getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new BalanceResponse(user.getId(), user.getBalance());
    }

    @Transactional
    public BalanceResponse deposit(Long userId, BigDecimal amount, Long currentUserId, Long paymentMethodId) {
        if (!userId.equals(currentUserId)) {
            throw new IllegalArgumentException("You can only deposit to your own account");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        PaymentMethod paymentMethod = null;
        if (paymentMethodId != null) {
            paymentMethod = paymentMethodRepository.findByIdAndUserIdAndDeletedAtIsNull(paymentMethodId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));

            YearMonth expiry = YearMonth.of(paymentMethod.getExpiryYear(), paymentMethod.getExpiryMonth());
            if (expiry.isBefore(YearMonth.now())) {
                throw new IllegalArgumentException("Card ending in " + paymentMethod.getLast4() + " has expired");
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
    public BalanceResponse withdraw(Long userId, BigDecimal amount, Long currentUserId) {
        if (!userId.equals(currentUserId)) {
            throw new IllegalArgumentException("You can only withdraw from your own account");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
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
    public BalanceResponse transfer(Long senderId, TransferRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        User recipient = userRepository.findByEmail(request.getRecipientEmail())
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));

        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("Cannot transfer funds to yourself");
        }

        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException(
                    String.format("Insufficient funds. Available: $%.2f", sender.getBalance()));
        }

        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        recipient.setBalance(recipient.getBalance().add(request.getAmount()));
        userRepository.save(sender);
        userRepository.save(recipient);

        WalletTransaction outTx = new WalletTransaction();
        outTx.setUser(sender);
        outTx.setType(WalletTransactionType.TRANSFER_OUT);
        outTx.setAmount(request.getAmount());
        outTx.setNote(recipient.getEmail());
        walletTransactionRepository.save(outTx);

        WalletTransaction inTx = new WalletTransaction();
        inTx.setUser(recipient);
        inTx.setType(WalletTransactionType.TRANSFER_IN);
        inTx.setAmount(request.getAmount());
        inTx.setNote(sender.getEmail());
        walletTransactionRepository.save(inTx);

        return new BalanceResponse(sender.getId(), sender.getBalance());
    }

    public Page<WalletTransactionResponse> getWalletHistory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return walletTransactionRepository.findByUserIdOrderByTimestampDesc(userId, pageable)
                .map(tx -> new WalletTransactionResponse(
                        tx.getId(),
                        tx.getType().name(),
                        tx.getAmount(),
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().getLast4() : null,
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().getCardType() : null,
                        tx.getNote(),
                        tx.getTimestamp()
                ));
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getBalance(),
                user.getCreatedAt()
        );
    }
}

package github.kaloyanov5.merkantil.service;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final WalletTransactionRepository walletTransactionRepository;

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
        return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                query, query, pageable).map(this::mapToUserResponse);
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
            paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Payment method not found"));
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

    public Page<WalletTransactionResponse> getWalletHistory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return walletTransactionRepository.findByUserIdOrderByTimestampDesc(userId, pageable)
                .map(tx -> new WalletTransactionResponse(
                        tx.getId(),
                        tx.getType().name(),
                        tx.getAmount(),
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().getLast4() : null,
                        tx.getPaymentMethod() != null ? tx.getPaymentMethod().getCardType() : null,
                        tx.getTimestamp()
                ));
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBalance(),
                user.getCreatedAt()
        );
    }
}

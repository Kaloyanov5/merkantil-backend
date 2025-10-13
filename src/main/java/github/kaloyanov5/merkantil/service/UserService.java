package github.kaloyanov5.merkantil.service;

import github.kaloyanov5.merkantil.entity.User;
import github.kaloyanov5.merkantil.repository.UserRepository;
import github.kaloyanov5.merkantil.request.BalanceResponse;
import github.kaloyanov5.merkantil.request.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    // password encoder (later?)

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

        return userRepository.findAll(pageable)
                .map(this::mapToUserResponse);
    }

    public Page<UserResponse> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> users = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                query, query, pageable);

        return users.map(this::mapToUserResponse);
    }

    public BalanceResponse getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new BalanceResponse(user.getId(), user.getBalance());
    }

    @Transactional
    public BalanceResponse deposit(Long userId, Double amount, Long currentUserId) {
        if (!userId.equals(currentUserId)) {
            throw new IllegalArgumentException("You can only deposit to your own account");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setBalance(user.getBalance() + amount);
        User updatedUser = userRepository.save(user);

        return new BalanceResponse(updatedUser.getId(), updatedUser.getBalance());
    }

    @Transactional
    public BalanceResponse withdraw(Long userId, Double amount, Long currentUserId) {
        if (!userId.equals(currentUserId)) {
            throw new IllegalArgumentException("You can only withdraw from your own account");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getBalance() < amount) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        user.setBalance(user.getBalance() - amount);
        User updatedUser = userRepository.save(user);

        return new BalanceResponse(updatedUser.getId(), updatedUser.getBalance());
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

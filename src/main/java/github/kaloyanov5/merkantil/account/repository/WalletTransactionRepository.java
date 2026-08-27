package github.kaloyanov5.merkantil.account.repository;

import github.kaloyanov5.merkantil.account.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    Page<WalletTransaction> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);
}

package github.kaloyanov5.merkantil.account.repository;

import github.kaloyanov5.merkantil.account.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserIdAndDeletedAtIsNull(Long userId);
    Optional<PaymentMethod> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
    long countByUserIdAndDeletedAtIsNull(Long userId);
}

package github.kaloyanov5.merkantil.repository;

import github.kaloyanov5.merkantil.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserId(Long userId);
    Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId);
}

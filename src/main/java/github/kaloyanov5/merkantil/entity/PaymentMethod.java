package github.kaloyanov5.merkantil.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_methods")
@Getter @Setter
@EntityListeners(AuditingEntityListener.class)
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    @Column(nullable = false, name = "cardholder_name")
    private String cardholderName;

    @Column(nullable = false, name = "last4", length = 4)
    private String last4;

    @Column(nullable = false, name = "expiry_month")
    private Integer expiryMonth;

    @Column(nullable = false, name = "expiry_year")
    private Integer expiryYear;

    @Column(nullable = false, name = "card_type")
    private String cardType; // VISA, MASTERCARD, AMEX, DISCOVER

    @CreatedDate
    @Column(nullable = false, updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

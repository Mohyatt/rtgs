package fr.betuf.rtgs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String typeAction;

    private Long idObjet;

    private String typeObjet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(length = 1000)
    private String details;

    private LocalDateTime dateHeure;

    @PrePersist
    protected void onCreate() {
        dateHeure = LocalDateTime.now();
    }
}

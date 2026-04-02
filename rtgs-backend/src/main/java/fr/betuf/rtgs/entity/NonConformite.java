package fr.betuf.rtgs.entity;

import fr.betuf.rtgs.entity.enums.NCGravite;
import fr.betuf.rtgs.entity.enums.NCStatut;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "non_conformites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonConformite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id", nullable = false)
    private Intervention intervention;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declarant_id")
    private Utilisateur declarant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NCGravite gravite;

    @Column(length = 500, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NCStatut statut;

    private Integer delaiCorrectionJours;

    private LocalDateTime dateDeclaration;

    @PrePersist
    protected void onCreate() {
        dateDeclaration = LocalDateTime.now();
        if (statut == null) {
            statut = NCStatut.DECLAREE;
        }
    }
}

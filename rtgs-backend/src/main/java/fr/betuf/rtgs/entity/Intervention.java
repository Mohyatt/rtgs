package fr.betuf.rtgs.entity;

import fr.betuf.rtgs.entity.enums.InterventionStatut;
import fr.betuf.rtgs.entity.enums.InterventionType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "interventions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Intervention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tunnel_id", nullable = false)
    private Tunnel tunnel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterventionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterventionStatut statut;

    @Column(nullable = false)
    private LocalDate datePrevue;

    private LocalDate dateFinPrevue;

    private LocalDate dateRealisation;

    private String competencesRequises;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String resume;

    @Column(length = 1000)
    private String recommandations;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createur_id")
    private Utilisateur createur;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "intervention", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Affectation> affectations = new ArrayList<>();

    @OneToMany(mappedBy = "intervention", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<NonConformite> nonConformites = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (reference == null) {
            reference = "INT-" + LocalDateTime.now().getYear() + "-" +
                    String.format("%04d", (int) (Math.random() * 9000 + 1000));
        }
        if (statut == null) {
            statut = InterventionStatut.BROUILLON;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

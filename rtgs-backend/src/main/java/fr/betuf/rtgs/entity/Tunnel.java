package fr.betuf.rtgs.entity;

import fr.betuf.rtgs.entity.enums.TunnelStatut;
import fr.betuf.rtgs.entity.enums.TunnelType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tunnels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tunnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String libelle;

    private String departement;

    private Double longueur;

    private Integer nbTubes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TunnelStatut statut;

    @Enumerated(EnumType.STRING)
    private TunnelType type;

    private LocalDate dateDerniereVisite;

    private String exploitant;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

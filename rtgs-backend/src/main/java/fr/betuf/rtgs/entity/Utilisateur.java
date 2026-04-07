package fr.betuf.rtgs.entity;

import fr.betuf.rtgs.entity.enums.UserRole;
import fr.betuf.rtgs.entity.enums.UserStatut;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatut statut;

    private String competences;

    private String pole;

    private String organisation;

    /** Date de retour prévue lors d'une déclaration d'indisponibilité */
    @Column(name = "date_retour")
    private LocalDate dateRetour;

    /** Motif de l'indisponibilité en cours */
    @Column(name = "motif_indispo", length = 500)
    private String motifIndispo;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (statut == null) {
            statut = UserStatut.ACTIF;
        }
    }

    public String getNomComplet() {
        return prenom + " " + nom;
    }
}

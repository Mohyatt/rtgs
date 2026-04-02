package fr.betuf.rtgs.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerte_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlerteAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alerte_audit_log_id", nullable = false)
    private Long alerteAuditLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_mission_id", nullable = false)
    private Utilisateur chargeMission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigne_par_id")
    private Utilisateur assignePar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intervention_id")
    private Intervention intervention;

    @Column(nullable = false, length = 20)
    private String statut;

    private String niveau;

    @Column(name = "objet_type")
    private String objetType;

    @Column(name = "objet_id")
    private Long objetId;

    @Column(name = "objet_libelle")
    private String objetLibelle;

    @Column(length = 1000)
    private String description;

    private String commentaire;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "traite_at")
    private LocalDateTime traiteAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (statut == null) statut = "ASSIGNEE";
    }
}

package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPerformanceDTO {
    private Long id;
    private String nomComplet;
    private String email;
    private String role;
    private String statut;
    private String pole;

    // Commun
    private int missionsActives;
    private int missionsCloturees;
    private int missionsEnRetard;
    private int missionsTotal;
    private int tauxOccupation;      // missionsActives / seuil max * 100

    // INGENIEUR
    private int missionsChefActives;
    private int missionsIntervenantActives;
    private String niveauCharge;     // OK / MOYEN / ELEVE

    // CHARGE_MISSION
    private int alertesEnAttente;
    private int alertesTraitees;
    private int alertesTotal;
    private int tauxTraitementAlertes;
    private int tauxCloture;
    private int interventionsACloturer;
}

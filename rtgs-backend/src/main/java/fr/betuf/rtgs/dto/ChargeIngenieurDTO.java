package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeIngenieurDTO {
    private Long id;
    private String nomComplet;
    private String pole;
    private String competences;
    private int nbMissionsActives;
    private String niveau;   // OK, MOYEN, ELEVE
    private String statut;
}

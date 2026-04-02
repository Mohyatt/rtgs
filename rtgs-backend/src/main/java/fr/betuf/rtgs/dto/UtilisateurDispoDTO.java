package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurDispoDTO {
    private Long id;
    private String nomComplet;
    private String email;
    private String role;
    private String statut;
    private String competences;
    private List<String> competencesList;
    private String pole;
    private String disponibilite; // DISPONIBLE, CONFLIT, INDISPONIBLE
    private int nbMissionsActives;
}

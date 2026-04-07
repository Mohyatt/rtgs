package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilisateurDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String email;
    private String role;
    private String statut;
    private String competences;
    private String pole;
    private String organisation;
    private String dateRetour;
    private String motifIndispo;
}

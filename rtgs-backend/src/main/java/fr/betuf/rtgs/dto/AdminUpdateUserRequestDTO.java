package fr.betuf.rtgs.dto;

import lombok.Data;

@Data
public class AdminUpdateUserRequestDTO {
    private String nom;
    private String prenom;
    private String email;
    private String role;
    private String statut;
    private String competences;
    private String pole;
    private String organisation;
}

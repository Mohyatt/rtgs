package fr.betuf.rtgs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonConformiteDTO {
    private Long id;
    private String gravite;
    private String description;
    private String statut;
    private Integer delaiCorrectionJours;
    private Long declarantId;
    private String dateDeclaration;
}

package fr.betuf.rtgs.dto;

import lombok.Data;

@Data
public class TraiterAlerteRequest {
    private String commentaire;
    private Long chargeMissionId;
}

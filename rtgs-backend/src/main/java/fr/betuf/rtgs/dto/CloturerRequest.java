package fr.betuf.rtgs.dto;

import lombok.Data;

import java.util.List;

@Data
public class CloturerRequest {
    private String dateRealisation;
    private String resume;
    private String recommandations;
    private List<NonConformiteDTO> nonConformites;
}

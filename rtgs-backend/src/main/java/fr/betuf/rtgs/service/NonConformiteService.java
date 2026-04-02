package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.NonConformiteDTO;
import fr.betuf.rtgs.entity.NonConformite;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.entity.enums.NCStatut;
import fr.betuf.rtgs.repository.NonConformiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NonConformiteService {

    private final NonConformiteRepository nonConformiteRepository;
    private final AuditLogService auditLogService;

    public List<NonConformiteDTO> getByIntervention(Long interventionId) {
        return nonConformiteRepository.findByInterventionId(interventionId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public NonConformiteDTO updateStatut(Long id, String newStatut, Utilisateur utilisateur) {
        NonConformite nc = nonConformiteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "NC non trouvée"));

        NCStatut ancien = nc.getStatut();
        nc.setStatut(NCStatut.valueOf(newStatut));
        nc = nonConformiteRepository.save(nc);

        auditLogService.save("MAJ_STATUT_NC", nc.getId(), "NON_CONFORMITE", utilisateur,
                "NC #" + nc.getId() + " sur " + nc.getIntervention().getReference()
                + " — statut : " + ancien + " → " + newStatut
                + " — par : " + utilisateur.getNomComplet());

        return toDTO(nc);
    }

    private NonConformiteDTO toDTO(NonConformite nc) {
        return NonConformiteDTO.builder()
                .id(nc.getId())
                .gravite(nc.getGravite().name())
                .description(nc.getDescription())
                .statut(nc.getStatut().name())
                .delaiCorrectionJours(nc.getDelaiCorrectionJours())
                .declarantId(nc.getDeclarant() != null ? nc.getDeclarant().getId() : null)
                .dateDeclaration(nc.getDateDeclaration() != null ? nc.getDateDeclaration().toString() : null)
                .build();
    }
}

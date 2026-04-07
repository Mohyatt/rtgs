package fr.betuf.rtgs.service;

import fr.betuf.rtgs.dto.AuditLogDTO;
import fr.betuf.rtgs.entity.AuditLog;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLog save(String typeAction, Long idObjet, String typeObjet,
                         Utilisateur utilisateur, String details) {
        AuditLog log = AuditLog.builder()
                .typeAction(typeAction)
                .idObjet(idObjet)
                .typeObjet(typeObjet)
                .utilisateur(utilisateur)
                .details(details)
                .build();
        return auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findAll() {
        return auditLogRepository.findAllByOrderByDateHeureDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogDTO> findByTypeObjet(String typeObjet, Long idObjet) {
        return auditLogRepository.findByTypeObjetAndIdObjetOrderByDateHeureDesc(typeObjet, idObjet)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<AuditLog> findByTypeAction(String typeAction) {
        return auditLogRepository.findByTypeActionOrderByDateHeureDesc(typeAction);
    }

    private AuditLogDTO toDTO(AuditLog log) {
        return AuditLogDTO.builder()
                .id(log.getId())
                .typeAction(log.getTypeAction())
                .idObjet(log.getIdObjet())
                .typeObjet(log.getTypeObjet())
                .details(log.getDetails())
                .dateHeure(log.getDateHeure() != null ? log.getDateHeure().toString() : null)
                .utilisateurId(log.getUtilisateur() != null ? log.getUtilisateur().getId() : null)
                .utilisateurNom(log.getUtilisateur() != null ? log.getUtilisateur().getNomComplet() : null)
                .build();
    }
}

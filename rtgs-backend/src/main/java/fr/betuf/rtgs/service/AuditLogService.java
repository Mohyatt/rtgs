package fr.betuf.rtgs.service;

import fr.betuf.rtgs.entity.AuditLog;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<AuditLog> findAll() {
        return auditLogRepository.findAllByOrderByDateHeureDesc();
    }

    public List<AuditLog> findByTypeObjet(String typeObjet, Long idObjet) {
        return auditLogRepository.findByTypeObjetAndIdObjetOrderByDateHeureDesc(typeObjet, idObjet);
    }

    public List<AuditLog> findByTypeAction(String typeAction) {
        return auditLogRepository.findByTypeActionOrderByDateHeureDesc(typeAction);
    }
}

package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.entity.AuditLog;
import fr.betuf.rtgs.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAll(
            @RequestParam(required = false) String typeObjet,
            @RequestParam(required = false) Long idObjet) {
        if (typeObjet != null && idObjet != null) {
            return ResponseEntity.ok(auditLogService.findByTypeObjet(typeObjet, idObjet));
        }
        return ResponseEntity.ok(auditLogService.findAll());
    }
}

package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.*;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import fr.betuf.rtgs.security.JwtUtil;
import fr.betuf.rtgs.service.InterventionService;
import fr.betuf.rtgs.service.PlanificationAutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interventions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION','INGENIEUR','EXPLOITANT')")
public class InterventionController {

    private final InterventionService interventionService;
    private final PlanificationAutoService planificationAutoService;
    private final JwtUtil jwtUtil;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public ResponseEntity<List<InterventionDTO>> getAll(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long tunnelId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            HttpServletRequest request) {
        Utilisateur currentUser = extractUser(request);
        return ResponseEntity.ok(interventionService.getAll(statut, type, tunnelId, dateFrom, dateTo, currentUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterventionDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(interventionService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<InterventionDTO> create(@RequestBody InterventionDTO dto, HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(interventionService.create(dto, u));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<InterventionDTO> update(@PathVariable Long id, @RequestBody InterventionDTO dto, HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(interventionService.update(id, dto, u));
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('CHARGE_MISSION','INGENIEUR')")
    public ResponseEntity<InterventionDTO> changeStatut(@PathVariable Long id,
                                                         @RequestBody ChangeStatutRequest req,
                                                         HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(interventionService.changeStatut(id, req.getNewStatut(), u));
    }

    @PostMapping("/{id}/affectations")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<InterventionDTO> affecter(@PathVariable Long id,
                                                      @RequestBody List<AffectationRequestDTO> dtos,
                                                      HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(interventionService.affecter(id, dtos, u));
    }

    @GetMapping("/{id}/intervenants-disponibles")
    public ResponseEntity<List<UtilisateurDispoDTO>> getIntervenantsDisponibles(@PathVariable Long id) {
        return ResponseEntity.ok(interventionService.getIntervenantsDisponibles(id));
    }

    @GetMapping("/{id}/conflits/{userId}")
    public ResponseEntity<ConflitPlanningDTO> detecterConflits(@PathVariable Long id, @PathVariable Long userId) {
        return ResponseEntity.ok(interventionService.detecterConflits(id, userId));
    }

    @PostMapping("/{id}/rapport")
    @PreAuthorize("hasRole('INGENIEUR')")
    public ResponseEntity<InterventionDTO> soumettreRapport(@PathVariable Long id,
                                                              @RequestBody RapportRequest req,
                                                              HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(interventionService.soumettreRapport(id, req.getResume(), req.getRecommandations(), u));
    }

    @PostMapping("/{id}/cloturer")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<CloturerResponse> cloturer(@PathVariable Long id,
                                                       @RequestBody CloturerRequest req,
                                                       HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(interventionService.cloturer(id, req, u));
    }

    @PostMapping("/planifier-automatique")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<Map<String, Integer>> planifierAutomatique() {
        int count = planificationAutoService.planifierVisitesPeriodiques();
        return ResponseEntity.ok(Map.of("count", count));
    }

    private Utilisateur extractUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long userId = jwtUtil.extractUserId(header.substring(7));
                return utilisateurRepository.findById(userId).orElse(null);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}

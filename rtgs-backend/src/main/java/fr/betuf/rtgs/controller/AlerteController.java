package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.AlerteAssignmentDTO;
import fr.betuf.rtgs.dto.AlerteDTO;
import fr.betuf.rtgs.dto.TraiterAlerteRequest;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import fr.betuf.rtgs.security.JwtUtil;
import fr.betuf.rtgs.service.AlerteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alertes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION')")
public class AlerteController {

    private final AlerteService alerteService;
    private final JwtUtil jwtUtil;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public ResponseEntity<List<AlerteDTO>> getAlertes(@RequestParam(required = false) String niveau) {
        return ResponseEntity.ok(alerteService.getAlertesActives(niveau));
    }

    @PostMapping("/calculer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlerteDTO>> calculer() {
        return ResponseEntity.ok(alerteService.calculerAlertes());
    }

    @GetMapping("/charges-mission")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getChargesMission() {
        return ResponseEntity.ok(alerteService.getChargesMissionAvecCharge());
    }

    @PutMapping("/{id}/traiter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlerteDTO> traiter(@PathVariable Long id,
                                              @RequestBody TraiterAlerteRequest request,
                                              HttpServletRequest httpRequest) {
        Utilisateur utilisateur = extractUser(httpRequest);
        return ResponseEntity.ok(alerteService.traiterAlerte(id, request, utilisateur));
    }

    @GetMapping("/mes-alertes")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<List<AlerteAssignmentDTO>> getMesAlertes(HttpServletRequest request) {
        Utilisateur user = extractUser(request);
        return ResponseEntity.ok(alerteService.getMesAlertes(user.getId()));
    }

    @GetMapping("/mes-alertes/en-attente")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<List<AlerteAssignmentDTO>> getMesAlertesEnAttente(HttpServletRequest request) {
        Utilisateur user = extractUser(request);
        return ResponseEntity.ok(alerteService.getMesAlertesEnAttente(user.getId()));
    }

    @PutMapping("/assignments/{id}/lier-intervention")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<AlerteAssignmentDTO> lierIntervention(@PathVariable Long id,
                                                                  @RequestParam Long interventionId) {
        return ResponseEntity.ok(alerteService.lierIntervention(id, interventionId));
    }

    @GetMapping("/performance")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<Map<String, Object>> getPerformance(HttpServletRequest request) {
        Utilisateur user = extractUser(request);
        return ResponseEntity.ok(alerteService.getPerformanceCdm(user.getId()));
    }

    private Utilisateur extractUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Long userId = jwtUtil.extractUserId(token);
            return utilisateurRepository.findById(userId).orElse(null);
        }
        return null;
    }
}

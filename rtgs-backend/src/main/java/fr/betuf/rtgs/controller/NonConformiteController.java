package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.NonConformiteDTO;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import fr.betuf.rtgs.security.JwtUtil;
import fr.betuf.rtgs.service.NonConformiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/non-conformites")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION','INGENIEUR','EXPLOITANT')")
public class NonConformiteController {

    private final NonConformiteService nonConformiteService;
    private final JwtUtil jwtUtil;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public ResponseEntity<List<NonConformiteDTO>> getByIntervention(@RequestParam Long interventionId) {
        return ResponseEntity.ok(nonConformiteService.getByIntervention(interventionId));
    }

    @PutMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('INGENIEUR','CHARGE_MISSION','ADMIN')")
    public ResponseEntity<NonConformiteDTO> updateStatut(@PathVariable Long id,
                                                          @RequestParam String newStatut,
                                                          HttpServletRequest request) {
        Utilisateur u = extractUser(request);
        return ResponseEntity.ok(nonConformiteService.updateStatut(id, newStatut, u));
    }

    private Utilisateur extractUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long userId = jwtUtil.extractUserId(header.substring(7));
                return utilisateurRepository.findById(userId).orElse(null);
            } catch (Exception e) { return null; }
        }
        return null;
    }
}

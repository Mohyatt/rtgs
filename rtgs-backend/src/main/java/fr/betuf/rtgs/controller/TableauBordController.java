package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.ChargeIngenieurDTO;
import fr.betuf.rtgs.dto.KpiDTO;
import fr.betuf.rtgs.entity.Utilisateur;
import fr.betuf.rtgs.repository.UtilisateurRepository;
import fr.betuf.rtgs.security.JwtUtil;
import fr.betuf.rtgs.service.TableauBordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tableau-bord")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION','INGENIEUR','EXPLOITANT')")
public class TableauBordController {

    private final TableauBordService tableauBordService;
    private final JwtUtil jwtUtil;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping("/kpi")
    public ResponseEntity<KpiDTO> getKpi() {
        return ResponseEntity.ok(tableauBordService.getKpi());
    }

    @GetMapping("/kpi/cdm")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<Map<String, Object>> getKpiCdm(HttpServletRequest request) {
        Utilisateur user = extractUser(request);
        return ResponseEntity.ok(tableauBordService.getKpiCdm(user.getId()));
    }

    @GetMapping("/kpi/ingenieur")
    @PreAuthorize("hasRole('INGENIEUR')")
    public ResponseEntity<Map<String, Object>> getKpiIngenieur(HttpServletRequest request) {
        Utilisateur user = extractUser(request);
        return ResponseEntity.ok(tableauBordService.getKpiIngenieur(user.getId()));
    }

    @GetMapping("/charge-ingenieurs")
    @PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION')")
    public ResponseEntity<List<ChargeIngenieurDTO>> getChargeIngenieurs() {
        return ResponseEntity.ok(tableauBordService.getChargeIngenieurs());
    }

    private Utilisateur extractUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = jwtUtil.extractUserId(header.substring(7));
            return utilisateurRepository.findById(userId).orElse(null);
        }
        return null;
    }
}

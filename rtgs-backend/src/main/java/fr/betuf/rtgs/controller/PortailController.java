package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.TunnelPublicDTO;
import fr.betuf.rtgs.service.AuditLogService;
import fr.betuf.rtgs.service.TunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/portail")
@RequiredArgsConstructor
public class PortailController {

    private final TunnelService tunnelService;
    private final AuditLogService auditLogService;

    @GetMapping("/tunnels")
    public ResponseEntity<List<TunnelPublicDTO>> getAllTunnels(HttpServletRequest request) {
        List<TunnelPublicDTO> tunnels = tunnelService.getAllPublic();
        auditLogService.save("CONSULTATION_PORTAIL", null, "PORTAIL", null,
                "Consultation portail public — liste des tunnels — IP : " + request.getRemoteAddr());
        return ResponseEntity.ok(tunnels);
    }

    @GetMapping("/tunnels/{id}")
    public ResponseEntity<TunnelPublicDTO> getTunnelById(@PathVariable Long id, HttpServletRequest request) {
        TunnelPublicDTO tunnel = tunnelService.getPublicById(id);
        auditLogService.save("CONSULTATION_PORTAIL", id, "TUNNEL", null,
                "Consultation portail public — tunnel : " + tunnel.getLibelle()
                + " — IP : " + request.getRemoteAddr());
        return ResponseEntity.ok(tunnel);
    }

    @GetMapping("/tunnels/{id}/rapport")
    public ResponseEntity<byte[]> getRapport(@PathVariable Long id) {
        TunnelPublicDTO tunnel = tunnelService.getPublicById(id);
        String content = "Fiche tunnel: " + tunnel.getLibelle();
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain")
                .header("Content-Disposition", "attachment; filename=fiche-tunnel-" + id + ".txt")
                .body(content.getBytes());
    }
}

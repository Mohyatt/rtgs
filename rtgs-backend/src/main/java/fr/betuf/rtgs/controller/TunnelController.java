package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.dto.TunnelDTO;
import fr.betuf.rtgs.service.TunnelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tunnels")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION','INGENIEUR','EXPLOITANT')")
public class TunnelController {

    private final TunnelService tunnelService;

    @GetMapping
    public ResponseEntity<List<TunnelDTO>> getAll() {
        return ResponseEntity.ok(tunnelService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TunnelDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tunnelService.getById(id));
    }

    @GetMapping("/recherche")
    public ResponseEntity<List<TunnelDTO>> rechercher(
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Double longueurMin,
            @RequestParam(required = false) Double longueurMax,
            @RequestParam(required = false) String statut) {
        return ResponseEntity.ok(tunnelService.rechercher(departement, type, longueurMin, longueurMax, statut));
    }

    @GetMapping("/{id}/historique")
    public ResponseEntity<?> getHistorique(@PathVariable Long id) {
        // Return interventions for this tunnel
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}/export-csv")
    public ResponseEntity<String> exportCsv(@PathVariable Long id) {
        TunnelDTO t = tunnelService.getById(id);
        String csv = "id,libelle,departement,longueur,type,dateDerniereVisite\n"
                + t.getId() + "," + t.getLibelle() + "," + t.getDepartement() + ","
                + t.getLongueur() + "," + t.getType() + "," + t.getDateDerniereVisite() + "\n";
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=tunnel-" + id + ".csv")
                .body(csv);
    }

    @PostMapping
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<TunnelDTO> create(@RequestBody TunnelDTO dto) {
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CHARGE_MISSION')")
    public ResponseEntity<TunnelDTO> update(@PathVariable Long id, @RequestBody TunnelDTO dto) {
        return ResponseEntity.ok(tunnelService.getById(id));
    }
}

package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.service.RapportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION','EXPLOITANT')")
public class RapportController {

    private final RapportService rapportService;

    @GetMapping("/mensuel")
    public ResponseEntity<byte[]> getMensuel(@RequestParam int mois, @RequestParam int annee) {
        byte[] pdf = rapportService.generateMonthlyReportPdf(mois, annee);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=rapport-" + annee + "-" + mois + ".pdf")
                .body(pdf);
    }

    @GetMapping("/trimestriel")
    public ResponseEntity<byte[]> getTrimestriel(@RequestParam int trimestre, @RequestParam int annee) {
        // Generate quarterly report (3 months)
        byte[] pdf = rapportService.generateMonthlyReportPdf(trimestre * 3, annee);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=rapport-T" + trimestre + "-" + annee + ".pdf")
                .body(pdf);
    }

    @GetMapping("/kpi-csv")
    public ResponseEntity<String> getKpiCsv() {
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=kpi.csv")
                .body(rapportService.generateKpiCsv());
    }
}

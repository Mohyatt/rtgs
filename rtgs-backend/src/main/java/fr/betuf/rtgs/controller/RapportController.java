package fr.betuf.rtgs.controller;

import fr.betuf.rtgs.service.RapportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CHARGE_MISSION','EXPLOITANT')")
public class RapportController {

    private final RapportService rapportService;

    /** Endpoint appelé par le frontend */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> getPdf(
            @RequestParam(defaultValue = "INTERVENTIONS") String type,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) Long tunnelId) {

        LocalDate from = dateDebut != null && !dateDebut.isBlank() ? LocalDate.parse(dateDebut) : null;
        LocalDate to   = dateFin   != null && !dateFin.isBlank()   ? LocalDate.parse(dateFin)   : null;

        byte[] pdf = rapportService.generatePdf(type, from, to, tunnelId);
        String filename = "rapport-" + type.toLowerCase() + "-" + LocalDate.now() + ".pdf";
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=" + filename)
                .body(pdf);
    }

    @GetMapping("/mensuel")
    public ResponseEntity<byte[]> getMensuel(@RequestParam int mois, @RequestParam int annee) {
        byte[] pdf = rapportService.generateMonthlyReportPdf(mois, annee);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=rapport-" + annee + "-" + mois + ".pdf")
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

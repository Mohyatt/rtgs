package fr.betuf.rtgs.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Cell;
import fr.betuf.rtgs.dto.KpiDTO;
import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.entity.enums.InterventionStatut;
import fr.betuf.rtgs.repository.InterventionRepository;
import fr.betuf.rtgs.repository.NonConformiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RapportService {

    private final InterventionRepository interventionRepository;
    private final NonConformiteRepository nonConformiteRepository;
    private final TableauBordService tableauBordService;

    public byte[] generateMonthlyReportPdf(int mois, int annee) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            document.add(new Paragraph("RTGS — Rapport mensuel")
                    .setBold().setFontSize(18));
            document.add(new Paragraph("BETuF — Bureau d'Étude des Tunnels de France")
                    .setFontSize(12));
            document.add(new Paragraph("Période : " + Month.of(mois).name() + " " + annee)
                    .setFontSize(12));
            document.add(new Paragraph(" "));

            KpiDTO kpi = tableauBordService.getKpi();
            document.add(new Paragraph("Indicateurs clés").setBold().setFontSize(14));
            document.add(new Paragraph("• Alertes critiques : " + kpi.getAlertesCritiques()));
            document.add(new Paragraph("• Interventions en retard : " + kpi.getInterventionsEnRetard()));
            document.add(new Paragraph("• Taux de conformité : " + kpi.getTauxConformite() + "%"));
            document.add(new Paragraph("• Interventions ce mois : " + kpi.getInterventionsCeMois()));
            document.add(new Paragraph(" "));

            // Interventions du mois
            LocalDate from = LocalDate.of(annee, mois, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            List<Intervention> interventions = interventionRepository.findByDatePrevueBetween(from, to);

            document.add(new Paragraph("Interventions du mois").setBold().setFontSize(14));
            if (interventions.isEmpty()) {
                document.add(new Paragraph("Aucune intervention planifiée pour cette période."));
            } else {
                Table table = new Table(new float[]{2, 3, 2, 2});
                table.addHeaderCell("Référence");
                table.addHeaderCell("Tunnel");
                table.addHeaderCell("Type");
                table.addHeaderCell("Statut");
                for (Intervention i : interventions) {
                    table.addCell(i.getReference());
                    table.addCell(i.getTunnel().getLibelle());
                    table.addCell(i.getType().getLibelle());
                    table.addCell(i.getStatut().name());
                }
                document.add(table);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    public String generateKpiCsv() {
        KpiDTO kpi = tableauBordService.getKpi();
        StringBuilder sb = new StringBuilder();
        sb.append("date,alertesCritiques,alertesPreventives,alertesInfo,interventionsEnRetard,interventionsCeMois,tauxConformite,tunnelsActifs\n");
        sb.append(LocalDate.now()).append(",")
                .append(kpi.getAlertesCritiques()).append(",")
                .append(kpi.getAlertesPreventives()).append(",")
                .append(kpi.getAlertesInfo()).append(",")
                .append(kpi.getInterventionsEnRetard()).append(",")
                .append(kpi.getInterventionsCeMois()).append(",")
                .append(kpi.getTauxConformite()).append(",")
                .append(kpi.getTunnelsActifs()).append("\n");
        return sb.toString();
    }
}

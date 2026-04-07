package fr.betuf.rtgs.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import fr.betuf.rtgs.dto.KpiDTO;
import fr.betuf.rtgs.entity.Intervention;
import fr.betuf.rtgs.entity.NonConformite;
import fr.betuf.rtgs.entity.Tunnel;
import fr.betuf.rtgs.entity.enums.InterventionStatut;
import fr.betuf.rtgs.entity.enums.TunnelStatut;
import fr.betuf.rtgs.repository.InterventionRepository;
import fr.betuf.rtgs.repository.NonConformiteRepository;
import fr.betuf.rtgs.repository.TunnelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RapportService {

    private final InterventionRepository interventionRepository;
    private final NonConformiteRepository nonConformiteRepository;
    private final TunnelRepository tunnelRepository;
    private final TableauBordService tableauBordService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(30, 58, 138);
    private static final DeviceRgb ROW_ALT = new DeviceRgb(240, 244, 255);

    @Transactional(readOnly = true)
    public byte[] generatePdf(String type, LocalDate dateDebut, LocalDate dateFinParam, Long tunnelId) {
        return switch (type) {
            case "CONFORMITE" -> generateConformite(dateDebut, dateFinParam);
            case "NON_CONFORMITES" -> generateNonConformites(dateDebut, dateFinParam, tunnelId);
            case "ACTIVITE" -> generateActivite(dateDebut, dateFinParam);
            default -> generateInterventions(dateDebut, dateFinParam, tunnelId);
        };
    }

    // ── RAPPORT INTERVENTIONS ────────────────────────────────────────────────

    private byte[] generateInterventions(LocalDate from, LocalDate to, Long tunnelId) {
        List<Intervention> list;
        if (tunnelId != null) {
            list = interventionRepository.findByTunnelIdOrderByDatePrevueDesc(tunnelId);
        } else if (from != null && to != null) {
            list = interventionRepository.findByDatePrevueBetween(from, to);
        } else {
            list = interventionRepository.findAllByOrderByDatePrevueDesc();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = newDoc(baos);
            addTitle(doc, "Rapport d'interventions");
            addPeriode(doc, from, to);

            Table t = newTable(new float[]{2, 3, 2, 2, 2});
            addHeader(t, "Référence", "Tunnel", "Type", "Date prévue", "Statut");
            int row = 0;
            for (Intervention i : list) {
                boolean alt = row++ % 2 == 1;
                addRow(t, alt,
                        i.getReference(),
                        i.getTunnel() != null ? i.getTunnel().getLibelle() : "—",
                        i.getType() != null ? i.getType().getLibelle() : "—",
                        i.getDatePrevue() != null ? i.getDatePrevue().format(FMT) : "—",
                        i.getStatut().name());
            }
            if (list.isEmpty()) addEmptyRow(t, 5);
            doc.add(t);
            addSummary(doc, "Total : " + list.size() + " intervention(s)");
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF interventions", e);
        }
    }

    // ── RAPPORT CONFORMITÉ ───────────────────────────────────────────────────

    private byte[] generateConformite(LocalDate from, LocalDate to) {
        List<Tunnel> tunnels = tunnelRepository.findByStatut(TunnelStatut.ACTIF);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = newDoc(baos);
            addTitle(doc, "Rapport de conformité réglementaire");
            addPeriode(doc, from, to);

            KpiDTO kpi = tableauBordService.getKpi();
            doc.add(new Paragraph("Taux de conformité global : " + kpi.getTauxConformite() + "%")
                    .setBold().setFontSize(13).setMarginBottom(12));

            Table t = newTable(new float[]{3, 2, 2, 2});
            addHeader(t, "Tunnel", "Longueur (m)", "Dernière visite", "Conformité");
            LocalDate seuil = LocalDate.now().minusDays(365);
            int row = 0;
            for (Tunnel tunnel : tunnels) {
                boolean alt = row++ % 2 == 1;
                String visite = tunnel.getDateDerniereVisite() != null
                        ? tunnel.getDateDerniereVisite().format(FMT) : "Jamais";
                boolean conforme = tunnel.getDateDerniereVisite() != null
                        && !tunnel.getDateDerniereVisite().isBefore(seuil);
                addRow(t, alt,
                        tunnel.getLibelle(),
                        tunnel.getLongueur() != null ? String.valueOf(tunnel.getLongueur().intValue()) : "—",
                        visite,
                        conforme ? "CONFORME" : "NON CONFORME");
            }
            if (tunnels.isEmpty()) addEmptyRow(t, 4);
            doc.add(t);
            long nonConformes = tunnels.stream()
                    .filter(tn -> tn.getDateDerniereVisite() == null || tn.getDateDerniereVisite().isBefore(seuil))
                    .count();
            addSummary(doc, tunnels.size() + " tunnel(s) actif(s) — " + nonConformes + " non conforme(s)");
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF conformité", e);
        }
    }

    // ── RAPPORT NON-CONFORMITÉS ──────────────────────────────────────────────

    private byte[] generateNonConformites(LocalDate from, LocalDate to, Long tunnelId) {
        List<Intervention> interventions;
        if (tunnelId != null) {
            interventions = interventionRepository.findByTunnelIdOrderByDatePrevueDesc(tunnelId);
        } else {
            interventions = interventionRepository.findByStatutOrderByDatePrevueDesc(InterventionStatut.CLOTUREE);
        }

        List<NonConformite> ncs = interventions.stream()
                .flatMap(i -> nonConformiteRepository.findByInterventionId(i.getId()).stream())
                .filter(nc -> {
                    if (from == null || to == null) return true;
                    LocalDate d = nc.getDateDeclaration() != null
                            ? nc.getDateDeclaration().toLocalDate() : null;
                    return d != null && !d.isBefore(from) && !d.isAfter(to);
                })
                .collect(Collectors.toList());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = newDoc(baos);
            addTitle(doc, "Rapport de non-conformités");
            addPeriode(doc, from, to);

            Table t = newTable(new float[]{2, 3, 2, 2, 3});
            addHeader(t, "Intervention", "Tunnel", "Gravité", "Statut", "Description");
            int row = 0;
            for (NonConformite nc : ncs) {
                boolean alt = row++ % 2 == 1;
                String ref = nc.getIntervention() != null ? nc.getIntervention().getReference() : "—";
                String tunnel = nc.getIntervention() != null && nc.getIntervention().getTunnel() != null
                        ? nc.getIntervention().getTunnel().getLibelle() : "—";
                String desc = nc.getDescription().length() > 80
                        ? nc.getDescription().substring(0, 80) + "…" : nc.getDescription();
                addRow(t, alt, ref, tunnel, nc.getGravite().name(), nc.getStatut().name(), desc);
            }
            if (ncs.isEmpty()) addEmptyRow(t, 5);
            doc.add(t);
            long critiques = ncs.stream().filter(nc -> nc.getGravite().name().equals("CRITIQUE")).count();
            addSummary(doc, ncs.size() + " NC — dont " + critiques + " critique(s)");
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF non-conformités", e);
        }
    }

    // ── RAPPORT ACTIVITÉ GLOBALE ─────────────────────────────────────────────

    private byte[] generateActivite(LocalDate from, LocalDate to) {
        List<Intervention> all = (from != null && to != null)
                ? interventionRepository.findByDatePrevueBetween(from, to)
                : interventionRepository.findAllByOrderByDatePrevueDesc();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = newDoc(baos);
            addTitle(doc, "Rapport d'activité global");
            addPeriode(doc, from, to);

            KpiDTO kpi = tableauBordService.getKpi();

            // KPIs
            Table kpiTable = newTable(new float[]{3, 2});
            addHeader(kpiTable, "Indicateur", "Valeur");
            addRow(kpiTable, false, "Interventions ce mois", String.valueOf(kpi.getInterventionsCeMois()));
            addRow(kpiTable, true, "Interventions en retard", String.valueOf(kpi.getInterventionsEnRetard()));
            addRow(kpiTable, false, "Alertes critiques actives", String.valueOf(kpi.getAlertesCritiques()));
            addRow(kpiTable, true, "Alertes préventives actives", String.valueOf(kpi.getAlertesPreventives()));
            addRow(kpiTable, false, "Taux de conformité", kpi.getTauxConformite() + "%");
            addRow(kpiTable, true, "Tunnels actifs", String.valueOf(kpi.getTunnelsActifs()));
            doc.add(kpiTable);
            doc.add(new Paragraph(" "));

            // Répartition par statut
            doc.add(new Paragraph("Répartition par statut").setBold().setFontSize(13).setMarginTop(8));
            Table statTable = newTable(new float[]{3, 2});
            addHeader(statTable, "Statut", "Nombre");
            int rowIdx = 0;
            for (InterventionStatut statut : InterventionStatut.values()) {
                long count = all.stream().filter(i -> i.getStatut() == statut).count();
                addRow(statTable, rowIdx++ % 2 == 1, statut.name(), String.valueOf(count));
            }
            doc.add(statTable);
            addSummary(doc, "Total : " + all.size() + " intervention(s) sur la période");

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF activité", e);
        }
    }

    // ── HELPERS PDF ──────────────────────────────────────────────────────────

    private Document newDoc(ByteArrayOutputStream baos) {
        PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
        return new Document(pdf);
    }

    private void addTitle(Document doc, String title) {
        doc.add(new Paragraph("RTGS — " + title)
                .setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));
        doc.add(new Paragraph("BETuF — Bureau d'Étude des Tunnels de France")
                .setFontSize(11).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY).setMarginBottom(4));
        doc.add(new Paragraph("Généré le " + LocalDate.now().format(FMT))
                .setFontSize(10).setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY).setMarginBottom(16));
    }

    private void addPeriode(Document doc, LocalDate from, LocalDate to) {
        if (from != null && to != null) {
            doc.add(new Paragraph("Période : " + from.format(FMT) + " → " + to.format(FMT))
                    .setFontSize(11).setMarginBottom(12));
        }
    }

    private Table newTable(float[] cols) {
        Table t = new Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth();
        t.setMarginBottom(8);
        return t;
    }

    private void addHeader(Table t, String... cols) {
        for (String col : cols) {
            t.addHeaderCell(new Cell().add(new Paragraph(col).setBold())
                    .setBackgroundColor(HEADER_COLOR)
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(5));
        }
    }

    private void addRow(Table t, boolean alt, String... vals) {
        DeviceRgb bg = alt ? ROW_ALT : null;
        for (String v : vals) {
            Cell c = new Cell().add(new Paragraph(v != null ? v : "—")).setPadding(4);
            if (bg != null) c.setBackgroundColor(bg);
            t.addCell(c);
        }
    }

    private void addEmptyRow(Table t, int cols) {
        Cell c = new Cell(1, cols)
                .add(new Paragraph("Aucune donnée pour cette période").setItalic())
                .setTextAlignment(TextAlignment.CENTER).setPadding(8);
        t.addCell(c);
    }

    private void addSummary(Document doc, String text) {
        doc.add(new Paragraph(text).setBold().setFontSize(11)
                .setMarginTop(8).setFontColor(ColorConstants.DARK_GRAY));
    }

    // ── ANCIENNE MÉTHODE (conservée pour compatibilité) ──────────────────────

    public byte[] generateMonthlyReportPdf(int mois, int annee) {
        LocalDate from = LocalDate.of(annee, mois, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        return generateInterventions(from, to, null);
    }

    public String generateKpiCsv() {
        KpiDTO kpi = tableauBordService.getKpi();
        return "date,alertesCritiques,alertesPreventives,alertesInfo,interventionsEnRetard,interventionsCeMois,tauxConformite,tunnelsActifs\n"
                + LocalDate.now() + ","
                + kpi.getAlertesCritiques() + ","
                + kpi.getAlertesPreventives() + ","
                + kpi.getAlertesInfo() + ","
                + kpi.getInterventionsEnRetard() + ","
                + kpi.getInterventionsCeMois() + ","
                + kpi.getTauxConformite() + ","
                + kpi.getTunnelsActifs() + "\n";
    }
}

package fr.betuf.rtgs.entity.enums;

public enum InterventionType {
    VISITE_SECURITE_ANNUELLE("Visite sécurité annuelle"),
    CONTROLE_VENTILATION("Contrôle ventilation"),
    EXPERTISE_GENIE_CIVIL("Expertise génie civil"),
    INSPECTION_PERIODIQUE("Inspection périodique"),
    CONTROLE_SUITE_INCIDENT("Contrôle suite à incident"),
    INTERVENTION_CORRECTIVE("Intervention corrective");

    private final String libelle;

    InterventionType(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}

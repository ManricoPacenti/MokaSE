package it.pacenti.moka.persistence.json.model;

import java.util.ArrayList;
import java.util.List;

public class TemplateDocument {

    private List<TemplateData> templates = new ArrayList<>();

    public TemplateDocument() {
    }

    public List<TemplateData> getTemplates() {
        return templates;
    }

    public void setTemplates(List<TemplateData> templates) {
        this.templates = templates;
    }
}

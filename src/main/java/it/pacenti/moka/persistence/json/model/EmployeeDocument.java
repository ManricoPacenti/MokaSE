package it.pacenti.moka.persistence.json.model;

import java.util.ArrayList;
import java.util.List;

public class EmployeeDocument {

    private int schemaVersion = 1;
    private List<EmployeeData> employees = new ArrayList<>();

    public EmployeeDocument() {
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<EmployeeData> getEmployees() {
        return employees;
    }

    public void setEmployees(List<EmployeeData> employees) {
        this.employees = employees != null ? employees : new ArrayList<>();
    }
}
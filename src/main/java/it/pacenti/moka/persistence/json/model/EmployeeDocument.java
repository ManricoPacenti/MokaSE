package it.pacenti.moka.persistence.json.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Root JSON document for employee persistence.
 */
public class EmployeeDocument {

    private int schemaVersion;
    private List<EmployeeData> employees;

    public EmployeeDocument() {
        this.schemaVersion = 2;
        this.employees = new ArrayList<>();
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
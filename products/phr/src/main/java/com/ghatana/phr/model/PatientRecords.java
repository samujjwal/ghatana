package com.ghatana.phr.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Data record entity
 *
 * @doc.type class
 * @doc.purpose Data record entity
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class PatientRecords {
    private List<PatientRecord> records = new ArrayList<>();
    private int totalCount;

    public PatientRecords() {
    }

    public PatientRecords(List<PatientRecord> records) {
        this.records = records;
        this.totalCount = records.size();
    }

    public List<PatientRecord> getRecords() {
        return records;
    }

    public void setRecords(List<PatientRecord> records) {
        this.records = records;
        this.totalCount = records.size();
    }

    public void addRecord(PatientRecord record) {
        this.records.add(record);
        this.totalCount = this.records.size();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int size() {
        return records.size();
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }
}

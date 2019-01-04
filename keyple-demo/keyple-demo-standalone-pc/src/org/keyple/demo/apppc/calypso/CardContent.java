package org.keyple.demo.apppc.calypso;

import java.util.SortedMap;

public class CardContent {
    byte[] serialNumber;
    String poRevision;
    String poTypeName;
    String extraInfo;
    SortedMap<Integer, byte[]> icc;
    SortedMap<Integer, byte[]> id;
    SortedMap<Integer, byte[]> environment;
    SortedMap<Integer, byte[]> eventLog;
    SortedMap<Integer, byte[]> specialEvents;
    SortedMap<Integer, byte[]> contractsList;
    SortedMap<Integer, byte[]> odMemory;
    SortedMap<Integer, byte[]> contracts;
    SortedMap<Integer, Integer> counters;


    public byte[] getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(byte[] serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getPoRevision() {
        return poRevision;
    }

    public void setPoRevision(String poRevision) {
        this.poRevision = poRevision;
    }

    public String getPoTypeName() {
        return poTypeName;
    }

    public void setPoTypeName(String poTypeName) {
        this.poTypeName = poTypeName;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    public SortedMap<Integer, byte[]> getIcc() {
        return icc;
    }

    public void setIcc(SortedMap<Integer, byte[]> icc) {
        this.icc = icc;
    }

    public SortedMap<Integer, byte[]> getId() {
        return id;
    }

    public void setId(SortedMap<Integer, byte[]> id) {
        this.id = id;
    }

    public SortedMap<Integer, byte[]> getEnvironment() {
        return environment;
    }

    public void setEnvironment(SortedMap<Integer, byte[]> environment) {
        this.environment = environment;
    }

    public SortedMap<Integer, byte[]> getEventLog() {
        return eventLog;
    }

    public void setEventLog(SortedMap<Integer, byte[]> eventLog) {
        this.eventLog = eventLog;
    }

    public SortedMap<Integer, byte[]> getSpecialEvents() {
        return specialEvents;
    }

    public void setSpecialEvents(SortedMap<Integer, byte[]> specialEvents) {
        this.specialEvents = specialEvents;
    }

    public SortedMap<Integer, byte[]> getContractsList() {
        return contractsList;
    }

    public void setContractsList(SortedMap<Integer, byte[]> contractsList) {
        this.contractsList = contractsList;
    }

    public SortedMap<Integer, byte[]> getOdMemory() {
        return odMemory;
    }

    public void setOdMemory(SortedMap<Integer, byte[]> odMemory) {
        this.odMemory = odMemory;
    }

    public SortedMap<Integer, byte[]> getContracts() {
        return contracts;
    }

    public void setContracts(SortedMap<Integer, byte[]> contracts) {
        this.contracts = contracts;
    }

    public SortedMap<Integer, Integer> getCounters() {
        return counters;
    }

    public void setCounters(SortedMap<Integer, Integer> counters) {
        this.counters = counters;
    }
}

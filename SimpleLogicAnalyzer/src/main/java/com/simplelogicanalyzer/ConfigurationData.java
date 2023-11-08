package com.simplelogicanalyzer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ConfigurationData {
    @JsonProperty("logicProbe")
    private Probe logicProbe;
    @JsonProperty("loggingProbe")
    private List<Probe> loggingProbe;
    @JsonProperty("signals")
    private List<String> signals;

    public Probe getLogicProbe() {
        return logicProbe;
    }

    public void setLogicProbe(Probe logicProbe) {
        this.logicProbe = logicProbe;
    }

    public List<Probe> getLoggingProbe() {
        return loggingProbe;
    }

    public void setLoggingProbe(List<Probe> loggingProbe) {
        this.loggingProbe = loggingProbe;
    }

    public List<String> getSignals() {
        return signals;
    }

    public void setSignals(List<String> signals) {
        this.signals = signals;
    }
}
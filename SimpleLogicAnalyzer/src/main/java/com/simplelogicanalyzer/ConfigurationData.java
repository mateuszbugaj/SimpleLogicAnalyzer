package com.simplelogicanalyzer;

import java.util.List;

public class ConfigurationData {

    private String logicProbe;
    private List<String> loggingProbe;
    private List<String> signals;

    public String getLogicProbe() {
        return logicProbe;
    }

    public void setLogicProbe(String logicProbe) {
        this.logicProbe = logicProbe;
    }

    public List<String> getLoggingProbe() {
        return loggingProbe;
    }

    public void setLoggingProbe(List<String> loggingProbe) {
        this.loggingProbe = loggingProbe;
    }

    public List<String> getSignals() {
        return signals;
    }

    public void setSignals(List<String> signals) {
        this.signals = signals;
    }
}
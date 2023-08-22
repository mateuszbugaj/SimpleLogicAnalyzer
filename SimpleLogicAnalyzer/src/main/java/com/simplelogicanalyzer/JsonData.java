package com.simplelogicanalyzer;

import java.util.List;

public class JsonData {

    private Integer logicProbeProductID;
    private Integer usbUartProductID;
    private List<String> signals;

    public Integer getLogicProbeProductID() {
        return logicProbeProductID;
    }

    public void setLogicProbeProductID(Integer logicProbeProductID) {
        this.logicProbeProductID = logicProbeProductID;
    }

    public Integer getUsbUartProductID() {
        return usbUartProductID;
    }

    public void setUsbUartProductID(Integer usbUartProductID) {
        this.usbUartProductID = usbUartProductID;
    }

    public List<String> getSignals() {
        return signals;
    }

    public void setSignals(List<String> signals) {
        this.signals = signals;
    }
}
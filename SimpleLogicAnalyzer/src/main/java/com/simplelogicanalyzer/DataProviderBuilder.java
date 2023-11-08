package com.simplelogicanalyzer;

import javafx.collections.FXCollections;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class DataProviderBuilder {
    private final ConfigurationData configurationData;
    private final ArrayList<Signal> signals;
    private final Signal logSignal;

    public DataProviderBuilder(ConfigurationData configurationData, ArrayList<Signal> signals, Signal logSignal) {
        this.configurationData = configurationData;
        this.signals = signals;
        this.logSignal = logSignal;
    }

    public DataProvider getDataProvider(){
        if(configurationData.getLogicProbe().getPort() != null){
            return new UsbDataProvider(signals, configurationData.getLogicProbe(), logSignal, configurationData.getLoggingProbe());
        } else {
            return new FileDataProvider(signals, configurationData.getLogicProbe(), logSignal, configurationData.getLoggingProbe());
        }
    }
}

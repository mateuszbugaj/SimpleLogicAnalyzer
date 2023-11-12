package com.simplelogicanalyzer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

public class FileDataProvider implements DataProvider{
    private final ArrayList<ObservableList<DataPoint>> logDataList = new ArrayList<>();
    private final ArrayList<Signal> signals;
    private final Signal logSignal;


    public FileDataProvider(ArrayList<Signal> signals, Probe probeDataFile, Signal logSignal, List<Probe> logFiles){
        this.signals = signals;
        this.logSignal = logSignal;

        ObservableList<String> probeRawData = FXCollections.observableArrayList();
        getFileChangeListener(probeDataFile.getFile(), probeRawData);
        probeRawData.addListener((ListChangeListener<String>) change -> {
            Platform.runLater(() -> {
                while(change.next()){
                    try {
                        List<Integer> parsedProbeData = parseProbeData(change.getAddedSubList().get(0));
                        for(int i = 0; i < parsedProbeData.size() && i < signals.size(); i++){
                            signals.get(i).series.getData().add(new XYChart.Data<>(
                                    signals.get(i).series.getData().size(),
                                    parsedProbeData.get(i)
                            ));
                        }
                    } catch (ConcurrentModificationException ignore ) {} // TODO: THIS IS BAD AND NOT GOOD
                }
            });
        });

        for(Probe logFile : logFiles){
            ObservableList<String> logRawData = FXCollections.observableArrayList();
            ObservableList<DataPoint> logData = FXCollections.observableArrayList();

            getFileChangeListener(logFile.getFile(), logRawData);
            logRawData.addListener((ListChangeListener<String>) change -> {
                while(change.next()){
                    logData.add(0, new DataPoint(change.getAddedSubList().get(0), System.currentTimeMillis()));
                }
            });

            logData.addListener(new LogDataListener(signals, logSignal));

            logDataList.add(logData);
        }
    }

    private void getFileChangeListener(String filePath, ObservableList<String> output){
        System.out.println("Creating file listener for " + filePath);
        FileChangeListener fileListener = new FileChangeListener(filePath, output);

        Thread thread = new Thread(fileListener);
        thread.setDaemon(true);
        thread.start();

    }

    private List<Integer> parseProbeData(String s){
        ArrayList<Integer> output = new ArrayList<>();
        String[] dataSplit = s.split(" ");
        for(String dataPoint : dataSplit){
            output.add(Integer.parseInt(dataPoint));
        }

        return output;
    }

    @Override
    public ArrayList<ObservableList<DataPoint>> getLogDataList() {
        return logDataList;
    }

    @Override
    public void sendProbe(String msg) {
        // TODO: Not implemented (files don't require)
    }

    @Override
    public void sendLogging(String msg, String target) {

    }

    @Override
    public void clear() {
        // Refresh signal charts by adding one datapoint
        for (Signal signal : signals) {
            signal.series.getData().add(new XYChart.Data<>(signal.series.getData().size(), 0));
        }

        logSignal.series.getData().add(new XYChart.Data<>(logSignal.series.getData().size(), 0));
        logDataList.forEach(List::clear);
    }
}
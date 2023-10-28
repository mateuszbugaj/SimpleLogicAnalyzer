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
    private final ObservableList<DataPoint> probeData = FXCollections.observableArrayList();
    private final ArrayList<ObservableList<DataPoint>> logDataList = new ArrayList<>();


    public FileDataProvider(ArrayList<Signal> signals, String probeDataFile, Signal logSignal, List<String> logFiles){
        ObservableList<String> probeRawData = FXCollections.observableArrayList();
        getFileChangeListener(probeDataFile, probeRawData);
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

        for(String logFile : logFiles){
            ObservableList<String> logRawData = FXCollections.observableArrayList();
            ObservableList<DataPoint> logData = FXCollections.observableArrayList();

            getFileChangeListener(logFile, logRawData);
            logRawData.addListener((ListChangeListener<String>) change -> {
                while(change.next()){
                    logData.add(0, new DataPoint(change.getAddedSubList().get(0), System.currentTimeMillis()));
                }
            });

            logData.addListener(new LogDataListener(signals, logSignal));

            logDataList.add(logData);
        }
    }

    private Thread getFileChangeListener(String filePath, ObservableList<String> output){
        System.out.println("Creating file listener for " + filePath);
        FileChangeListener fileListener = new FileChangeListener(filePath, output);

        Thread thread = new Thread(fileListener);
        thread.setDaemon(true);
        thread.start();

        return thread;
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
    public void send(String msg) {
        // TODO: Not implemented (files don't require)
    }
}
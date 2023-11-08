package com.simplelogicanalyzer;

import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;

import java.io.IOException;
import java.util.*;

public class UsbDataProvider implements DataProvider{
    private final ArrayList<ObservableList<DataPoint>> logDataList = new ArrayList<>();
    private final SerialPort probeDevicePort;
    private final ArrayList<Signal> signals;
    private final Signal logSignal;

    public UsbDataProvider(ArrayList<Signal> signals, Probe probeDevice, Signal logSignal, List<Probe> logDevices) {
        this.signals = signals;
        this.logSignal = logSignal;

        ObservableList<String> probeRawData = FXCollections.observableArrayList();
        probeDevicePort = getDeviceListener(probeDevice, probeRawData);
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

        for(Probe logDevice : logDevices){
            ObservableList<String> logRawData = FXCollections.observableArrayList();
            ObservableList<DataPoint> logData = FXCollections.observableArrayList();

            getDeviceListener(logDevice, logRawData);
            logRawData.addListener((ListChangeListener<String>) change -> {
                while(change.next()){
                    logData.add(0, new DataPoint(change.getAddedSubList().get(0), System.currentTimeMillis()));
                }
            });

            logData.addListener(new LogDataListener(signals, logSignal));

            logDataList.add(logData);
        }
    }

    private SerialPort getDeviceListener(Probe device, ObservableList<String> output){
        System.out.println("Creating listener for " + device);
        Optional<SerialPort> serialPort = Arrays.stream(SerialPort.getCommPorts()).filter(port -> ("/dev/" + port.getSystemPortName()).equals(device.getPort())).findAny();

        if(serialPort.isPresent()){
            try {
                serialPort.get().setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                serialPort.get().setBaudRate(device.getBaudRate());
                serialPort.get().openPort();
                serialPort.get().getInputStream().skip(serialPort.get().bytesAvailable());
                serialPort.get().addDataListener(new SerialPortDataListenerImpl(output));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Device " + device + " not found");
            return null;
        }

        return serialPort.get();
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
        try {
            probeDevicePort.getOutputStream().write(msg.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear() {
        // Refresh signal charts by one datapoint.
        for (Signal signal : signals) {
            signal.series.getData().add(new XYChart.Data<>(signal.series.getData().size(), 0));
        }

        logSignal.series.getData().add(new XYChart.Data<>(logSignal.series.getData().size(), 0));
    }
}

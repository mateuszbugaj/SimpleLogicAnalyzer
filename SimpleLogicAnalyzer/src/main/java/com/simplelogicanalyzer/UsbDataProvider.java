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
    private final ObservableList<DataPoint> probeData = FXCollections.observableArrayList();
    private final ArrayList<ObservableList<DataPoint>> logDataList = new ArrayList<>();
    private final SerialPort probeDevice;

    public UsbDataProvider(ArrayList<Signal> signals, String probeDeviceName, Signal logSignal, List<String> logDeviceNames) {
        ObservableList<String> probeRawData = FXCollections.observableArrayList();
        probeDevice = getDeviceListener(probeDeviceName, probeRawData);
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

        for(String logDevice : logDeviceNames){
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

    private SerialPort getDeviceListener(String device, ObservableList<String> output){
        System.out.println("Creating listener for " + device);
        Optional<SerialPort> serialPort = Arrays.stream(SerialPort.getCommPorts()).filter(port -> ("/dev/" + port.getSystemPortName()).equals(device)).findAny();

        if(serialPort.isPresent()){
            try {
                serialPort.get().setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                serialPort.get().setBaudRate(9600);
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
            probeDevice.getOutputStream().write(msg.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

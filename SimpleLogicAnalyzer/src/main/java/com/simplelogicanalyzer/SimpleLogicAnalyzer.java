package com.simplelogicanalyzer;

import com.fasterxml.jackson.core.JsonParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.fazecast.jSerialComm.SerialPort;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SimpleLogicAnalyzer extends Application {
    public static ObservableList<String> rawProbeData;

    public static Scene scene;
    public static VBox mainVBox;
    private static final int DATA_POINTS_IN_VIEW = 50;

    @Override
    public void start(Stage stage) throws IOException {
        mainVBox = new VBox();

        ObjectMapper mapper = new ObjectMapper();
        InputStream propertiesInputStream = JsonParser.class.getClassLoader().getResourceAsStream("analyzer-properties.json");
        JsonData data = mapper.readValue(propertiesInputStream, JsonData.class);

        ArrayList<Signal> signals = new ArrayList<>();
        for(String s: data.getSignals()){
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            NumberAxis xAxis = new NumberAxis();
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(series.getData().size() - DATA_POINTS_IN_VIEW);
            xAxis.setUpperBound(series.getData().size() - 1);

            NumberAxis yAxis = new NumberAxis();
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(0);
            yAxis.setUpperBound(1.5);

            LineChart<Number,Number> lineChart = new LineChart<>(xAxis, yAxis, FXCollections.observableArrayList(series));
            lineChart.setTitle(s);
            lineChart.setAnimated(false);

            lineChart.addEventFilter(ScrollEvent.ANY, (event) -> {
                if (event.getDeltaY() != 0) {
                    signals.forEach(i -> i.scrollOffset.set(i.scrollOffset.get() + (event.getDeltaY() > 0 ? 1 : -1)));
                }
            });

            mainVBox.getChildren().add(lineChart);
            signals.add(new Signal(s, lineChart, series, xAxis));
        }

        rawProbeData = FXCollections.observableArrayList();
        rawProbeData.addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(Change<? extends String> change) {
                change.next();
                String newData = change.getAddedSubList().get(0);
                String[] data = newData.split(" ");
                Platform.runLater(() -> {
                    for(int i = 0; i < data.length; i++){
                        Signal signal = signals.get(i);
                        signal.series.getData().add(new XYChart.Data<>(
                                signal.series.getData().size(),
                                Integer.parseInt(data[i])
                        ));
                    }
                });
            }
        });

        System.out.println("Available ports:");
        for(SerialPort serial:SerialPort.getCommPorts()){
            System.out.println(serial.getSystemPortName() + ", " + serial.getDescriptivePortName() + ", " + serial.getProductID());

            if(serial.getProductID() == data.getLogicProbeProductID() || serial.getProductID() == data.getUsbUartProductID()){
                serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                serial.setBaudRate(9600);
                serial.openPort();
                serial.getInputStream().skip(serial.bytesAvailable());

                if(serial.getProductID() == data.getLogicProbeProductID()){
                    serial.addDataListener(new SerialPortDataListenerImpl(rawProbeData));
                }
            }
        }

        scene  = new Scene(mainVBox,800,600);
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
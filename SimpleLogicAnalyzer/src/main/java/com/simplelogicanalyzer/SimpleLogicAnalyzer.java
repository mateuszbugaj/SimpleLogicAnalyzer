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
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    boolean collectingData = false;
    public SerialPort probeSerial;

    @Override
    public void start(Stage stage) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream propertiesInputStream = JsonParser.class.getClassLoader().getResourceAsStream("analyzer-properties.json");
        JsonData configData = mapper.readValue(propertiesInputStream, JsonData.class);

        BorderPane layout = new BorderPane();
        ToolBar toolBar = new ToolBar();
        layout.setTop(toolBar);

        VBox mainVBox = new VBox();
        layout.setCenter(mainVBox);

        Button startButton = new Button("START");
        toolBar.getItems().add(startButton);

        ArrayList<Signal> signals = new ArrayList<>();
        for(int i = 0; i < configData.getSignals().size(); i++){
            String s = configData.getSignals().get(i);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();

            NumberAxis xAxis = new NumberAxis();
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(series.getData().size() - 150);
            xAxis.setUpperBound(series.getData().size() - 1);

            NumberAxis yAxis = new NumberAxis();
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(-0.1);
            yAxis.setUpperBound(1.1);
            yAxis.setTickLabelsVisible(false);
            yAxis.setLabel(s);

            LineChart<Number,Number> lineChart = new LineChart<>(xAxis, yAxis, FXCollections.observableArrayList(series));
            lineChart.setAnimated(false);
            lineChart.setLegendVisible(false);
            lineChart.setFocusTraversable(true);
            lineChart.setCreateSymbols(false);
            lineChart.setPadding(new Insets(0, 0, 0, 0));
            lineChart.setMaxHeight(80);

            if(i%2==0){
                series.getNode().setStyle("-fx-stroke:"+ "red" +";");
            } else {
                series.getNode().setStyle("-fx-stroke:"+ "blue" +";");
            }

            // Remove tick labels for every chart but the last one
            if(i != configData.getSignals().size() - 1){
                xAxis.setTickLabelsVisible(false);
                xAxis.setOpacity(0);
            }

            mainVBox.getChildren().add(lineChart);
            signals.add(new Signal(s, lineChart, series, xAxis));
        }

        rawProbeData = FXCollections.observableArrayList();
        rawProbeData.addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(Change<? extends String> change) {
                change.next();

                String newData = change.getAddedSubList().get(0);
                String[] dataSplit = newData.split(" ");
                Platform.runLater(() -> {
                    for(int i = 0; i < dataSplit.length; i++){
                        if(i > configData.getSignals().size()){
                            break;
                        }

                        if(collectingData){
                            Signal signal = signals.get(i);
                            signal.series.getData().add(new XYChart.Data<>(
                                    signal.series.getData().size(),
                                    Integer.parseInt(dataSplit[i])
                            ));
                        }
                    }
                });
            }
        });

        System.out.println("Available ports:");
        for(SerialPort serial:SerialPort.getCommPorts()){
            System.out.println(serial.getSystemPortName() + ", " + serial.getDescriptivePortName() + ", " + serial.getProductID());

            if(serial.getProductID() == configData.getLogicProbeProductID() || serial.getProductID() == configData.getUsbUartProductID()){
                serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                serial.setBaudRate(9600);
                serial.openPort();
                serial.getInputStream().skip(serial.bytesAvailable());

                if(serial.getProductID() == configData.getLogicProbeProductID()){
                    probeSerial = serial;
                    probeSerial.addDataListener(new SerialPortDataListenerImpl(rawProbeData));
                }
            }
        }

        startButton.setOnAction(event -> {
            try {
                if (collectingData) {
                    collectingData = false;
                    startButton.setText("START");
                    probeSerial.getOutputStream().write("stop".getBytes());
                } else {
                    collectingData = true;
                    startButton.setText("STOP");
                    probeSerial.getOutputStream().write("start".getBytes());
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        });

        scene  = new Scene(layout,800,600);
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();

        scene.addEventFilter(ScrollEvent.ANY, event -> {
            if(event.getDeltaX() != 0){
                signals.forEach(signal -> signal.zoom(event.getDeltaX()));
            }

            if(event.getDeltaY() != 0){
                signals.forEach(signal -> signal.scroll(event.getDeltaY()));
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
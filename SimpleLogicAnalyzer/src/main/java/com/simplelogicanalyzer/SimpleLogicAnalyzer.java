package com.simplelogicanalyzer;

import com.fasterxml.jackson.core.JsonParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.fazecast.jSerialComm.SerialPort;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SimpleLogicAnalyzer extends Application {
    public static ObservableList<String> rawProbeData;
    public static ObservableList<String> rawLogData;

    public static Scene scene;
    boolean collectingData = false;
    public SerialPort probeSerial;
    public ArrayList<SerialPort> logSerial = new ArrayList<>();

    @Override
    public void start(Stage stage) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream propertiesInputStream = JsonParser.class.getClassLoader().getResourceAsStream("analyzer-properties.json");
        JsonData configData = mapper.readValue(propertiesInputStream, JsonData.class);

        BorderPane layout = new BorderPane();
        ToolBar toolBar = new ToolBar();
        layout.setTop(toolBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.maxWidth(Double.MAX_VALUE);
        scrollPane.prefWidth(Double.MAX_VALUE);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Disable scrolling on the scroll pane
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if(event.getDeltaY() != 0 || event.getDeltaX() != 0) {
                event.consume();
            }
        });

        layout.setCenter(scrollPane);

        VBox chartVBox = new VBox();
        scrollPane.setContent(chartVBox);
        chartVBox.prefWidthProperty().bind(scrollPane.widthProperty());

        Button startButton = new Button("START");
        toolBar.getItems().add(startButton);

        ArrayList<Signal> signals = new ArrayList<>();
        for(int i = 0; i < configData.getSignals().size(); i++){
            String s = configData.getSignals().get(i);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();

            NumberAxis xAxis = new NumberAxis();
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel(s);

            LineChart<Number,Number> lineChart = new LineChart<>(xAxis, yAxis, FXCollections.observableArrayList(series));
            configureChart(lineChart, xAxis, yAxis);
            lineChart.setCreateSymbols(false);
            lineChart.prefWidthProperty().bind(layout.widthProperty());
            lineChart.setMaxHeight(80);

            if(i%2==0){
                series.getNode().setStyle("-fx-stroke:"+ "red" +";");
            } else {
                series.getNode().setStyle("-fx-stroke:"+ "blue" +";");
            }

            chartVBox.getChildren().add(lineChart);
            signals.add(new Signal(s, lineChart, series, xAxis));
        }

        // Create log panel
        NumberAxis xAxis = new NumberAxis();
        XYChart.Series<Number, Number> series = new XYChart.Series<>();

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Log data");

        ScatterChart<Number,Number> logPanel = new ScatterChart<>(xAxis, yAxis, FXCollections.observableArrayList(series));
        configureChart(logPanel, xAxis, yAxis);

        logPanel.setMaxHeight(300);
        logPanel.setPrefHeight(300);
        logPanel.prefWidthProperty().bind(layout.widthProperty());
        signals.add(new Signal("Log Panel", logPanel, series, xAxis, signals.get(0).series));
        layout.setBottom(logPanel);

        rawProbeData = FXCollections.observableArrayList();
        rawProbeData.addListener(new ListChangeListener<String>() {
            @Override
            public void onChanged(Change<? extends String> change) {
                change.next();

                String newData = change.getAddedSubList().get(0);
                String[] dataSplit = newData.split(" ");
                Platform.runLater(() -> {
                    for(int i = 0; i < dataSplit.length; i++){
                        if(i > configData.getSignals().size()) break;

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

        rawLogData = FXCollections.observableArrayList();
        rawLogData.addListener(new ListChangeListener<String>() {
            double y = 0.9;
            @Override
            public void onChanged(Change<? extends String> change) {
                change.next();

                String newData = change.getAddedSubList().get(0);
                Platform.runLater(() -> {
                    if(collectingData){
                        Optional<Signal> signal = signals.stream().filter(s -> s.name.equals("Log Panel")).findAny();
                        if(signal.isPresent()){
                            var data = new XYChart.Data<Number, Number>(signals.get(0).series.getData().size(), y);
                            data.setNode(createDataNode(data.YValueProperty(), newData));
                            signal.get().series.getData().add(data);

                            y += -0.1;
                            if(y < 0.2) y = 0.9;
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
                    serial.addDataListener(new SerialPortDataListenerImpl(rawProbeData));
                    probeSerial = serial;
                }

                if(serial.getProductID() == configData.getUsbUartProductID()){
                    serial.addDataListener(new SerialPortDataListenerImpl(rawLogData));
                    logSerial.add(serial);
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

    private static void configureChart(Chart chart, NumberAxis xAxis, NumberAxis yAxis){
        xAxis.setAutoRanging(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setOpacity(0);

        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(-0.1);
        yAxis.setUpperBound(1.1);
        yAxis.setTickLabelsVisible(false);

        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setFocusTraversable(true);
        chart.setPadding(new Insets(0, 0, 0, 0));
    }

    private static Node createDataNode(ObjectExpression<Number> value, String labelText) {
        var label = new Label(labelText);

        var pane = new Pane(label);
        pane.setShape(new Circle(4.0));
        pane.setScaleShape(false);

        label.setTextAlignment(TextAlignment.CENTER);
        label.setTranslateY(5);
        label.translateXProperty().bind(label.widthProperty().divide(2).add(20));

        return pane;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
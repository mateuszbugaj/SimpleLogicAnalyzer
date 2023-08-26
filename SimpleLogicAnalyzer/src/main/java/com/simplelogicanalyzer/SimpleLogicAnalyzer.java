package com.simplelogicanalyzer;

import com.fasterxml.jackson.core.JsonParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

import com.fazecast.jSerialComm.SerialPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.util.Callback;

public class SimpleLogicAnalyzer extends Application {

    public static Scene scene;
    SimpleBooleanProperty collectingData = new SimpleBooleanProperty(false);
    SerialPort probeSerial;
    ArrayList<SerialPort> logSerial = new ArrayList<>();
    SimpleBooleanProperty showingLogs = new SimpleBooleanProperty(false);
    SimpleLongProperty timestamp = new SimpleLongProperty();

    @Override
    public void start(Stage stage) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream propertiesInputStream = JsonParser.class.getClassLoader().getResourceAsStream("analyzer-properties.json");
        JsonData configData = mapper.readValue(propertiesInputStream, JsonData.class);

        BorderPane mainLayout = new BorderPane(); // Positions toolbar at the top, center layout at center and logsListView at the right
        BorderPane centerLayout = new BorderPane(); // Positions signal charts scroll pane at center and 
        mainLayout.setCenter(centerLayout);
        
        ScrollPane signalChartsScrollPane = new ScrollPane();
        signalChartsScrollPane.maxWidth(Double.MAX_VALUE);
        signalChartsScrollPane.prefWidth(Double.MAX_VALUE);
        signalChartsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Disable scrolling on the scroll pane
        signalChartsScrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if(event.getDeltaY() != 0 || event.getDeltaX() != 0) {
                event.consume();
            }
        });
        
        centerLayout.setCenter(signalChartsScrollPane);

        // Create log list view
        ListView<DataPoint> logsListView = new ListView<>();
        logsListView.setPrefWidth(400);
        logsListView.visibleProperty().bind(showingLogs);
        logsListView.managedProperty().bind(showingLogs);

        logsListView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if(event.getDeltaY() != 0 || event.getDeltaX() != 0) {
                event.consume();
            }
        });

        logsListView.setCellFactory(new Callback<>() {
            @Override
            public ListCell<DataPoint> call(ListView<DataPoint> dataPointListView) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(DataPoint dataPoint, boolean empty) {
                        super.updateItem(dataPoint, empty);
                        if (dataPoint != null && !empty) {
                            SimpleDateFormat sdf = new SimpleDateFormat("[ss.SSS] ");
                            long timeDelta = dataPoint.timestamp - timestamp.get();
                            String formattedDate = sdf.format(new Date(timeDelta));
                            setText(formattedDate + dataPoint.content);
                        } else {
                            setText(null);
                        }
                    }
                };
            }
        });

        mainLayout.setRight(logsListView);

        // Create signal charts
        VBox signalCHartsVBox = new VBox();
        signalChartsScrollPane.setContent(signalCHartsVBox);
        signalCHartsVBox.prefWidthProperty().bind(signalChartsScrollPane.widthProperty());

        ArrayList<Signal> signals = new ArrayList<>();
        for(int signalIndex = 0; signalIndex < configData.getSignals().size(); signalIndex++){
            String signalName = configData.getSignals().get(signalIndex);
            XYChart.Series<Number, Number> signalChartDataSeries = new XYChart.Series<>();

            NumberAxis signalChartXAxis = new NumberAxis();
            signalChartXAxis.setTickLabelsVisible(false);
            signalChartXAxis.setOpacity(0);
            NumberAxis signalChartYAxis = new NumberAxis();
            signalChartYAxis.setLabel(signalName);

            LineChart<Number,Number> signalChart = new LineChart<>(signalChartXAxis, signalChartYAxis, FXCollections.observableArrayList(signalChartDataSeries));
            configureChart(signalChart, signalChartXAxis, signalChartYAxis);
            signalChart.setCreateSymbols(false);
            signalChart.prefWidthProperty().bind(mainLayout.widthProperty());
            signalChart.setPrefHeight(50);

            if(signalIndex%2==0){
                signalChartDataSeries.getNode().setStyle("-fx-stroke:"+ "red" +";");
            } else {
                signalChartDataSeries.getNode().setStyle("-fx-stroke:"+ "blue" +";");
            }

            signalCHartsVBox.getChildren().add(signalChart);
            signals.add(new Signal(signalName, signalChart, signalChartDataSeries, signalChartXAxis));
        }

        // Create log panel
        ObservableList<DataPoint> rawProbeData = FXCollections.observableArrayList();
        ArrayList<ObservableList<DataPoint>> rawLogData = new ArrayList<>();

        NumberAxis logPanelXAxis = new NumberAxis();
        NumberAxis logPanelYAxis = new NumberAxis();
        logPanelYAxis.setLabel("Log data");

        XYChart.Series<Number, Number> logPanelDataSeries = new XYChart.Series<>();
        ScatterChart<Number,Number> logPanelChart = new ScatterChart<>(logPanelXAxis, logPanelYAxis, FXCollections.observableArrayList(logPanelDataSeries));
        configureChart(logPanelChart, logPanelXAxis, logPanelYAxis);
        logPanelChart.setMaxHeight(300);
        logPanelChart.setPrefHeight(300);
        logPanelChart.prefWidthProperty().bind(mainLayout.widthProperty());
        signals.add(new Signal("Log Panel", logPanelChart, logPanelDataSeries, logPanelXAxis, signals.get(0).series));
        centerLayout.setBottom(logPanelChart);

        // Configure data ports
        BorderPane toolbarLayout = new BorderPane();
        mainLayout.setTop(toolbarLayout);

        HBox logPortsButtons = new HBox();
        logPortsButtons.setSpacing(10);
        logPortsButtons.setPadding(new Insets(5));
        toolbarLayout.setRight(logPortsButtons);
        
        System.out.println("Available ports:");
        for(SerialPort serial:SerialPort.getCommPorts()){
            System.out.println(serial.getSystemPortName() + ", " + serial.getDescriptivePortName() + ", " + serial.getProductID());

            if(serial.getProductID() == configData.getLogicProbeProductID() || serial.getProductID() == configData.getUsbUartProductID()){
                serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                serial.setBaudRate(9600);
                serial.openPort();
                serial.getInputStream().skip(serial.bytesAvailable());

                if(serial.getProductID() == configData.getLogicProbeProductID()){
                    rawProbeData.addListener(new ProbeDataListener(configData, collectingData, signals));
                    serial.addDataListener(new SerialPortDataListenerImpl(rawProbeData, collectingData));
                    probeSerial = serial;
                }

                if(serial.getProductID() == configData.getUsbUartProductID()){
                    ObservableList<DataPoint> observableList = FXCollections.observableArrayList();
                    rawLogData.add(observableList);
                    serial.addDataListener(new SerialPortDataListenerImpl(observableList, collectingData));
                    observableList.addListener(new LogDataListener(collectingData, signals));
                    logSerial.add(serial);

                    Button usartButton = new Button(serial.getSystemPortName());
                    usartButton.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent actionEvent) {
                            Platform.runLater(() -> {
                                if(showingLogs.get()){
                                    if(logsListView.getItems().equals(observableList)){
                                        usartButton.setBorder(null);
                                        showingLogs.set(false);
                                    } else {
                                        logsListView.setItems(observableList);
                                    }
                                } else {
                                    showingLogs.set(true);
                                    logsListView.setItems(observableList);
                                }
                            });
                        }
                    });

                    logPortsButtons.getChildren().add(usartButton);
                }
            }
        }

        Button startButton = new Button("START");
        Button pauseButton = new Button("PAUSE");
        Button clearButton = new Button("CLEAR");

        HBox toolbarLeftHBox = new HBox(startButton, pauseButton, clearButton);
        toolbarLeftHBox.setSpacing(10);
        toolbarLeftHBox.setPadding(new Insets(5));
        toolbarLayout.setLeft(toolbarLeftHBox);
        
        startButton.setOnAction(event -> {
            try {
                if (collectingData.get()) {
                    collectingData.set(false);
                    startButton.setText("START");
                    probeSerial.getOutputStream().write("stop".getBytes());
                } else {
                    collectingData.set(true);
                    timestamp.set(System.currentTimeMillis());
                    startButton.setText("STOP");
                    probeSerial.getOutputStream().write("start".getBytes());
                }
            } catch (IOException e){
                e.printStackTrace();
            }
        });

        pauseButton.setOnAction(actionEvent -> {
            if (pauseButton.getText().equals("PAUSE")){
                pauseButton.setText("UNPAUSE");
                signals.forEach(signal -> signal.pause(true));
            } else {
                pauseButton.setText("PAUSE");
                signals.forEach(signal -> signal.pause(false));
            }
        });

        clearButton.setOnAction(actionEvent -> {
            timestamp.set(System.currentTimeMillis());
            signals.forEach(Signal::clear);
            rawProbeData.clear();
            rawLogData.forEach(List::clear);
        });

        scene  = new Scene(mainLayout,800,600);
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

    private void configureChart(Chart chart, NumberAxis xAxis, NumberAxis yAxis){
        xAxis.setAutoRanging(false);

        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(-0.1);
        yAxis.setUpperBound(1.1);
        yAxis.setTickLabelsVisible(false);

        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setFocusTraversable(true);
        chart.setPadding(new Insets(0, 0, 0, 0));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
package com.simplelogicanalyzer;

import com.fasterxml.jackson.core.JsonParser;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

import com.fazecast.jSerialComm.SerialPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.util.Callback;

public class SimpleLogicAnalyzer extends Application {

    public static Scene scene;
    SimpleBooleanProperty collectingData = new SimpleBooleanProperty(false);
    boolean readingFromUSB = true;
    SerialPort probeSerial;
    ArrayList<SerialPort> logSerial = new ArrayList<>();
    SimpleBooleanProperty showingLogs = new SimpleBooleanProperty(false);
    SimpleLongProperty timestamp = new SimpleLongProperty();

    @Override
    public void start(Stage stage) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream propertiesInputStream = JsonParser.class.getClassLoader().getResourceAsStream(getParameters().getRaw().get(0));
        JsonData configData = mapper.readValue(propertiesInputStream, JsonData.class);

        BorderPane mainLayout = new BorderPane(); // Positions toolbar at the top, center layout at center and logsListView at the right
        BorderPane centerLayout = new BorderPane(); // Positions signal charts scroll pane at center
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

        logsListView.setOnMouseClicked(event -> {
            DataPoint selectedDataPoint = logsListView.getSelectionModel().getSelectedItem();
            if (selectedDataPoint != null) {
                for (XYChart.Data<Number, Number> data : logPanelChart.getData().get(0).getData()) {
                    Node node = data.getNode();
                    if (node instanceof Pane) {
                        Label label = (Label) ((Pane) node).getChildren().get(0);
                        label.setStyle("-fx-text-fill: black;");

                        if (data.getXValue().equals(selectedDataPoint.chartXPosition) && label.getText().equals(selectedDataPoint.content)) {
                            label.setStyle("-fx-text-fill: red;");

                            // Add the vertical line to each signal chart
                            for (Signal signal : signals) {
                                LineChart<Number, Number> signalChart;
                                try {
                                    signalChart = (LineChart<Number, Number>) signal.lineChart;
                                    addVerticalLineToChart(signalChart, selectedDataPoint.chartXPosition);
                                } catch (ClassCastException e){}

                            }

                            addVerticalLineToChart(logPanelChart, selectedDataPoint.chartXPosition);
                        }
                    }
                }
            }
        });

        // Configure data ports
        BorderPane toolbarLayout = new BorderPane();
        mainLayout.setTop(toolbarLayout);

        HBox logPortsButtons = new HBox();
        logPortsButtons.setSpacing(10);
        logPortsButtons.setPadding(new Insets(5));
        toolbarLayout.setRight(logPortsButtons);

        if(configData.getLogicProbe().contains(".txt")){
            // Read data from file
            Path filePath = Paths.get(configData.getLogicProbe());
            try (Stream<String> stream = Files.lines(filePath)) {
                stream.forEach(newLine -> {
                    String[] messageSplit = newLine.strip().split(" ");

                    Platform.runLater(() -> {
                        for(int i = 0; i < messageSplit.length; i++){
                            if(i > configData.getSignals().size()) break;
            
                            Signal signal = signals.get(i);
                            try{
                                signal.series.getData().add(new XYChart.Data<>(
                                        signal.series.getData().size(),
                                        Integer.parseInt(messageSplit[i])
                                ));
                            } catch (NumberFormatException e){
                                break;
                            }
                        }
                    });
                });
            }

            configureFileDataListeners(configData, rawProbeData, signals);
        } else {
            List<Button> usartButtons = configureSerialDataListeners(configData, rawProbeData, signals, rawLogData, logsListView);
            logPortsButtons.getChildren().addAll(usartButtons);
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
                    if(readingFromUSB) probeSerial.getOutputStream().write("stop".getBytes());
                } else {
                    collectingData.set(true);
                    timestamp.set(System.currentTimeMillis());
                    startButton.setText("STOP");
                    if(readingFromUSB) probeSerial.getOutputStream().write("start".getBytes());
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

        // Create selectable list of signals to show
        HBox signalCheckBoxes = new HBox();
        signalCheckBoxes.setSpacing(10);
        signalCheckBoxes.setPadding(new Insets(8, 10, 0, 10));

        for (Signal signal : signals) {
            CheckBox signalCheckBox = new CheckBox(signal.name);
            signalCheckBox.setSelected(true); // default all signals to be selected
            signalCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                signal.lineChart.visibleProperty().bind(signalCheckBox.selectedProperty());
                signal.lineChart.managedProperty().bind(signalCheckBox.selectedProperty());
            });
            signalCheckBoxes.getChildren().add(signalCheckBox);
        }

        toolbarLayout.setCenter(signalCheckBoxes);

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

    private void configureFileDataListeners(JsonData configData, ObservableList<DataPoint> rawProbeData, ArrayList<Signal> signals) throws IOException{
        readingFromUSB = false;
        Path filePath = Paths.get(configData.getLogicProbe());

        WatchService service = FileSystems.getDefault().newWatchService();
        filePath.getParent().register(service, java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY);
        Thread thread = new Thread(() -> {
            while(true){
                try {
                    WatchKey key = service.take();
                    for(WatchEvent<?> event : key.pollEvents()){
                        if(filePath.getFileName().equals(event.context())){
                            // File has changed, get the last line
                            RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
                            long length = raf.length() - 1;
                            StringBuilder sb = new StringBuilder();

                            for (long pointer = length; pointer >= 0; pointer--) {
                                raf.seek(pointer);
                                char c = (char) raf.read();
                                if (c == '\n' && pointer < length) {
                                    break;
                                }
                                sb.append(c);
                            }

                            String newLine = sb.reverse().toString().strip();
                            String[] messageSplit = newLine.split(" ");

                            Platform.runLater(() -> {
                                for(int i = 0; i < messageSplit.length; i++){
                                    if(i > configData.getSignals().size()) break;
                    
                                    if(collectingData.get()){
                                        Signal signal = signals.get(i);
                                        try{
                                            signal.series.getData().add(new XYChart.Data<>(
                                                    signal.series.getData().size(),
                                                    Integer.parseInt(messageSplit[i])
                                            ));
                                        } catch (NumberFormatException e){
                                            break;
                                        }
                                    }
                                }
                            });
                        }
                    }
                    key.reset();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        rawProbeData.addListener(new ProbeDataListener(configData, collectingData, signals));
    }

    private List<Button> configureSerialDataListeners(JsonData configData, ObservableList<DataPoint> rawProbeData, ArrayList<Signal> signals, ArrayList<ObservableList<DataPoint>> rawLogData, ListView<DataPoint> logsListView) throws IOException {
        System.out.println("Logic probe: " + configData.getLogicProbe());
        System.out.println("Logging probe: " + configData.getLoggingProbe());
        System.out.println("Available ports:");

        List<Button> usartButtons = new ArrayList<>();
        for(SerialPort serial:SerialPort.getCommPorts()){
            System.out.println(serial.getSystemPortName() + ", " + serial.getDescriptivePortName() + ", " + serial.getProductID() + ", " + serial.getSystemPortPath());

            if(serial.getSystemPortPath().equals(configData.getLogicProbe()) || configData.getLoggingProbe().contains(serial.getSystemPortPath())){
                serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                serial.setBaudRate(9600);
                serial.openPort();
                serial.getInputStream().skip(serial.bytesAvailable());

                if(serial.getSystemPortPath().equals(configData.getLogicProbe())){
                    System.out.println("Logic probe found");
                    rawProbeData.addListener(new ProbeDataListener(configData, collectingData, signals));
                    serial.addDataListener(new SerialPortDataListenerImpl(rawProbeData, collectingData));
                    probeSerial = serial;
                    continue;
                }

                if(configData.getLoggingProbe().contains(serial.getSystemPortPath())){
                    System.out.println("Logging probe found");
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
                    usartButtons.add(usartButton);
                    continue;
                }
            }
        }

        return usartButtons;
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

    private void addVerticalLineToChart(XYChart<Number, Number> chart, double xValue) {
        XYChart.Data<Number, Number> start = new XYChart.Data<>(xValue, -1);
        XYChart.Data<Number, Number> end = new XYChart.Data<>(xValue, 2);

        XYChart.Series<Number, Number> verticalLineSeries = new XYChart.Series<>();
        verticalLineSeries.getData().addAll(start, end);

        // Remove the previously added series (if it exists)
        List<XYChart.Series<Number, Number>> toRemove = new ArrayList<>();
        for (XYChart.Series<Number, Number> series : chart.getData()) {
            if ("verticalLine".equals(series.getName())) {
                toRemove.add(series);
            }
        }
        chart.getData().removeAll(toRemove);

        verticalLineSeries.setName("verticalLine");

        verticalLineSeries.nodeProperty().addListener(new ChangeListener<Node>() {
            @Override
            public void changed(ObservableValue<? extends Node> observable, Node oldValue, Node newValue) {
                if (newValue != null) {
                    newValue.setStyle("-fx-stroke:"+ "black" +";");
                    verticalLineSeries.nodeProperty().removeListener(this);  // remove the listener to avoid repeated calls
                }
            }
        });

        chart.getData().add(verticalLineSeries);
    }


    public static void main(String[] args) {
        launch(args);
    }
}
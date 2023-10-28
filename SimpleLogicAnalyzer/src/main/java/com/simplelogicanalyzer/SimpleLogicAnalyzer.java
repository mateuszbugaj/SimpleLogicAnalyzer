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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import com.fazecast.jSerialComm.SerialPort;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SimpleLogicAnalyzer extends Application {

    private final SimpleBooleanProperty collectingData = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty showingLogs = new SimpleBooleanProperty(false);
    private final SimpleLongProperty timestamp = new SimpleLongProperty();
    private ConfigurationData configData;
    private final ArrayList<Signal> signals = new ArrayList<>();
    private Signal logSignal;
    private DataProvider dataProvider;

    @Override
    public void start(Stage stage) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream propertiesInputStream = JsonParser.class.getClassLoader().getResourceAsStream(getParameters().getRaw().get(0));
        if(propertiesInputStream == null){
            System.out.println("Analyzer properties file " + getParameters().getRaw().get(0) + " not found.");
            System.exit(0);
        }

        configData = mapper.readValue(propertiesInputStream, ConfigurationData.class);

        BorderPane mainLayout = new BorderPane();
        BorderPane centerLayout = new BorderPane();
        mainLayout.setCenter(centerLayout);

        ScrollPane signalChartsScrollPane = createSignalChartsScrollPane();
        centerLayout.setCenter(signalChartsScrollPane);

        VBox signalCharts = createSignalCharts(signalChartsScrollPane);
        signalChartsScrollPane.setContent(signalCharts);

        ScatterChart<Number, Number> logPanelChart = createLogPanel();
        centerLayout.setBottom(logPanelChart);

        DataProviderBuilder dataProviderBuilder = new DataProviderBuilder(configData, signals, logSignal);
        dataProvider = dataProviderBuilder.getDataProvider();

        ListView<DataPoint> logsListView = createLogList();
        mainLayout.setRight(logsListView);

        BorderPane toolbarLayout = createToolbar();
        mainLayout.setTop(toolbarLayout);

        HBox logButtons = new HBox();
        logButtons.setSpacing(10);
        logButtons.setPadding(new Insets(5));
        for(int i = 0; i < dataProvider.getLogDataList().size(); i++){
            ObservableList<DataPoint> logList = dataProvider.getLogDataList().get(i);
            Button showLogButton = new Button(configData.getLoggingProbe().get(i));
            showLogButton.setOnAction(new EventHandler<ActionEvent>() {
                @Override
                public void handle(ActionEvent actionEvent) {
                    if(showingLogs.get()){
                        if(logsListView.getItems().equals(logList)){
                            showLogButton.setBorder(null);
                            showingLogs.set(false);
                        } else {
                            logsListView.setItems(logList);
                        }
                    } else {
                        showingLogs.set(true);
                        logsListView.setItems(logList);
                    }
                }
            });

            logButtons.getChildren().addAll(showLogButton);
        }
        toolbarLayout.setRight(logButtons);

        Scene scene = new Scene(mainLayout,800,600);
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();

        scene.addEventFilter(ScrollEvent.ANY, event -> {
            if(event.getDeltaX() != 0){
                signals.forEach(signal -> signal.zoom(event.getDeltaX()));
                logSignal.zoom(event.getDeltaX());
            }

            if(event.getDeltaY() != 0){
                signals.forEach(signal -> signal.scroll(event.getDeltaY()));
                logSignal.scroll(event.getDeltaY());
            }
        });
    }

    private ScrollPane createSignalChartsScrollPane(){
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

        return scrollPane;
    }

    private VBox createSignalCharts(ScrollPane parent){
        VBox signalCHartsVBox = new VBox();
        signalCHartsVBox.prefWidthProperty().bind(parent.widthProperty());

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
            signalChart.setPrefHeight(50);

            if(signalIndex%2==0){
                signalChartDataSeries.getNode().setStyle("-fx-stroke:"+ "red" +";");
            } else {
                signalChartDataSeries.getNode().setStyle("-fx-stroke:"+ "blue" +";");
            }

            signalCHartsVBox.getChildren().add(signalChart);
            signals.add(new Signal(signalName, signalChart, signalChartDataSeries, signalChartXAxis));
        }

        return signalCHartsVBox;
    }

    private ScatterChart<Number,Number> createLogPanel(){
        NumberAxis logPanelXAxis = new NumberAxis();
        NumberAxis logPanelYAxis = new NumberAxis();
        logPanelYAxis.setLabel("Log data");

        XYChart.Series<Number, Number> logPanelDataSeries = new XYChart.Series<>();
        ScatterChart<Number,Number> logPanelChart = new ScatterChart<>(logPanelXAxis, logPanelYAxis, FXCollections.observableArrayList(logPanelDataSeries));
        configureChart(logPanelChart, logPanelXAxis, logPanelYAxis);
        logPanelChart.setMaxHeight(300);
        logPanelChart.setPrefHeight(300);
        logSignal = new Signal("Log Panel", logPanelChart, logPanelDataSeries, logPanelXAxis, signals.get(0).series);

        return logPanelChart;
    }

    private ListView<DataPoint> createLogList(){
        ListView<DataPoint> logsListView = new ListView<>();
        logsListView.setPrefWidth(400);
        logsListView.visibleProperty().bind(showingLogs);
        logsListView.managedProperty().bind(showingLogs);

        logsListView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if(event.getDeltaY() != 0 || event.getDeltaX() != 0) {
                event.consume();
            }
        });

        logsListView.setCellFactory(new LogsListViewCellFactory(timestamp));

        logsListView.setOnMouseClicked(event -> {
            DataPoint selectedDataPoint = logsListView.getSelectionModel().getSelectedItem();
            if (selectedDataPoint != null) {
                for (XYChart.Data<Number, Number> data : logSignal.series.getData()) {
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
                                    } catch (ClassCastException ignored){}

                                }

                                addVerticalLineToChart(logSignal.series.getChart(), selectedDataPoint.chartXPosition);
                            }
                        }
                    }
            }
        });

        return logsListView;
    }

    private BorderPane createToolbar(){
        BorderPane toolbarLayout = new BorderPane();

        Button startButton = new Button("START");
        Button pauseButton = new Button("PAUSE");
        Button clearButton = new Button("CLEAR");

        HBox toolbarLeftHBox = new HBox(startButton, pauseButton, clearButton);
        toolbarLeftHBox.setSpacing(10);
        toolbarLeftHBox.setPadding(new Insets(5));
        toolbarLayout.setLeft(toolbarLeftHBox);

        startButton.setOnAction(event -> {
            if (collectingData.get()) {
                collectingData.set(false);
                startButton.setText("START");
                dataProvider.send("stop");
            } else {
                collectingData.set(true);
                timestamp.set(System.currentTimeMillis());
                startButton.setText("STOP");
                dataProvider.send("start");
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

        return toolbarLayout;
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
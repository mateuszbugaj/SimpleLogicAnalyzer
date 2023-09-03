package com.simplelogicanalyzer;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.Optional;

public class LogDataListener implements ListChangeListener<DataPoint> {
    private final SimpleBooleanProperty collectingData;
    private final ArrayList<Signal> signals;
    private static int id = 0; // used for color-coding
    private final int instanceIndex;
    private static double y = 0.9;

    public LogDataListener(SimpleBooleanProperty collectingData, ArrayList<Signal> signals) {
        this.collectingData = collectingData;
        this.signals = signals;

        instanceIndex = id;
        id++;
    }

    @Override
    public void onChanged(Change<? extends DataPoint> change) {
        change.next();

        if(change.getAddedSubList().isEmpty()) return;

        DataPoint dataPoint = change.getAddedSubList().get(0);
        String newData = dataPoint.content;
        Platform.runLater(() -> {
            if(collectingData.get()){
                Optional<Signal> signal = signals.stream().filter(s -> s.name.equals("Log Panel")).findAny();
                if(signal.isPresent()){
                    var data = new XYChart.Data<Number, Number>(signals.get(0).series.getData().size(), y);
                    data.setNode(createDataNode(newData));
                    signal.get().series.getData().add(data);
                    dataPoint.chartXPosition = signals.get(0).series.getData().size();

                    y += -0.1;
                    if(y < 0.2) y = 0.9;
                }
            }
        });
    }

    private Node createDataNode(String labelText) {
        Label label = new Label(labelText);
        Pane pane = new Pane(label);
        Circle circle = new Circle(4.0);
        pane.setShape(circle);
        pane.setScaleShape(false);

        if(instanceIndex == 0) {
            pane.setStyle("-fx-background-color: purple;");
        } else if(instanceIndex == 1) {
            pane.setStyle("-fx-background-color: green;");
        }

        label.setTextAlignment(TextAlignment.CENTER);
        label.setTranslateY(5);
        label.translateXProperty().bind(label.widthProperty().divide(2).add(20));

        return pane;
    }
}

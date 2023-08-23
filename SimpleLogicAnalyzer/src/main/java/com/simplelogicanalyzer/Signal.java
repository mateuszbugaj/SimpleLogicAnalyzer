package com.simplelogicanalyzer;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class Signal {
    public String name;
    public LineChart<Number, Number> lineChart;
    public XYChart.Series<Number, Number> series;
    public SimpleIntegerProperty scrollOffset;
    public SimpleIntegerProperty zoom;

    public Signal(String name, LineChart<Number, Number> lineChart, XYChart.Series<Number, Number> series, NumberAxis xAxis) {
        this.name = name;
        this.lineChart = lineChart;
        this.series = series;
        zoom = new SimpleIntegerProperty();
        scrollOffset = new SimpleIntegerProperty();
        zoom.set(150);

        InvalidationListener changeXAxisBoundsListener = new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                xAxis.setLowerBound(series.getData().size() - zoom.get() + scrollOffset.get());
                xAxis.setUpperBound(series.getData().size() - 1 + scrollOffset.get());
            }
        };
        series.getData().addListener(changeXAxisBoundsListener);
        scrollOffset.addListener(changeXAxisBoundsListener);
        zoom.addListener(changeXAxisBoundsListener);
    }
}

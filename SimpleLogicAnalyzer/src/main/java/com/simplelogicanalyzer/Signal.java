package com.simplelogicanalyzer;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class Signal {
    public String name;
    public Chart lineChart;
    public XYChart.Series<Number, Number> series;
    public SimpleIntegerProperty scrollOffset;
    public SimpleIntegerProperty zoom;
    public XYChart.Series<Number, Number> referenceSeries;

    public Signal(String name, Chart lineChart, XYChart.Series<Number, Number> series, NumberAxis xAxis){
        this(name, lineChart, series, xAxis, series);
    }

    public Signal(String name, Chart lineChart, XYChart.Series<Number, Number> series, NumberAxis xAxis, XYChart.Series<Number, Number> referenceSeries) {
        this.name = name;
        this.lineChart = lineChart;
        this.series = series;
        zoom = new SimpleIntegerProperty();
        scrollOffset = new SimpleIntegerProperty();
        zoom.set(150);

        InvalidationListener changeXAxisBoundsListener = new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                xAxis.setLowerBound(referenceSeries.getData().size() - zoom.get() + scrollOffset.get());
                xAxis.setUpperBound(referenceSeries.getData().size() - 1 + scrollOffset.get());
            }
        };
        series.getData().addListener(changeXAxisBoundsListener);
        referenceSeries.getData().addListener(changeXAxisBoundsListener);
        scrollOffset.addListener(changeXAxisBoundsListener);
        zoom.addListener(changeXAxisBoundsListener);
    }

    public void zoom(double delta){
        int zoomSpeed = 5; // points per scroll
        zoom.set(zoom.get() + zoomSpeed * (delta > 0 ? -1 : 1));
    }

    public void scroll(double delta){
        int scrollSpeed = zoom.get() / 15;
        scrollOffset.set(scrollOffset.get() + scrollSpeed * (delta > 0 ? -1 : 1));
    }
}

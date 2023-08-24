package com.simplelogicanalyzer;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.chart.Chart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Paint;

public class Signal {
    public String name;
    public Chart lineChart;
    public XYChart.Series<Number, Number> series;
    public XYChart.Series<Number, Number> referenceSeries;
    public SimpleIntegerProperty scrollOffset;
    public SimpleIntegerProperty zoom;
    public InvalidationListener changeXAxisBoundsListener;

    public Signal(String name, Chart lineChart, XYChart.Series<Number, Number> series, NumberAxis xAxis){
        this(name, lineChart, series, xAxis, series);
    }

    public Signal(String name, Chart lineChart, XYChart.Series<Number, Number> series, NumberAxis xAxis, XYChart.Series<Number, Number> referenceSeries) {
        this.name = name;
        this.lineChart = lineChart;
        this.series = series;
        this.referenceSeries = referenceSeries;
        zoom = new SimpleIntegerProperty();
        scrollOffset = new SimpleIntegerProperty();
        zoom.set(150);

        changeXAxisBoundsListener = new InvalidationListener() {
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

    public void pause(boolean value){
        if (value == true){
            series.getData().removeListener(changeXAxisBoundsListener);
            referenceSeries.getData().removeListener(changeXAxisBoundsListener);
        } else {
            series.getData().addListener(changeXAxisBoundsListener);
            referenceSeries.getData().addListener(changeXAxisBoundsListener);
            scrollOffset.set(0);
        }
    }

    public void clear(){
        series.getData().clear();
        referenceSeries.getData().clear();
        scrollOffset.set(0);
        zoom.set(150);
    }

    public void addDataListener(ListChangeListener<String> dataListener, ObservableList<String> data){
        data.addListener(dataListener);
    }
}

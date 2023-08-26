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
import javafx.util.StringConverter;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Signal {
    public String name;
    public Chart lineChart;
    public XYChart.Series<Number, Number> series;
    public XYChart.Series<Number, Number> referenceSeries;
    public SimpleIntegerProperty scrollOffset;
    public SimpleIntegerProperty zoom;
    public InvalidationListener changeXAxisBoundsListener;
    public boolean pause;
    public int pauseAnchor;

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

        changeXAxisBoundsListener = new InvalidationListener() {
            @Override
            public void invalidated(Observable observable) {
                int pauseOffset = 0;
                if(pause){
                    pauseOffset = referenceSeries.getData().size() - pauseAnchor;
                }

                xAxis.setLowerBound(referenceSeries.getData().size() - zoom.get() + scrollOffset.get() - pauseOffset);
                xAxis.setUpperBound(referenceSeries.getData().size() + scrollOffset.get() - pauseOffset);
            }
        };

        series.getData().addListener(changeXAxisBoundsListener);
        referenceSeries.getData().addListener(changeXAxisBoundsListener);
        scrollOffset.addListener(changeXAxisBoundsListener);
        zoom.addListener(changeXAxisBoundsListener);
        zoom.set(300);
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
        pause = value;
        if(pause){
            pauseAnchor = referenceSeries.getData().size();
        }
    }

    public void clear(){
        series.getData().clear();
        referenceSeries.getData().clear();
        scrollOffset.set(0);
    }
}

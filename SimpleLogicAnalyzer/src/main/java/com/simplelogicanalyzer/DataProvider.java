package com.simplelogicanalyzer;

import javafx.collections.ObservableList;

import java.util.ArrayList;

public interface DataProvider {
    ArrayList<ObservableList<DataPoint>> getLogDataList();
    void sendProbe(String msg);
    void sendLogging(String msg, String target);
    void clear();
}

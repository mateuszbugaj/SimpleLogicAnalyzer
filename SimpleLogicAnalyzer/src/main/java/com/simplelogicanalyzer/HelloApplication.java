package com.simplelogicanalyzer;

import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;
import com.fazecast.jSerialComm.SerialPort;


public class HelloApplication extends Application {

    private XYChart.Series series;
    private int maxDataPoints = 20;

    int theLastPoint = 1;
    int x = 0;

    final NumberAxis xAxis = new NumberAxis();
    final NumberAxis yAxis = new NumberAxis();

    @Override
    public void start(Stage stage) {
        // Create axes
        xAxis.setLabel("Milliseconds");
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(1);
        xAxis.setUpperBound(maxDataPoints + 1);

        // Create chart
        final LineChart<Number,Number> lineChart = new LineChart<>(xAxis,yAxis);
        lineChart.setTitle("Stock Monitoring");
        lineChart.setAnimated(false);

        // Create and add series
        series = new XYChart.Series();
        series.setName("My Portfolio");
        lineChart.getData().add(series);

        // Add initial data
        for (int i=1; i<=maxDataPoints; i++) {
            addData();
        }

        // Setup scene
        Scene scene  = new Scene(lineChart,800,600);
        stage.setScene(scene);
        stage.show();

        // Setup timeline to add new data every 2 seconds
        Timeline timeline = new Timeline(new KeyFrame(
                Duration.seconds(0.1),
                ae -> {
                    addData();
                    removeOldData();
                    xAxis.setLowerBound(xAxis.getLowerBound() + 1);
                    xAxis.setUpperBound(xAxis.getLowerBound() + 1 + maxDataPoints);
                }
        ));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void addData() {
        x += new Random().nextInt(21) - 10;
        series.getData().add(new XYChart.Data(theLastPoint, x));
        theLastPoint++;
    }
    private void removeOldData() {
        if (series.getData().size() > maxDataPoints) {
            series.getData().remove(0);
        }
    }

    /*
    USB Product ID - Function
    29967 - Arduino logic analyzer
    60000 - USB to UART Bridge Controller
     */
    public static void main(String[] args) throws IOException {
        ArrayList<String> logicAnalyzerContainer = new ArrayList<>();


        System.out.println("Available ports:");
        for(SerialPort serial:SerialPort.getCommPorts()){
            System.out.println(serial.getSystemPortName());
            System.out.println(serial.getDescriptivePortName());
            System.out.println(serial.getProductID());
            System.out.println();

            if(serial.getProductID() == 29_987 || serial.getProductID() == 60_000){
                serial.setBaudRate(9600);
                serial.openPort();
                serial.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                InputStream inputStream = serial.getInputStream();
                if(inputStream.available() > 0){
                    long skippedBytes = inputStream.skip(inputStream.available());
                    System.out.println("Skipped bytes" + skippedBytes);
                }

                if(serial.getProductID() == 29_987){
                    serial.addDataListener(new SerialPortDataListenerImpl(logicAnalyzerContainer));
                }
            }
        }
        launch(args);
    }

}

class SerialPortDataListenerImpl implements SerialPortDataListener {
    private final int CUTOFF_ASCII = '\r';
    private String buffer = "";
    public String receivedMessage = "";
    public ArrayList<String> container; // contains received messages

    public SerialPortDataListenerImpl(ArrayList<String> container) {
        this.container = container;
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }

    @Override
    public void serialEvent(SerialPortEvent serialPortEvent) {
        SerialPort serialPort = serialPortEvent.getSerialPort();

        if(receivedMessage.equals("")){
            int bytesAvailable = serialPort.bytesAvailable();
            byte[] byteBuffer = new byte[bytesAvailable];
            serialPort.readBytes(byteBuffer, bytesAvailable);

            receivedMessage = new String(byteBuffer);
        }

        if(receivedMessage.indexOf('\r') != -1){
            receivedMessage = buffer.concat(receivedMessage);
            buffer = "";

            long fullMessageCount = receivedMessage.chars().filter(c -> c == '\r').count();
            String[] messageSplit = receivedMessage.split("\r");
            for(int i = 0; i < messageSplit.length; i++){
                if(i < fullMessageCount){
                    container.add(messageSplit[i].trim());
                    System.out.println(container.get(container.size()-1));
                } else {
                    buffer = buffer.concat(messageSplit[i]);
                }
            }
        } else {
            buffer = buffer.concat(receivedMessage);
        }

        receivedMessage = "";
    }
}
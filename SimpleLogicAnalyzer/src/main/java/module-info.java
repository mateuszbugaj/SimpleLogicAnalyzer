module com.simplelogicanalyzer {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;


    opens com.simplelogicanalyzer to javafx.fxml;
    exports com.simplelogicanalyzer;
}
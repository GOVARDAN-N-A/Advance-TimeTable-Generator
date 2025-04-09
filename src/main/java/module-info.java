module com.example.timetablegenertor {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires org.apache.poi.ooxml;


    opens com.example.timetablegenertor to javafx.fxml;
    exports com.example.timetablegenertor;
}
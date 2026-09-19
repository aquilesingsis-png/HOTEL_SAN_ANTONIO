module untrm.hotel_san_antonio {
    requires javafx.controls;
    requires javafx.fxml;

    opens untrm.hotel_san_antonio to javafx.fxml;
    exports untrm.hotel_san_antonio;
}

module powertrain.ev.simulation.evpowertrainsimulation {
    requires javafx.controls;
    requires javafx.fxml;


    opens powertrain.ev.simulation to javafx.fxml;
    exports powertrain.ev.simulation;
}
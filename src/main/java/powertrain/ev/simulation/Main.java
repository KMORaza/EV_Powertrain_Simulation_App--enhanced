package powertrain.ev.simulation;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application {
    private static final int WAVE_POINTS = 200;
    private static final double SIM_UPDATE_MS = 200;
    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private static class EVSimulation {
        DoubleProperty batteryVoltage = new SimpleDoubleProperty(400); // V
        DoubleProperty batteryCapacity = new SimpleDoubleProperty(60); // kWh
        DoubleProperty motorPower = new SimpleDoubleProperty(150); // kW
        DoubleProperty motorTorque = new SimpleDoubleProperty(0); // Nm
        DoubleProperty motorRpm = new SimpleDoubleProperty(0); // RPM
        DoubleProperty vehicleSpeed = new SimpleDoubleProperty(0); // km/h
        DoubleProperty acceleration = new SimpleDoubleProperty(0); // m/s²
        DoubleProperty soc = new SimpleDoubleProperty(100); // %
        DoubleProperty distance = new SimpleDoubleProperty(0); // km
        DoubleProperty energyConsumed = new SimpleDoubleProperty(0); // kWh
        DoubleProperty regenEfficiency = new SimpleDoubleProperty(0.5); // 0.0-1.0
        DoubleProperty batteryTemp = new SimpleDoubleProperty(25); // °C
        DoubleProperty energyEfficiency = new SimpleDoubleProperty(0); // Wh/km
        DoubleProperty vehicleMass = new SimpleDoubleProperty(1500); // kg
        DoubleProperty dragCoefficient = new SimpleDoubleProperty(0.3);
        StringProperty driveMode = new SimpleStringProperty("Normal");
        BooleanProperty isRunning = new SimpleBooleanProperty(false);
        BooleanProperty isPaused = new SimpleBooleanProperty(false);
        BooleanProperty regenBraking = new SimpleBooleanProperty(true);
    }
    private final EVSimulation sim = new EVSimulation();
    private final double[] voltageWave = new double[WAVE_POINTS];
    private final double[] currentWave = new double[WAVE_POINTS];
    private final double[] speedWave = new double[WAVE_POINTS];
    private final double[] tempWave = new double[WAVE_POINTS];
    private int waveIndex = 0;
    private long lastTime = 0;
    private final AtomicBoolean isSimulationRunning = new AtomicBoolean(false);
    /// Drive mode parameters
    private static final Map<String, double[]> DRIVE_MODES = new HashMap<>();
    static {
        DRIVE_MODES.put("Eco", new double[]{0.5, 0.7}); // maxAccel, powerFactor
        DRIVE_MODES.put("Normal", new double[]{1.0, 1.0});
        DRIVE_MODES.put("Sport", new double[]{1.5, 1.3});
    }
    @Override
    public void start(Stage primaryStage) {
        for (int i = 0; i < WAVE_POINTS; i++) {
            voltageWave[i] = sim.batteryVoltage.get();
            currentWave[i] = 0;
            speedWave[i] = 0;
            tempWave[i] = sim.batteryTemp.get();
        }
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-font-size: 14px;");
        VBox centerContent = new VBox(10);
        centerContent.setPadding(new Insets(10));
        centerContent.setMaxWidth(400);
        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(10);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        TextField voltageField = createNumericField(sim.batteryVoltage, 100, 1000);
        TextField capacityField = createNumericField(sim.batteryCapacity, 10, 200);
        TextField powerField = createNumericField(sim.motorPower, 50, 500);
        TextField massField = createNumericField(sim.vehicleMass, 1000, 3000);
        TextField dragField = createNumericField(sim.dragCoefficient, 0.1, 0.5);
        CheckBox regenCheck = new CheckBox("Regen Braking");
        regenCheck.selectedProperty().bindBidirectional(sim.regenBraking);
        Slider regenSlider = new Slider(0, 100, 50);
        sim.regenEfficiency.bind(regenSlider.valueProperty().divide(100));
        ComboBox<String> driveModeCombo = new ComboBox<>();
        driveModeCombo.getItems().addAll("Eco", "Normal", "Sport");
        driveModeCombo.valueProperty().bindBidirectional(sim.driveMode);
        driveModeCombo.setValue("Normal");
        Spinner<Double> accelSpinner = new Spinner<>(-1.5, 1.5, 0, 0.1);
        accelSpinner.setDisable(true);
        sim.acceleration.bind(accelSpinner.valueProperty());
        addControl(controls, 0, "Battery Voltage (V):", voltageField);
        addControl(controls, 1, "Battery Capacity (kWh):", capacityField);
        addControl(controls, 2, "Motor Power (kW):", powerField);
        addControl(controls, 3, "Vehicle Mass (kg):", massField);
        addControl(controls, 4, "Drag Coefficient:", dragField);
        addControl(controls, 5, "Regen Braking:", regenCheck);
        addControl(controls, 6, "Regen Efficiency (%):", regenSlider);
        addControl(controls, 7, "Drive Mode:", driveModeCombo);
        addControl(controls, 8, "Acceleration (m/s²):", accelSpinner);
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        Button startButton = new Button("Start");
        Button pauseButton = new Button("Pause");
        Button stopButton = new Button("Stop");
        Button resetButton = new Button("Reset");
        Button exportButton = new Button("Export Data");
        stopButton.setDisable(true);
        resetButton.setDisable(true);
        pauseButton.setDisable(true);
        buttonBox.getChildren().addAll(startButton, pauseButton, stopButton, resetButton, exportButton);
        GridPane status = new GridPane();
        status.setHgap(10);
        status.setVgap(10);
        status.setPadding(new Insets(10));
        status.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        Label speedLabel = createStatusLabel(sim.vehicleSpeed, " km/h");
        Label socLabel = createStatusLabel(sim.soc, " %");
        Label distanceLabel = createStatusLabel(sim.distance, " km");
        Label energyLabel = createStatusLabel(sim.energyConsumed, " kWh");
        Label torqueLabel = createStatusLabel(sim.motorTorque, " Nm");
        Label rpmLabel = createStatusLabel(sim.motorRpm, " RPM");
        Label tempLabel = createStatusLabel(sim.batteryTemp, " °C");
        Label efficiencyLabel = createStatusLabel(sim.energyEfficiency, " Wh/km");
        addStatus(status, 0, "Speed:", speedLabel);
        addStatus(status, 1, "State of Charge:", socLabel);
        addStatus(status, 2, "Distance:", distanceLabel);
        addStatus(status, 3, "Energy Consumed:", energyLabel);
        addStatus(status, 4, "Motor Torque:", torqueLabel);
        addStatus(status, 5, "Motor RPM:", rpmLabel);
        addStatus(status, 6, "Battery Temp:", tempLabel);
        addStatus(status, 7, "Efficiency:", efficiencyLabel);
        Canvas canvas = new Canvas(400, 699);
        drawWaveforms(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
        startButton.setOnAction(e -> startSimulation(startButton, pauseButton, stopButton, resetButton, accelSpinner));
        pauseButton.setOnAction(e -> pauseSimulation(pauseButton));
        stopButton.setOnAction(e -> stopSimulation(startButton, pauseButton, stopButton, resetButton, accelSpinner));
        resetButton.setOnAction(e -> resetSimulation(canvas, speedLabel, socLabel, distanceLabel, energyLabel, torqueLabel, rpmLabel, tempLabel, efficiencyLabel));
        exportButton.setOnAction(e -> exportData());
        Label titleLabel = new Label("EV Powertrain Simulation");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        centerContent.getChildren().addAll(titleLabel, controls, buttonBox, status);
        root.setCenter(centerContent);
        root.setRight(canvas);
        Scene scene = new Scene(root, 800, 720);
        primaryStage.setTitle("EV Powertrain Simulation");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> Platform.exit());
        primaryStage.setResizable(false);
        primaryStage.show();
        startSimulationLoop(canvas);
    }

    private TextField createNumericField(DoubleProperty property, double min, double max) {
        TextField field = new TextField(DF.format(property.get()));
        field.setTooltip(new Tooltip("Enter a value between " + min + " and " + max));
        TextFormatter<Double> formatter = new TextFormatter<>(new DoubleStringConverter(), property.get(), change -> {
            try {
                double value = Double.parseDouble(change.getControlNewText());
                if (value >= min && value <= max) {
                    return change;
                }
            } catch (NumberFormatException ignored) {}
            return null;
        });
        field.setTextFormatter(formatter);
        property.bind(Bindings.createDoubleBinding(() -> {
            try {
                return Double.parseDouble(field.getText());
            } catch (NumberFormatException e) {
                return property.get();
            }
        }, field.textProperty()));
        return field;
    }

    private Label createStatusLabel(DoubleProperty property, String unit) {
        Label label = new Label(DF.format(property.get()) + unit);
        property.addListener((obs, old, newVal) -> label.setText(DF.format(newVal) + unit));
        return label;
    }

    private void addControl(GridPane grid, int row, String label, Control control) {
        grid.add(new Label(label), 0, row);
        grid.add(control, 1, row);
    }

    private void addStatus(GridPane grid, int row, String label, Label value) {
        grid.add(new Label(label), 0, row);
        grid.add(value, 1, row);
    }

    private void drawWaveforms(GraphicsContext gc, double width, double height) {
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, width, height);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(0.5);
        int gridSize = 15;
        double squareSize = Math.min(width, height) / gridSize;
        for (int i = 0; i <= 29; i++) {
            double pos = i * squareSize;
            gc.strokeLine(0, pos, width, pos);
            gc.strokeLine(pos, 0, pos, height);
        }
        /// Voltage (red)
        gc.setStroke(Color.RED);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i < WAVE_POINTS; i++) {
            int idx = (waveIndex + i) % WAVE_POINTS;
            double x = (double) i / WAVE_POINTS * width;
            double y = height - (voltageWave[idx] / (sim.batteryVoltage.get() * 1.2) * height * 0.8);
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
        /// Current (green)
        double maxCurrent = (sim.motorPower.get() * 1000 / sim.batteryVoltage.get()) * 1.2;
        gc.setStroke(Color.LIGHTGREEN);
        gc.beginPath();
        for (int i = 0; i < WAVE_POINTS; i++) {
            int idx = (waveIndex + i) % WAVE_POINTS;
            double x = (double) i / WAVE_POINTS * width;
            double y = height - (currentWave[idx] / maxCurrent * height * 0.8);
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
        /// Speed (blue)
        gc.setStroke(Color.LIGHTSKYBLUE);
        gc.beginPath();
        for (int i = 0; i < WAVE_POINTS; i++) {
            int idx = (waveIndex + i) % WAVE_POINTS;
            double x = (double) i / WAVE_POINTS * width;
            double y = height - (speedWave[idx] / 200 * height * 0.8);
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
        /// Temperature (yellow)
        gc.setStroke(Color.YELLOW);
        gc.beginPath();
        for (int i = 0; i < WAVE_POINTS; i++) {
            int idx = (waveIndex + i) % WAVE_POINTS;
            double x = (double) i / WAVE_POINTS * width;
            double y = height - ((tempWave[idx] - 10) / 60 * height * 0.8);
            if (i == 0) gc.moveTo(x, y);
            else gc.lineTo(x, y);
        }
        gc.stroke();
        /// Labels
        gc.setFill(Color.RED);
        gc.fillText("Voltage (V)", 10, 20);
        gc.setFill(Color.LIGHTGREEN);
        gc.fillText("Current (A)", 10, 40);
        gc.setFill(Color.LIGHTSKYBLUE);
        gc.fillText("Speed (km/h)", 10, 60);
        gc.setFill(Color.YELLOW);
        gc.fillText("Temp (°C)", 10, 80);
    }

    private void updateWaveforms(double timeDiff) {
        voltageWave[waveIndex] = sim.batteryVoltage.get() * (0.95 + 0.05 * Math.sin(timeDiff * 0.01));
        currentWave[waveIndex] = (sim.motorPower.get() * 1000 / sim.batteryVoltage.get()) * (0.9 + 0.1 * Math.sin(timeDiff * 0.02));
        speedWave[waveIndex] = sim.vehicleSpeed.get();
        tempWave[waveIndex] = sim.batteryTemp.get();
        waveIndex = (waveIndex + 1) % WAVE_POINTS;
    }

    private void startSimulation(Button startButton, Button pauseButton, Button stopButton, Button resetButton, Spinner<Double> accelSpinner) {
        sim.isRunning.set(true);
        sim.isPaused.set(false);
        isSimulationRunning.set(true);
        startButton.setDisable(true);
        pauseButton.setDisable(false);
        stopButton.setDisable(false);
        resetButton.setDisable(false);
        accelSpinner.setDisable(false);
        lastTime = System.nanoTime();
    }

    private void pauseSimulation(Button pauseButton) {
        sim.isPaused.set(!sim.isPaused.get());
        pauseButton.setText(sim.isPaused.get() ? "Resume" : "Pause");
    }

    private void stopSimulation(Button startButton, Button pauseButton, Button stopButton, Button resetButton, Spinner<Double> accelSpinner) {
        sim.isRunning.set(false);
        sim.isPaused.set(false);
        isSimulationRunning.set(false);
        startButton.setDisable(false);
        pauseButton.setDisable(true);
        pauseButton.setText("Pause");
        stopButton.setDisable(true);
        resetButton.setDisable(true);
        accelSpinner.setDisable(true);
    }

    private void resetSimulation(Canvas canvas, Label... labels) {
        sim.vehicleSpeed.set(0);
        sim.motorRpm.set(0);
        sim.motorTorque.set(0);
        sim.distance.set(0);
        sim.energyConsumed.set(0);
        sim.soc.set(100);
        sim.batteryTemp.set(25);
        sim.energyEfficiency.set(0);
        lastTime = 0;
        waveIndex = 0;
        for (int i = 0; i < WAVE_POINTS; i++) {
            voltageWave[i] = sim.batteryVoltage.get();
            currentWave[i] = 0;
            speedWave[i] = 0;
            tempWave[i] = sim.batteryTemp.get();
        }
        drawWaveforms(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
        for (Label label : labels) label.setText(DF.format(0) + label.getText().substring(label.getText().indexOf(" ")));
    }

    private void exportData() {
        try (FileWriter writer = new FileWriter("ev_simulation_" + UUID.randomUUID() + ".csv")) {
            writer.write("Index,Voltage (V),Current (A),Speed (km/h),Temperature (°C)\n");
            for (int i = 0; i < WAVE_POINTS; i++) {
                int idx = (waveIndex + i) % WAVE_POINTS;
                writer.write(String.format("%d,%.2f,%.2f,%.2f,%.2f\n",
                        i, voltageWave[idx], currentWave[idx], speedWave[idx], tempWave[idx]));
            }
            showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Data exported to CSV file.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Export Failed", "Error exporting data: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void startSimulationLoop(Canvas canvas) {
        new Thread(() -> {
            while (true) {
                if (isSimulationRunning.get() && !sim.isPaused.get()) {
                    Platform.runLater(() -> updateSimulation(canvas));
                }
                try {
                    Thread.sleep((long) SIM_UPDATE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private void updateSimulation(Canvas canvas) {
        if (!sim.isRunning.get()) return;
        long now = System.nanoTime();
        double dt = (lastTime == 0) ? 0.2 : (now - lastTime) / 1_000_000_000.0;
        lastTime = now;
        double[] modeParams = DRIVE_MODES.get(sim.driveMode.get());
        double maxAccel = modeParams[0];
        double powerFactor = modeParams[1];
        double accel = Math.max(-maxAccel, Math.min(maxAccel, sim.acceleration.get()));
        double speedMs = sim.vehicleSpeed.get() / 3.6;
        double force = sim.vehicleMass.get() * accel;
        double drag = 0.5 * sim.dragCoefficient.get() * 2.5 * 1.225 * speedMs * speedMs;
        double rolling = 0.01 * sim.vehicleMass.get() * 9.81;
        double totalForce = force - drag - rolling;
        speedMs += (totalForce / sim.vehicleMass.get()) * dt;
        sim.vehicleSpeed.set(Math.max(0, Math.min(180, speedMs * 3.6)));
        sim.motorRpm.set(sim.vehicleSpeed.get() * 50);
        /// Motor efficiency
        double motorEfficiency = 0.85 * (1 - 0.1 * Math.abs(sim.motorRpm.get() / 9000));
        sim.motorTorque.set(sim.motorPower.get() * powerFactor * 1000 /
                (Math.max(0.1, sim.motorRpm.get() / 60 * 2 * Math.PI) * motorEfficiency));

        sim.distance.set(sim.distance.get() + sim.vehicleSpeed.get() / 3600 * dt);
        /// Battery and energy
        double tempEfficiency = 1.0 - (sim.batteryTemp.get() > 40 ? (sim.batteryTemp.get() - 40) * 0.01 : 0);
        double powerUse = sim.motorPower.get() * powerFactor * (0.5 + 0.5 * Math.abs(accel)) / (motorEfficiency * tempEfficiency);
        sim.energyConsumed.set(sim.energyConsumed.get() + powerUse / 3600 * dt);
        sim.soc.set(100 - (sim.energyConsumed.get() / sim.batteryCapacity.get() * 100));
        if (sim.soc.get() < 0) sim.soc.set(0);
        /// Regenerative braking
        if (accel < 0 && sim.regenBraking.get() && sim.soc.get() < 95) {
            double regenEnergy = sim.regenEfficiency.get() * powerUse * 0.5;
            sim.energyConsumed.set(sim.energyConsumed.get() - regenEnergy / 3600 * dt);
            sim.soc.set(Math.min(100, 100 - (sim.energyConsumed.get() / sim.batteryCapacity.get() * 100)));
        }
        /// Battery temperature with thermal mass
        double thermalMass = 1000; // J/°C
        double heatInput = (powerUse / sim.motorPower.get()) * 0.1;
        double cooling = 0.05 * (sim.batteryTemp.get() - 25);
        sim.batteryTemp.set(sim.batteryTemp.get() + (heatInput - cooling) * dt / thermalMass);
        sim.batteryTemp.set(Math.max(10, Math.min(70, sim.batteryTemp.get())));
        /// Energy efficiency
        sim.energyEfficiency.set(sim.distance.get() > 0 ? (sim.energyConsumed.get() * 1000) / sim.distance.get() : 0);
        updateWaveforms(dt);
        drawWaveforms(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
package powertrain.ev.simulation;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application {
    private static final int WAVE_POINTS = 200;
    private static final double SIM_UPDATE_MS = 16.67; // ~60 FPS
    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMdd_HHmmss");
    private static class EVSimulation {
        /// Battery
        DoubleProperty batteryVoltage = new SimpleDoubleProperty(400); // V
        DoubleProperty batteryCapacity = new SimpleDoubleProperty(60); // kWh
        DoubleProperty soc = new SimpleDoubleProperty(100); // %
        DoubleProperty batteryTemp = new SimpleDoubleProperty(25); // °C
        DoubleProperty thermalMass = new SimpleDoubleProperty(1000); // J/°C
        /// Motor
        DoubleProperty motorPower = new SimpleDoubleProperty(150); // kW
        DoubleProperty motorTorque = new SimpleDoubleProperty(0); // Nm
        DoubleProperty motorRpm = new SimpleDoubleProperty(0); // RPM
        DoubleProperty gearRatio = new SimpleDoubleProperty(8.0); // Configurable gear ratio
        /// Vehicle
        DoubleProperty vehicleSpeed = new SimpleDoubleProperty(0); // km/h
        DoubleProperty acceleration = new SimpleDoubleProperty(0); // m/s²
        DoubleProperty vehicleMass = new SimpleDoubleProperty(1500); // kg
        DoubleProperty dragCoefficient = new SimpleDoubleProperty(0.3);
        DoubleProperty frontalArea = new SimpleDoubleProperty(2.5); // m²
        DoubleProperty airDensity = new SimpleDoubleProperty(1.225); // kg/m³
        DoubleProperty rollingResistance = new SimpleDoubleProperty(0.01);
        DoubleProperty distance = new SimpleDoubleProperty(0); // km
        DoubleProperty energyConsumed = new SimpleDoubleProperty(0); // kWh
        DoubleProperty regenEfficiency = new SimpleDoubleProperty(0.5); // 0.0-1.0
        DoubleProperty energyEfficiency = new SimpleDoubleProperty(0); // Wh/km
        /// Modes and State
        StringProperty driveMode = new SimpleStringProperty("Normal");
        BooleanProperty isRunning = new SimpleBooleanProperty(false);
        BooleanProperty isPaused = new SimpleBooleanProperty(false);
        BooleanProperty regenBraking = new SimpleBooleanProperty(true);
        /// Waveform Toggles
        BooleanProperty showVoltage = new SimpleBooleanProperty(true);
        BooleanProperty showCurrent = new SimpleBooleanProperty(true);
        BooleanProperty showSpeed = new SimpleBooleanProperty(true);
        BooleanProperty showTemp = new SimpleBooleanProperty(true);
    }

    private final EVSimulation sim = new EVSimulation();
    private final double[] voltageWave = new double[WAVE_POINTS];
    private final double[] currentWave = new double[WAVE_POINTS];
    private final double[] speedWave = new double[WAVE_POINTS];
    private final double[] tempWave = new double[WAVE_POINTS];
    private final double[] socWave = new double[WAVE_POINTS];
    private final double[] torqueWave = new double[WAVE_POINTS];
    private final double[] efficiencyWave = new double[WAVE_POINTS];
    private int waveIndex = 0;
    private long lastTime = 0;
    private final AtomicBoolean isSimulationRunning = new AtomicBoolean(false);
    private AnimationTimer simulationTimer;
    /// Drive mode parameters: {maxAccel (m/s²), powerFactor}
    private static final Map<String, double[]> DRIVE_MODES = new HashMap<>();
    static {
        DRIVE_MODES.put("Eco", new double[]{0.5, 0.7});
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
            socWave[i] = sim.soc.get();
            torqueWave[i] = 0;
            efficiencyWave[i] = 0;
        }

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(8));
        root.setStyle("-fx-font-size: 14px;");
        VBox centerContent = new VBox(8);
        centerContent.setPadding(new Insets(8));
        centerContent.setMaxWidth(450);
        /// Controls
        GridPane controls = new GridPane();
        controls.setHgap(10);
        controls.setVgap(8);
        controls.setPadding(new Insets(8));
        controls.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        TextField voltageField = createNumericField(sim.batteryVoltage, 100, 1000);
        voltageField.setPrefWidth(150);
        TextField capacityField = createNumericField(sim.batteryCapacity, 10, 200);
        capacityField.setPrefWidth(150);
        TextField powerField = createNumericField(sim.motorPower, 50, 500);
        powerField.setPrefWidth(150);
        TextField massField = createNumericField(sim.vehicleMass, 1000, 3000);
        massField.setPrefWidth(150);
        TextField dragField = createNumericField(sim.dragCoefficient, 0.1, 0.5);
        dragField.setPrefWidth(150);
        TextField frontalAreaField = createNumericField(sim.frontalArea, 1.5, 3.5);
        frontalAreaField.setPrefWidth(150);
        TextField airDensityField = createNumericField(sim.airDensity, 1.0, 1.5);
        airDensityField.setPrefWidth(150);
        TextField rollingField = createNumericField(sim.rollingResistance, 0.005, 0.02);
        rollingField.setPrefWidth(150);
        TextField gearRatioField = createNumericField(sim.gearRatio, 4.0, 12.0);
        gearRatioField.setPrefWidth(150);
        TextField thermalMassField = createNumericField(sim.thermalMass, 500, 2000);
        thermalMassField.setPrefWidth(150);
        CheckBox regenCheck = new CheckBox("Regen Braking");
        regenCheck.selectedProperty().bindBidirectional(sim.regenBraking);
        Slider regenSlider = new Slider(0, 100, 50);
        regenSlider.setPrefWidth(150);
        sim.regenEfficiency.bind(regenSlider.valueProperty().divide(100));
        ComboBox<String> driveModeCombo = new ComboBox<>();
        driveModeCombo.getItems().addAll("Eco", "Normal", "Sport");
        driveModeCombo.valueProperty().bindBidirectional(sim.driveMode);
        driveModeCombo.setValue("Normal");
        driveModeCombo.setPrefWidth(150);
        Spinner<Double> accelSpinner = new Spinner<>(-1.5, 1.5, 0, 0.1);
        accelSpinner.setPrefWidth(150);
        accelSpinner.setDisable(true);
        sim.acceleration.bind(accelSpinner.valueProperty());
        /// Waveform toggle checkboxes
        CheckBox voltageCheck = new CheckBox("Voltage");
        voltageCheck.selectedProperty().bindBidirectional(sim.showVoltage);
        CheckBox currentCheck = new CheckBox("Current");
        currentCheck.selectedProperty().bindBidirectional(sim.showCurrent);
        CheckBox speedCheck = new CheckBox("Speed");
        speedCheck.selectedProperty().bindBidirectional(sim.showSpeed);
        CheckBox tempCheck = new CheckBox("Temp");
        tempCheck.selectedProperty().bindBidirectional(sim.showTemp);
        VBox waveformBox = new VBox(8, voltageCheck, currentCheck, speedCheck, tempCheck);
        waveformBox.setAlignment(Pos.CENTER_LEFT);
        addControl(controls, 0, "Battery Voltage (V):", voltageField);
        addControl(controls, 1, "Battery Capacity (kWh):", capacityField);
        addControl(controls, 2, "Motor Power (kW):", powerField);
        addControl(controls, 3, "Vehicle Mass (kg):", massField);
        addControl(controls, 4, "Drag Coefficient:", dragField);
        addControl(controls, 5, "Frontal Area (m²):", frontalAreaField);
        addControl(controls, 6, "Air Density (kg/m³):", airDensityField);
        addControl(controls, 7, "Rolling Resistance:", rollingField);
        addControl(controls, 8, "Gear Ratio:", gearRatioField);
        addControl(controls, 9, "Thermal Mass (J/°C):", thermalMassField);
        addControl(controls, 10, "Regen Braking:", regenCheck);
        addControl(controls, 11, "Regen Efficiency (%):", regenSlider);
        addControl(controls, 12, "Drive Mode:", driveModeCombo);
        addControl(controls, 13, "Acceleration (m/s²):", accelSpinner);
        addControl(controls, 14, "Show Waveforms:", waveformBox);
        ScrollPane controlsScroll = new ScrollPane(controls);
        controlsScroll.setFitToWidth(true);
        controlsScroll.setFitToHeight(true);
        controlsScroll.setMaxHeight(400);
        /// Buttons
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER);
        Button startButton = new Button("Start");
        startButton.setPrefWidth(80);
        Button pauseButton = new Button("Pause");
        pauseButton.setPrefWidth(80);
        Button stopButton = new Button("Stop");
        stopButton.setPrefWidth(80);
        Button resetButton = new Button("Reset");
        resetButton.setPrefWidth(80);
        Button exportButton = new Button("Export Data");
        exportButton.setPrefWidth(80);
        stopButton.setDisable(true);
        resetButton.setDisable(true);
        pauseButton.setDisable(true);
        buttonBox.getChildren().addAll(startButton, pauseButton, stopButton, resetButton, exportButton);
        /// Status
        GridPane status = new GridPane();
        status.setHgap(10);
        status.setVgap(8);
        status.setPadding(new Insets(8));
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
        /// Canvas
        Canvas canvas = new Canvas(450, 790);
        drawWaveforms(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
        /// Button Actions
        startButton.setOnAction(e -> startSimulation(startButton, pauseButton, stopButton, resetButton, accelSpinner));
        pauseButton.setOnAction(e -> pauseSimulation(pauseButton));
        stopButton.setOnAction(e -> stopSimulation(startButton, pauseButton, stopButton, resetButton, accelSpinner));
        resetButton.setOnAction(e -> resetSimulation(canvas, speedLabel, socLabel, distanceLabel, energyLabel, torqueLabel, rpmLabel, tempLabel, efficiencyLabel));
        exportButton.setOnAction(e -> exportData());
        /// Layout
        Label titleLabel = new Label("EV Powertrain Simulation");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        centerContent.getChildren().addAll(titleLabel, controlsScroll, buttonBox, status);
        ScrollPane centerScroll = new ScrollPane(centerContent);
        centerScroll.setFitToWidth(true);
        centerScroll.setFitToHeight(true);
        root.setCenter(centerScroll);
        root.setRight(canvas);
        Scene scene = new Scene(root, 900, 800);
        primaryStage.setTitle("EV Powertrain Simulation");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            isSimulationRunning.set(false);
            if (simulationTimer != null) simulationTimer.stop();
            Platform.exit();
        });
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

    private void addControl(GridPane grid, int row, String label, Node control) {
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
        int gridSize = 10;
        double squareSize = Math.min(width, height) / 10;
        for (int i = 0; i <= 25; i++) {
            double pos = i * squareSize;
            gc.strokeLine(0, pos, width, pos);
            gc.strokeLine(pos, 0, pos, height);
        }
        double maxVoltage = Arrays.stream(voltageWave).max().orElse(sim.batteryVoltage.get()) * 1.1;
        double maxCurrent = Arrays.stream(currentWave).max().orElse((sim.motorPower.get() * 1000 / sim.batteryVoltage.get())) * 1.1;
        double maxSpeed = Arrays.stream(speedWave).max().orElse(180.0) * 1.1;
        double maxTemp = Arrays.stream(tempWave).max().orElse(70.0) * 1.1;
        /// Voltage (red)
        if (sim.showVoltage.get()) {
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            gc.beginPath();
            for (int i = 0; i < WAVE_POINTS; i++) {
                int idx = (waveIndex + i) % WAVE_POINTS;
                double x = (double) i / WAVE_POINTS * width;
                double y = (height / 4) - (voltageWave[idx] / maxVoltage * (height / 4) * 0.8);
                if (i == 0) gc.moveTo(x, y);
                else gc.lineTo(x, y);
            }
            gc.stroke();
            gc.setFill(Color.RED);
            gc.fillText("Voltage (V)", 10, 20);
        }
        /// Current (green)
        if (sim.showCurrent.get()) {
            gc.setStroke(Color.LIGHTGREEN);
            gc.beginPath();
            for (int i = 0; i < WAVE_POINTS; i++) {
                int idx = (waveIndex + i) % WAVE_POINTS;
                double x = (double) i / WAVE_POINTS * width;
                double y = (height / 2) - (currentWave[idx] / maxCurrent * (height / 4) * 0.8);
                if (i == 0) gc.moveTo(x, y);
                else gc.lineTo(x, y);
            }
            gc.stroke();
            gc.setFill(Color.LIGHTGREEN);
            gc.fillText("Current (A)", 10, height / 4 + 20);
        }
        /// Speed (blue)
        if (sim.showSpeed.get()) {
            gc.setStroke(Color.LIGHTSKYBLUE);
            gc.beginPath();
            for (int i = 0; i < WAVE_POINTS; i++) {
                int idx = (waveIndex + i) % WAVE_POINTS;
                double x = (double) i / WAVE_POINTS * width;
                double y = (3 * height / 4) - (speedWave[idx] / maxSpeed * (height / 4) * 0.8);
                if (i == 0) gc.moveTo(x, y);
                else gc.lineTo(x, y);
            }
            gc.stroke();
            gc.setFill(Color.LIGHTSKYBLUE);
            gc.fillText("Speed (km/h)", 10, height / 2 + 20);
        }
        /// Temperature (yellow)
        if (sim.showTemp.get()) {
            gc.setStroke(Color.YELLOW);
            gc.beginPath();
            for (int i = 0; i < WAVE_POINTS; i++) {
                int idx = (waveIndex + i) % WAVE_POINTS;
                double x = (double) i / WAVE_POINTS * width;
                double y = height - ((tempWave[idx] - 10) / maxTemp * (height / 4) * 0.8);
                if (i == 0) gc.moveTo(x, y);
                else gc.lineTo(x, y);
            }
            gc.stroke();
            gc.setFill(Color.YELLOW);
            gc.fillText("Temp (°C)", 10, 3 * height / 4 + 20);
        }
    }

    private void updateWaveforms(double dt) {
        /// Voltage based on SoC
        double socFactor = sim.soc.get() < 20 ? 0.95 : sim.soc.get() > 80 ? 1.05 : 1.0;
        voltageWave[waveIndex] = sim.batteryVoltage.get() * socFactor;
        /// Current based on power consumption
        double motorEfficiency = 0.85 * (1 - 0.1 * Math.abs(sim.motorRpm.get() / 9000));
        double powerUse = sim.motorPower.get() * (0.5 + 0.5 * Math.abs(sim.acceleration.get())) / motorEfficiency;
        currentWave[waveIndex] = powerUse * 1000 / sim.batteryVoltage.get();
        speedWave[waveIndex] = sim.vehicleSpeed.get();
        tempWave[waveIndex] = sim.batteryTemp.get();
        socWave[waveIndex] = sim.soc.get();
        torqueWave[waveIndex] = sim.motorTorque.get();
        efficiencyWave[waveIndex] = sim.energyEfficiency.get();
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
            socWave[i] = sim.soc.get();
            torqueWave[i] = 0;
            efficiencyWave[i] = 0;
        }
        drawWaveforms(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
        for (Label label : labels) {
            String unit = label.getText().substring(label.getText().indexOf(" "));
            label.setText(DF.format(0) + unit);
        }
    }

    private void exportData() {
        String timestamp = SDF.format(new Date());
        String filename = "ev_simulation_" + timestamp + ".csv";
        try (FileWriter writer = new FileWriter(filename)) {
            /// header with simulation parameters
            writer.write(String.format("Simulation Parameters: Voltage=%.2f V, Capacity=%.2f kWh, Motor Power=%.2f kW, " +
                            "Mass=%.2f kg, Drag=%.2f, Frontal Area=%.2f m², Air Density=%.2f kg/m³, " +
                            "Rolling Resistance=%.2f, Gear Ratio=%.2f, Thermal Mass=%.2f J/°C\n",
                    sim.batteryVoltage.get(), sim.batteryCapacity.get(), sim.motorPower.get(),
                    sim.vehicleMass.get(), sim.dragCoefficient.get(), sim.frontalArea.get(),
                    sim.airDensity.get(), sim.rollingResistance.get(), sim.gearRatio.get(),
                    sim.thermalMass.get()));
            /// data header
            writer.write("Index,Voltage (V),Current (A),Speed (km/h),Temperature (°C),SoC (%),Torque (Nm),Efficiency (Wh/km)\n");
            for (int i = 0; i < WAVE_POINTS; i++) {
                int idx = (waveIndex + i) % WAVE_POINTS;
                writer.write(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                        i, voltageWave[idx], currentWave[idx], speedWave[idx], tempWave[idx],
                        socWave[idx], torqueWave[idx], efficiencyWave[idx]));
            }
            showAlert(Alert.AlertType.INFORMATION, "Export Successful", "Data exported to " + filename);
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
        simulationTimer = new AnimationTimer() {
            long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (isSimulationRunning.get() && !sim.isPaused.get()) {
                    double dt = (lastUpdate == 0) ? SIM_UPDATE_MS / 1000.0 : (now - lastUpdate) / 1_000_000_000.0;
                    lastUpdate = now;
                    updateSimulation(canvas, Math.min(dt, 0.1));
                }
            }
        };
        simulationTimer.start();
    }

    private void updateSimulation(Canvas canvas, double dt) {
        if (!sim.isRunning.get()) return;
        double[] modeParams = DRIVE_MODES.get(sim.driveMode.get());
        double maxAccel = modeParams[0];
        double powerFactor = modeParams[1];
        /// Vehicle Dynamics
        double accel = Math.max(-maxAccel, Math.min(maxAccel, sim.acceleration.get()));
        double speedMs = sim.vehicleSpeed.get() / 3.6;
        double force = sim.vehicleMass.get() * accel;
        double drag = 0.5 * sim.dragCoefficient.get() * sim.frontalArea.get() * sim.airDensity.get() * speedMs * speedMs;
        double rolling = sim.rollingResistance.get() * sim.vehicleMass.get() * 9.81;
        double totalForce = force - drag - rolling;
        speedMs += (totalForce / sim.vehicleMass.get()) * dt;
        sim.vehicleSpeed.set(Math.max(0, Math.min(180, speedMs * 3.6)));
        /// Motor
        sim.motorRpm.set(sim.vehicleSpeed.get() * sim.gearRatio.get() * 60 / (0.377 * 0.4)); // Wheel radius ~0.4m
        double motorEfficiency = 0.85 * (1 - 0.1 * Math.abs(sim.motorRpm.get() / 9000));
        sim.motorTorque.set(sim.motorPower.get() * powerFactor * 1000 /
                (Math.max(0.1, sim.motorRpm.get() / 60 * 2 * Math.PI) * motorEfficiency));
        sim.distance.set(sim.distance.get() + sim.vehicleSpeed.get() / 3600 * dt);
        /// Battery and Energy
        double tempEfficiency = 1.0 - (sim.batteryTemp.get() > 40 ? (sim.batteryTemp.get() - 40) * 0.01 : 0);
        double powerUse = sim.motorPower.get() * powerFactor * (0.5 + 0.5 * Math.abs(accel)) / (motorEfficiency * tempEfficiency);
        sim.energyConsumed.set(sim.energyConsumed.get() + powerUse / 3600 * dt);
        sim.soc.set(100 - (sim.energyConsumed.get() / sim.batteryCapacity.get() * 100));
        if (sim.soc.get() < 0) sim.soc.set(0);
        /// Regenerative Braking
        if (accel < 0 && sim.regenBraking.get() && sim.soc.get() < 95) {
            double socFactor = sim.soc.get() > 80 ? 0.5 : 1.0;
            double regenEnergy = sim.regenEfficiency.get() * powerUse * 0.5 * socFactor;
            sim.energyConsumed.set(sim.energyConsumed.get() - regenEnergy / 3600 * dt);
            sim.soc.set(Math.min(100, 100 - (sim.energyConsumed.get() / sim.batteryCapacity.get() * 100)));
        }
        /// Battery Temperature
        double heatInput = (powerUse / sim.motorPower.get()) * 0.1;
        double cooling = 0.05 * (sim.batteryTemp.get() - 25);
        sim.batteryTemp.set(sim.batteryTemp.get() + (heatInput - cooling) * dt / sim.thermalMass.get());
        sim.batteryTemp.set(Math.max(10, Math.min(70, sim.batteryTemp.get())));
        /// Energy Efficiency
        sim.energyEfficiency.set(sim.distance.get() > 0 ? (sim.energyConsumed.get() * 1000) / sim.distance.get() : 0);
        updateWaveforms(dt);
        drawWaveforms(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
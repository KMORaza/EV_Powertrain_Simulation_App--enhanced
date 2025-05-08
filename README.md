### First enhancement
* Users can adjust battery voltage (100–1000V), capacity (10–200kWh), motor power (50–500kW), vehicle mass (1000–3000kg), drag coefficient (0.1–0.5), regen efficiency (0–100%), drive mode, and acceleration (-1.5 to 1.5 m/s²).
* Shows real-time values for speed, SoC, distance, energy consumed, torque, RPM, battery temperature, and efficiency.
* Displays four waveforms with a grid overlay, color-coded for voltage (red), current (green), speed (blue), and temperature (yellow).
* Calculates vehicle speed using Newton’s second law, accounting for acceleration, drag, and rolling resistance.
* Drag force: `0.5 * dragCoefficient * 2.5 * 1.225 * speed^2` (assumes frontal area of 2.5m², air density 1.225kg/m³).
* Rolling resistance: `0.01 * mass * 9.81`.
* RPM is approximated as `speed * 50` (simplistic, assumes fixed gear ratio).
* Torque is calculated as `power * powerFactor / (RPM * efficiency)`, with efficiency decreasing at high RPM.
* Power consumption: `motorPower * powerFactor * (0.5 + 0.5 * |accel|) / (motorEfficiency * tempEfficiency)`.
* SoC: `100 - (energyConsumed / batteryCapacity * 100)`.
* Regenerative braking recovers energy when decelerating.
* Battery temperature changes based on heat input (from power use) and cooling, using a thermal mass of 1000 J/°C.
* Temperature is capped between 10°C and 70°C.
* Efficiency: `energyConsumed * 1000 / distance` (Wh/km).
* Stores 200 points for each parameter (voltage, current, speed, temperature).
* Voltage and current include sinusoidal noise for realism.
* Incorporates drag, rolling resistance, motor efficiency, regenerative braking, and thermal effects.
* Drive modes add variety, simulating different driving styles.

| ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/001.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/002.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/003.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/004.png) |
|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/005.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/006.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/007.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/008.png) |
| ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/009.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/010.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/011.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/012.png) |
| ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/013.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/014.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/015.png) | ![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshots/016.png) |

### Second enhancement
* Users can input values for battery voltage, capacity, motor power, vehicle mass, etc., within defined ranges (e.g., voltage: 100–1000V).
* Drive mode selection (Eco, Normal, Sport) adjusts max acceleration and power factor.
* Acceleration is controlled via a `Spinner` (-1.5 to 1.5 m/s²).
* Regenerative braking can be enabled/disabled, with efficiency adjustable via a `Slider`.
* Waveform visibility (voltage, current, speed, temperature) is toggled via `CheckBoxes`.
* Time step is capped at 0.1s to prevent large jumps.
* Calculates total force (force = mass * acceleration - drag - rolling resistance) & updates speed.
* Computes RPM based on speed and gear ratio.
* Calculates torque (torque = power * power factor / (RPM * efficiency).
* Motor efficiency decreases with high RPM.
* Power consumption depends on acceleration, motor efficiency, and temperature.
* Energy consumed updates SoC & regenerative braking recovers energy when decelerating, modulated by efficiency and SoC.
* Battery temperature changes based on heat input (from power use) and cooling.
* Temperature is capped between 10°C and 70°C.
* Models key EV components (battery, motor, vehicle) with realistic physics (drag, rolling resistance, regen braking).
* Includes thermal effects and efficiency variations based on operating conditions.

![](https://github.com/KMORaza/EV_Powertrain_Simulation_App--enhanced/blob/main/src/screenshot.png)

# Heatwise — Idea

## Overview
**Heatwise** is a lightweight, intelligent controller for an electric immersion heater (hot-water tank spiral).  
It automatically schedules heating to run during **low electricity price periods** using **Octopus Agile** data, helping to reduce energy costs and carbon footprint.

## Concept
The system continuously monitors:
- **Agile half-hour electricity prices** from the Octopus Energy API
- (Optionally) **local water temperature** from a sensor or smart relay with metering

Based on configurable thresholds, cylinder water temperature, and timing rules, it decides when to switch the **immersion heater ON or OFF**.  
Control happens through a **Wi-Fi relay** (e.g., Shelly Pro or Sonoff) that operates a **contactor coil**—the contactor itself switches the 3 kW (or similar) heating element safely.

## Goals
- **Cost optimization:** Use cheaper Agile slots automatically (it can even earn money when th electricity price is negative!)
- **Convenience:** Maintain sufficient hot water by a chosen “ready-by” time(s).
- **Simplicity:** No cloud dependencies, everything runs locally or in a small container (Raspberry Pi, NAS, etc.).
- **Safety:** The tank’s built-in thermostat remains the final temperature control.
- **Observability:** Exposes health, readiness, and Prometheus-compatible metrics for monitoring.

## Typical Flow
0. During startup recover previous decisions.
1. Fetch current and upcoming Agile prices.
2. Fetch current cylinder temperature.
3. Decide if heating is economically and operationally justified.
4. Command the Wi-Fi relay to energize or cut power to the immersion contactor.
5. Record each decision for transparency and analysis.

![](../diagrams/basic.svg)
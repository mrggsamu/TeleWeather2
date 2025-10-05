# TeleWeather

TeleWeather is an Android prototype (Jetpack Compose + Material 3) that **simulates** early disaster detection from satellite scenarios.  
It estimates risk for **Flood**, **Wildfire**, **Cyclone**, **Drought**, and **Landslide**, and lets you save **alerts**.  
Optionally, it can ingest **local sensor data** over classic Bluetooth (HC-05/ESP32) as an extra.

> ⚠️ This is a **demo** app. The “AI” is simulated and uses deterministic logic for chosen scenarios.  
> The Bluetooth feature is optional and meant for lab testing.

---

## Features

- **AI Analysis (simulated):** pick a satellite-like scenario → get top hazard, risk %, lead time, area and basic remote-sensing metrics (NDVI, LST, soil moisture, cloud cover).
- **Alerts:** create and review alerts with hazard, risk and approximate location.
- **Dashboard & History:** live temperature/humidity from a simulated stream; automatically switches to real values if Bluetooth input is connected.
- **Bluetooth (extra):** connect to a paired device (HC-05/HC06/ESP32 via SPP) and parse lines like  
  `T=23.4,H=52.7,P=1012.8`.
- **Material 3 / Dark mode:** clean UI, drawer navigation.

---

## Tech Stack

- **Kotlin**, **Jetpack Compose**, **Material 3**
- **Gradle 8**, **JDK 17**
- Optional: **Bluetooth classic (RFCOMM / SPP)**

---

## Project Structure (high level)


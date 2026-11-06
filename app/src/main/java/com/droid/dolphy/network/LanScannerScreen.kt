package com.droid.dolphy.network

import com.droid.dolphy.AppLocale
import com.droid.dolphy.DolphyIconButton

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.droid.dolphy.MaterialBackground
import com.droid.dolphy.MaterialCard
import com.droid.dolphy.R
import com.droid.dolphy.SectionTopBar
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

enum class LanDeviceType { PC, PHONE, TV, CAMERA, PRINTER, ROUTER, OTHER }

data class LanDevice(
    val ip: String,
    val hostname: String?,
    val mac: String?,
    val type: LanDeviceType,
    val isSelf: Boolean = false,
    val openPorts: List<Int> = emptyList(),
    val serviceName: String? = null,
    val isUPnP: Boolean = false,
    val brand: String? = null
)

private val portServiceNames = mapOf(
    21 to "FTP",
    22 to "SSH",
    23 to "Telnet",
    53 to "DNS",
    80 to "HTTP",
    135 to "RPC",
    139 to "NetBIOS",
    443 to "HTTPS",
    445 to "SMB",
    554 to "RTSP Cam",
    1883 to "MQTT IoT",
    3389 to "RDP",
    5000 to "UPnP/NAS",
    5353 to "mDNS",
    5555 to "ADB",
    7000 to "AirPlay",
    8000 to "ONVIF/Cam",
    8008 to "Cast",
    8009 to "Cast TLS",
    8080 to "HTTP-Alt",
    8089 to "TR-069",
    8443 to "HTTPS-Alt",
    8888 to "Web-UI",
    9100 to "Printer",
    62078 to "iOS Sync"
)

private val ouiVendors = mapOf(
    "00:00:0C" to "Cisco", "00:01:42" to "Google", "00:04:20" to "Intel",
    "00:05:5D" to "Avaya", "00:06:5B" to "Samsung", "00:09:0F" to "Harman",
    "00:0A:27" to "TCL", "00:0C:29" to "VMware", "00:0E:07" to "TP-Link",
    "00:0E:08" to "TP-Link", "00:0E:2E" to "LG", "00:10:18" to "Netgear",
    "00:11:22" to "Dahua", "00:11:32" to "Xiaomi", "00:11:50" to "D-Link",
    "00:11:5E" to "Hikvision", "00:11:6B" to "Toshiba", "00:11:95" to "Panasonic",
    "00:11:D8" to "Epson", "00:12:17" to "Apple", "00:12:47" to "Huawei",
    "00:12:74" to "Sony", "00:12:BF" to "Dell", "00:13:10" to "Xerox",
    "00:13:21" to "HP", "00:13:46" to "Canon", "00:13:72" to "Linksys",
    "00:13:77" to "Roku", "00:13:80" to "Raspberry Pi", "00:13:95" to "Netgear",
    "00:14:22" to "Dell", "00:14:6C" to "Cisco", "00:14:A4" to "UniFi",
    "00:14:BF" to "Samsung", "00:15:17" to "Apple", "00:15:E9" to "Nintendo",
    "00:16:17" to "Xiaomi", "00:16:38" to "Hikvision", "00:16:41" to "Tenda",
    "00:16:6D" to "Microsoft", "00:16:CB" to "Panasonic", "00:16:D3" to "Acer",
    "00:16:EA" to "TP-Link", "00:17:31" to "Asus", "00:17:61" to "Sonos",
    "00:17:88" to "Cisco-Linksys", "00:17:9A" to "Toshiba", "00:17:F2" to "Apple",
    "00:18:0A" to "LG", "00:18:0F" to "Apple", "00:18:1A" to "Huawei",
    "00:18:39" to "Apple", "00:18:4D" to "Apple", "00:18:73" to "Xiaomi",
    "00:18:E7" to "Bosch", "00:18:F6" to "Sony", "00:19:07" to "Apple",
    "00:19:5B" to "HP", "00:19:6C" to "Intel", "00:19:7E" to "Ruckus",
    "00:19:BB" to "TP-Link", "00:19:C1" to "Axis", "00:19:E0" to "D-Link",
    "00:1A:11" to "Google", "00:1A:2B" to "Broadcom", "00:1A:3F" to "Netgear",
    "00:1A:4F" to "Xiaomi", "00:1A:5B" to "HP", "00:1A:70" to "Samsung",
    "00:1A:73" to "Acer", "00:1A:79" to "Cisco", "00:1A:8C" to "Panasonic",
    "00:1A:A0" to "Sony", "00:1A:B6" to "TP-Link", "00:1A:C0" to "Xerox",
    "00:1A:E8" to "LG", "00:1B:10" to "Intel", "00:1B:11" to "Apple",
    "00:1B:21" to "Dell", "00:1B:2F" to "Samsung", "00:1B:63" to "Toshiba",
    "00:1B:67" to "Asus", "00:1B:73" to "Lenovo", "00:1B:7A" to "Canon",
    "00:1B:B9" to "Netgear", "00:1B:C0" to "TP-Link", "00:1B:D4" to "D-Link",
    "00:1B:EF" to "Xiaomi", "00:1B:F1" to "HiSilicon", "00:1C:10" to "Xiaomi",
    "00:1C:14" to "HP", "00:1C:23" to "Apple", "00:1C:26" to "Cisco",
    "00:1C:2E" to "Epson", "00:1C:3A" to "Roku", "00:1C:42" to "Sony",
    "00:1C:48" to "D-Link", "00:1C:49" to "TP-Link", "00:1C:4A" to "Dell",
    "00:1C:58" to "Intel", "00:1C:7A" to "Huawei", "00:1C:AB" to "Samsung",
    "00:1C:B3" to "Dell", "00:1C:C0" to "Asus", "00:1C:C4" to "LG",
    "00:1C:DF" to "Netgear", "00:1C:F0" to "Apple", "00:1D:0E" to "ZTE",
    "00:1D:19" to "Sonos", "00:1D:1E" to "HP", "00:1D:20" to "TP-Link",
    "00:1D:60" to "Apple", "00:1D:72" to "Xiaomi", "00:1D:7E" to "Microsoft",
    "00:1D:92" to "Panasonic", "00:1D:A5" to "Hikvision", "00:1D:D4" to "Tenda",
    "00:1D:E5" to "Huawei", "00:1D:EC" to "Nintendo", "00:1E:0E" to "Samsung",
    "00:1E:13" to "D-Link", "00:1E:2A" to "Intel", "00:1E:3A" to "Roku",
    "00:1E:42" to "Sony", "00:1E:4C" to "TP-Link", "00:1E:52" to "Xiaomi",
    "00:1E:58" to "Dell", "00:1E:65" to "Apple", "00:1E:68" to "Canon",
    "00:1E:6C" to "Netgear", "00:1E:7A" to "Asus", "00:1E:8C" to "LG",
    "00:1E:8F" to "Huawei", "00:1E:C1" to "Axis", "00:1E:E0" to "Lenovo",
    "00:1E:E5" to "Cisco", "00:1E:F7" to "Xiaomi", "00:1F:01" to "Apple",
    "00:1F:1E" to "Samsung", "00:1F:33" to "HP", "00:1F:3C" to "Toshiba",
    "00:1F:45" to "TP-Link", "00:1F:4C" to "D-Link", "00:1F:5C" to "Panasonic",
    "00:1F:63" to "Intel", "00:1F:64" to "Hikvision", "00:1F:6A" to "Dell",
    "00:1F:81" to "Netgear", "00:1F:90" to "Sony", "00:1F:A3" to "Asus",
    "00:1F:A8" to "Huawei", "00:1F:CC" to "TP-Link", "00:1F:D0" to "Xiaomi",
    "00:1F:D5" to "LG", "00:1F:E8" to "Xiaomi", "00:20:12" to "Panasonic",
    "00:20:18" to "Cisco", "00:20:3E" to "D-Link", "00:20:5C" to "HP",
    "00:20:6B" to "Epson", "00:20:78" to "Xerox", "00:20:7D" to "Netgear",
    "00:20:A6" to "Sony", "00:20:B0" to "Dell", "00:20:E0" to "LG",
    "00:21:2B" to "D-Link", "00:21:63" to "TP-Link", "00:21:6E" to "Samsung",
    "00:21:70" to "Dell", "00:21:81" to "Huawei", "00:21:86" to "Xiaomi",
    "00:21:91" to "Apple", "00:21:9B" to "HP", "00:21:A0" to "Toshiba",
    "00:21:A5" to "Intel", "00:21:B7" to "Hikvision", "00:21:C0" to "Asus",
    "00:21:CC" to "D-Link", "00:21:D2" to "LG", "00:21:E1" to "TP-Link",
    "00:21:E8" to "Netgear", "00:21:F7" to "Lenovo", "00:21:FB" to "Sony",
    "00:22:03" to "Cisco", "00:22:0D" to "Panasonic", "00:22:22" to "Xiaomi",
    "00:22:2E" to "Xiaomi", "00:22:3F" to "TP-Link", "00:22:48" to "Huawei",
    "00:22:4B" to "Dell", "00:22:55" to "Synology", "00:22:59" to "HP",
    "00:22:64" to "Samsung", "00:22:6B" to "Apple", "00:22:75" to "Roku",
    "00:22:7B" to "D-Link", "00:22:8E" to "Toshiba", "00:22:A4" to "Asus",
    "00:22:AB" to "Netgear", "00:22:B0" to "Intel", "00:22:B8" to "Apple",
    "00:22:C3" to "Xerox", "00:22:D1" to "LG", "00:22:E6" to "Canon",
    "00:22:F2" to "Lenovo", "00:22:F4" to "Xiaomi", "00:23:08" to "Sony",
    "00:23:0E" to "D-Link", "00:23:14" to "Apple", "00:23:18" to "Xiaomi",
    "00:23:1B" to "TP-Link", "00:23:2A" to "Samsung", "00:23:3C" to "Huawei",
    "00:23:4E" to "HP", "00:23:54" to "Asus", "00:23:5B" to "Cisco",
    "00:23:69" to "Dell", "00:23:76" to "LG", "00:23:7D" to "Intel",
    "00:23:7F" to "Toshiba", "00:23:8B" to "Apple", "00:23:97" to "Panasonic",
    "00:23:A7" to "Netgear", "00:23:C1" to "Hikvision", "00:23:C5" to "Xiaomi",
    "00:23:CB" to "TP-Link", "00:23:D4" to "Huawei", "00:23:D8" to "Lenovo",
    "00:23:DF" to "D-Link", "00:23:E4" to "Samsung", "00:23:F8" to "Asus",
    "00:24:01" to "Dell", "00:24:06" to "Xiaomi", "00:24:0B" to "Sony",
    "00:24:21" to "Apple", "00:24:36" to "LG", "00:24:3C" to "Xiaomi",
    "00:24:42" to "HP", "00:24:4B" to "Toshiba", "00:24:4E" to "Canon",
    "00:24:50" to "Intel", "00:24:54" to "Cisco", "00:24:6C" to "TP-Link",
    "00:24:7B" to "Lenovo", "00:24:81" to "Netgear", "00:24:86" to "Xiaomi",
    "00:24:8C" to "Asus", "00:24:93" to "Huawei", "00:24:9B" to "Samsung",
    "00:24:A4" to "Panasonic", "00:24:B2" to "D-Link", "00:24:B6" to "Apple",
    "00:24:C3" to "Xerox", "00:24:D7" to "Xiaomi", "00:24:E0" to "LG",
    "00:24:E6" to "Sony", "00:24:F2" to "Xiaomi", "00:24:FB" to "TP-Link",
    "00:25:00" to "Apple", "00:25:06" to "D-Link", "00:25:11" to "Cisco",
    "00:25:1C" to "Xiaomi", "00:25:22" to "Huawei", "00:25:36" to "Samsung",
    "00:25:3E" to "Lenovo", "00:25:46" to "Hikvision", "00:25:4E" to "Netgear",
    "00:25:53" to "Asus", "00:25:5C" to "Dell", "00:25:64" to "HP",
    "00:25:69" to "Xiaomi", "00:25:6E" to "Intel", "00:25:7C" to "Sony",
    "00:25:86" to "Toshiba", "00:25:90" to "Apple", "00:25:9C" to "TP-Link",
    "00:25:A2" to "Xiaomi", "00:25:B3" to "Linksys", "00:25:B6" to "LG",
    "00:25:C2" to "D-Link", "00:25:CD" to "Panasonic", "00:25:D3" to "Xiaomi",
    "00:25:D8" to "Asus", "00:25:E2" to "Netgear", "00:25:E6" to "Huawei",
    "00:25:EF" to "Samsung", "00:25:F3" to "TP-Link", "00:25:F5" to "Apple",
    "00:26:08" to "Dell", "00:26:0B" to "Lenovo", "00:26:18" to "Intel",
    "00:26:1E" to "Xiaomi", "00:26:24" to "HP", "00:26:2B" to "Cisco",
    "00:26:3A" to "Xerox", "00:26:44" to "Toshiba", "00:26:4D" to "Sony",
    "00:26:50" to "LG", "00:26:55" to "Xiaomi", "00:26:5A" to "TP-Link",
    "00:26:62" to "Panasonic", "00:26:6B" to "D-Link", "00:26:73" to "Huawei",
    "00:26:7C" to "Apple", "00:26:82" to "Asus", "00:26:86" to "Samsung",
    "00:26:90" to "Canon", "00:26:9E" to "Xiaomi", "00:26:A4" to "Dell",
    "00:26:AB" to "Netgear", "00:26:B0" to "Lenovo", "00:26:B8" to "HP",
    "00:26:BB" to "Apple", "00:26:C2" to "Intel", "00:26:C6" to "TP-Link",
    "00:26:D0" to "Cisco", "00:26:D8" to "Sony", "00:26:E0" to "Asus",
    "00:26:E4" to "Xiaomi", "00:26:E8" to "Huawei", "00:26:F0" to "LG",
    "00:26:F2" to "Samsung", "00:26:F7" to "D-Link", "00:26:FB" to "Toshiba",
    "00:27:00" to "Apple", "00:27:0D" to "Xiaomi", "00:27:13" to "Lenovo",
    "00:27:19" to "Dell", "00:27:22" to "Netgear", "00:27:2C" to "HP",
    "00:27:33" to "TP-Link", "00:27:38" to "Intel", "00:27:3E" to "Cisco",
    "00:27:46" to "Panasonic", "00:27:4C" to "Xiaomi", "00:27:52" to "Sony",
    "00:27:58" to "Asus", "00:27:5E" to "D-Link", "00:27:64" to "Huawei",
    "00:27:6A" to "Samsung", "00:27:70" to "LG", "00:27:76" to "Apple",
    "00:27:7C" to "Xerox", "00:27:84" to "Xiaomi", "00:27:8A" to "TP-Link",
    "00:27:90" to "Hikvision", "00:27:96" to "Toshiba", "00:27:9C" to "Dell",
    "00:27:A2" to "Lenovo", "00:27:A8" to "HP", "00:27:AE" to "Intel",
    "00:27:B4" to "Netgear", "00:27:BA" to "Cisco", "00:27:C0" to "Sony",
    "00:27:C6" to "Panasonic", "00:27:CC" to "Asus", "00:27:D2" to "Samsung",
    "00:27:D8" to "D-Link", "00:27:DE" to "LG", "00:27:E4" to "Huawei",
    "00:27:EA" to "Apple", "00:27:F0" to "Xiaomi", "00:27:F6" to "TP-Link",
    "00:27:FC" to "Xiaomi",
    "3C:5A:B4" to "TP-Link", "3C:5A:B5" to "TP-Link",
    "38:83:45" to "TP-Link", "38:83:46" to "TP-Link",
    "38:8B:59" to "Tenda", "38:8B:5A" to "Tenda",
    "8C:A9:82" to "Xiaomi", "8C:A9:83" to "Xiaomi",
    "F0:2F:74" to "TP-Link", "F0:2F:75" to "TP-Link",
    "A0:AB:1B" to "Apple", "A0:AB:1C" to "Apple",
    "AC:BC:32" to "Apple", "AC:BC:33" to "Apple",
    "48:8B:38" to "Xiaomi", "48:8B:39" to "Xiaomi",
    "B0:BE:75" to "LG", "B0:BE:76" to "LG",
    "1C:5B:A9" to "Samsung", "1C:5B:AA" to "Samsung",
    "70:3A:CB" to "Samsung", "70:3A:CC" to "Samsung",
    "5C:C5:D4" to "Apple", "5C:C5:D5" to "Apple",
    "18:2C:8B" to "Xiaomi", "14:58:D0" to "Xiaomi",
    "04:18:D6" to "Apple", "04:18:D7" to "Apple",
    "00:9A:CD" to "Huawei", "0C:37:26" to "Huawei",
    "34:23:87" to "Xiaomi", "20:6B:E7" to "Tenda",
    "E0:55:3D" to "TP-Link", "10:8C:CF" to "LG",
    "54:1F:7A" to "Xiaomi", "34:7E:5C" to "Tenda",
    "24:6F:28" to "Espressif (ESP32)", "30:AE:A4" to "Espressif (ESP32)",
    "A4:CF:12" to "Espressif (ESP32)", "B4:E6:2D" to "Espressif (ESP32)",
    "BC:DD:C2" to "Espressif (ESP8266)", "CC:50:E3" to "Espressif (ESP8266)",
    "D8:F1:5B" to "Espressif", "ECFABC" to "Espressif",
    "A4:2B:B0" to "TP-Link", "C0:25:E9" to "TP-Link", "50:C7:BF" to "TP-Link",
    "98:DA:C4" to "TP-Link", "B0:4E:26" to "TP-Link", "E8:DE:27" to "TP-Link",
    "78:44:76" to "ZTE / Viettel", "CC:7B:35" to "ZTE / Viettel", "34:4B:50" to "ZTE / Viettel",
    "A0:91:C8" to "VNPT iGate", "E4:4E:2D" to "VNPT iGate",
    "74:4D:28" to "MikroTik", "B8:69:F4" to "MikroTik", "CC:2D:E0" to "MikroTik",
    "24:5A:4C" to "Ubiquiti", "78:8A:20" to "Ubiquiti", "FC:EC:DA" to "Ubiquiti"
)

fun readSystemNeighborTable(): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    val macRegex = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
    // 1. Try /proc/net/arp
    try {
        BufferedReader(FileReader("/proc/net/arp")).use { reader ->
            reader.readLine()
            var line = reader.readLine()
            while (line != null) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3].uppercase(Locale.US)
                    if (mac.matches(macRegex) && mac != "00:00:00:00:00:00") {
                        result[ip] = mac
                    }
                }
                line = reader.readLine()
            }
        }
    } catch (_: Exception) {}

    // 2. Fallback to `ip neigh show` for Android 10+ where /proc/net/arp is restricted
    try {
        val proc = Runtime.getRuntime().exec(arrayOf("ip", "neigh", "show"))
        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.isNotEmpty()) {
                    val ip = parts[0]
                    val lladdrIdx = parts.indexOf("lladdr")
                    if (lladdrIdx >= 0 && lladdrIdx + 1 < parts.size) {
                        val mac = parts[lladdrIdx + 1].uppercase(Locale.US)
                        if (mac.matches(macRegex) && mac != "00:00:00:00:00:00") {
                            result[ip] = mac
                        }
                    }
                }
                line = reader.readLine()
            }
        }
    } catch (_: Exception) {}
    return result
}

fun sendWakeOnLanMagicPacket(macAddress: String, subnetBroadcast: String = "255.255.255.255"): Boolean {
    return try {
        val cleanHex = macAddress.replace(":", "").replace("-", "").trim()
        if (cleanHex.length != 12) return false
        val macBytes = ByteArray(6) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        val packetBytes = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packetBytes[i] = 0xFF.toByte()
        for (i in 1..16) {
            System.arraycopy(macBytes, 0, packetBytes, i * 6, 6)
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val addr = InetAddress.getByName(subnetBroadcast)
            socket.send(DatagramPacket(packetBytes, packetBytes.size, addr, 9))
            socket.send(DatagramPacket(packetBytes, packetBytes.size, addr, 7))
        }
        true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun LanScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<LanDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentSubnet by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf<LanDevice?>(null) }

    val nsdManager = remember { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }
    val resolvedNames = remember { ConcurrentHashMap<String, String>() }
    val upnpNames = remember { ConcurrentHashMap<String, String>() }

    val youLabel = stringResource(R.string.lan_scanner_you)

    fun getMacFromArp(ip: String): String? {
        return readSystemNeighborTable()[ip]
    }

    fun lookupBrand(mac: String?): String? {
        if (mac == null) return null
        val normalized = mac.uppercase().replace("-", ":")
        val prefix8 = normalized.take(8)
        if (ouiVendors.containsKey(prefix8)) return ouiVendors[prefix8]
        return null
    }

    fun detectBrandHttp(ip: String, openPorts: List<Int>): String? {
        val portsToCheck = if (80 in openPorts) listOf(80)
        else if (443 in openPorts) listOf(443)
        else listOf(80, 443, 8080, 8008)

        for (port in portsToCheck) {
            try {
                val url = URL("http://$ip:$port/")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 900
                connection.readTimeout = 900
                connection.instanceFollowRedirects = false
                val server = connection.getHeaderField("Server") ?: ""
                val wwwAuth = connection.getHeaderField("WWW-Authenticate") ?: ""
                connection.disconnect()

                val combined = "$server $wwwAuth"
                return when {
                    combined.contains("Viettel", ignoreCase = true) -> "Viettel"
                    combined.contains("VNPT", ignoreCase = true) || combined.contains("iGate", ignoreCase = true) -> "VNPT iGate"
                    combined.contains("FPT", ignoreCase = true) -> "FPT Telecom"
                    combined.contains("TP-Link", ignoreCase = true) || server.contains("TP-LINK", ignoreCase = true) -> "TP-Link"
                    combined.contains("Mercusys", ignoreCase = true) -> "Mercusys"
                    combined.contains("Huawei", ignoreCase = true) -> "Huawei"
                    combined.contains("D-Link", ignoreCase = true) -> "D-Link"
                    combined.contains("Netgear", ignoreCase = true) -> "Netgear"
                    combined.contains("Asus", ignoreCase = true) -> "Asus"
                    combined.contains("Linksys", ignoreCase = true) -> "Linksys"
                    combined.contains("Cisco", ignoreCase = true) -> "Cisco"
                    combined.contains("ZTE", ignoreCase = true) -> "ZTE"
                    combined.contains("MikroTik", ignoreCase = true) -> "MikroTik"
                    combined.contains("Ubiquiti", ignoreCase = true) -> "Ubiquiti"
                    combined.contains("Ruijie", ignoreCase = true) -> "Ruijie"
                    combined.contains("Synology", ignoreCase = true) -> "Synology"
                    combined.contains("QNAP", ignoreCase = true) -> "QNAP"
                    combined.contains("Hikvision", ignoreCase = true) || combined.contains("EZVIZ", ignoreCase = true) -> "Hikvision / Ezviz"
                    combined.contains("Dahua", ignoreCase = true) || combined.contains("Imou", ignoreCase = true) -> "Dahua / Imou"
                    combined.contains("Axis", ignoreCase = true) -> "Axis"
                    combined.contains("Panasonic", ignoreCase = true) -> "Panasonic"
                    combined.contains("Sony", ignoreCase = true) -> "Sony"
                    combined.contains("Samsung", ignoreCase = true) -> "Samsung"
                    combined.contains("LG", ignoreCase = true) -> "LG"
                    combined.contains("Apple", ignoreCase = true) -> "Apple"
                    combined.contains("HP", ignoreCase = true) -> "HP"
                    combined.contains("Canon", ignoreCase = true) -> "Canon"
                    combined.contains("Epson", ignoreCase = true) -> "Epson"
                    combined.contains("Brother", ignoreCase = true) -> "Brother"
                    combined.contains("Xiaomi", ignoreCase = true) -> "Xiaomi"
                    combined.contains("Tenda", ignoreCase = true) -> "Tenda"
                    combined.contains("Mercury", ignoreCase = true) -> "Mercury"
                    combined.contains("Totolink", ignoreCase = true) -> "Totolink"
                    else -> null
                }
            } catch (e: Exception) { continue }
        }
        return null
    }

    fun startScan() {
        if (isScanning) return
        isScanning = true
        progress = 0f
        devices = emptyList()
        resolvedNames.clear()
        upnpNames.clear()

        scope.launch(Dispatchers.IO) {
            val localIp = getLocalIpAddress()
            if (localIp == null) {
                withContext(Dispatchers.Main) { isScanning = false }
                return@launch
            }

            val selfIp = localIp.hostAddress
            val subnet = localIp.hostAddress.substringBeforeLast(".")
            currentSubnet = "$subnet.*"

            val foundDevices = mutableListOf<LanDevice>()
            foundDevices.add(LanDevice(selfIp, youLabel, getMacAddress(), LanDeviceType.PHONE, true))
            withContext(Dispatchers.Main) { devices = foundDevices.toList() }

            launch {
                try {
                    val ssdpRequest = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: ssdp:all\r\n\r\n"
                    val socket = DatagramSocket()
                    socket.soTimeout = 2000
                    val group = InetAddress.getByName("239.255.255.250")
                    val packet = DatagramPacket(ssdpRequest.toByteArray(), ssdpRequest.length, group, 1900)
                    socket.send(packet)

                    val receiveData = ByteArray(1024)
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 3000) {
                        try {
                            val receivePacket = DatagramPacket(receiveData, receiveData.size)
                            socket.receive(receivePacket)
                            val response = String(receivePacket.data, 0, receivePacket.length)
                            val address = receivePacket.address.hostAddress
                            if (address != null) {
                                if (response.contains("LOCATION:", ignoreCase = true)) {
                                    upnpNames[address] = "UPnP Device"
                                }
                            }
                        } catch (e: Exception) { break }
                    }
                    socket.close()
                } catch (e: Exception) {}
            }

            val ndm = nsdManager
            val discoveryListener = if (ndm != null) {
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        ndm.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(si: NsdServiceInfo?, errorCode: Int) {}
                            override fun onServiceResolved(si: NsdServiceInfo) {
                                val host = si.host?.hostAddress
                                if (host != null) resolvedNames[host] = si.serviceName
                            }
                        })
                    }
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }
            } else null

            if (ndm != null && discoveryListener != null) {
                try {
                    ndm.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    ndm.discoverServices("_airplay._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    ndm.discoverServices("_printer._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    ndm.discoverServices("_googlecast._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    ndm.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    ndm.discoverServices("_apple-mobdev2._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    ndm.discoverServices("_adb._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    delay(1000)
                } catch (e: Exception) {}
            }

            val neighborCache = ConcurrentHashMap(readSystemNeighborTable())
            val jobs = mutableListOf<Job>()
            val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            for (i in 1..254) {
                val testIp = "$subnet.$i"
                if (testIp == selfIp) continue

                val job = scanScope.launch {
                    try {
                        val address = InetAddress.getByName(testIp)
                        val reachable = address.isReachable(320) || neighborCache.containsKey(testIp)
                        val openPorts = checkCommonPorts(testIp, if (reachable) 110 else 55)

                        if (reachable || openPorts.isNotEmpty()) {
                            var hostname = resolvedNames[testIp] ?: upnpNames[testIp]
                            if (hostname == null) {
                                try {
                                    val canonical = address.canonicalHostName
                                    if (canonical != testIp) hostname = canonical
                                } catch (e: Exception) {}
                            }

                            val type = guessDeviceType(hostname, testIp, openPorts, i == 1)
                            val isUpnp = upnpNames.containsKey(testIp)
                            val mac = neighborCache[testIp] ?: getMacFromArp(testIp)
                            val brand = mac?.let { lookupBrand(it) } ?: detectBrandHttp(testIp, openPorts)

                            synchronized(foundDevices) {
                                val existingIdx = foundDevices.indexOfFirst { it.ip == testIp }
                                if (existingIdx == -1) {
                                    foundDevices.add(LanDevice(testIp, hostname, mac, type, openPorts = openPorts, isUPnP = isUpnp, brand = brand))
                                } else {
                                    val existing = foundDevices[existingIdx]
                                    foundDevices[existingIdx] = existing.copy(
                                        hostname = hostname ?: existing.hostname,
                                        type = if (type != LanDeviceType.OTHER) type else existing.type,
                                        openPorts = (existing.openPorts + openPorts).distinct(),
                                        mac = mac ?: existing.mac,
                                        brand = brand ?: existing.brand
                                    )
                                }
                            }
                            withContext(Dispatchers.Main) {
                                devices = foundDevices.toList().sortedBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
                            }
                        }
                    } catch (e: Exception) { }

                    synchronized(this) {
                        progress += 1f / 254f
                    }
                }
                jobs.add(job)
                if (i % 25 == 0) delay(60)
            }

            jobs.joinAll()
            // Refresh MAC table once more after ping sweep populates ARP/neighbor table
            val finalNeighbors = readSystemNeighborTable()
            synchronized(foundDevices) {
                for (idx in foundDevices.indices) {
                    val d = foundDevices[idx]
                    val mac = d.mac ?: finalNeighbors[d.ip]
                    val brand = d.brand ?: mac?.let { lookupBrand(it) }
                    if (mac != d.mac || brand != d.brand) {
                        foundDevices[idx] = d.copy(mac = mac, brand = brand)
                    }
                }
            }
            ndm?.let { d -> try { discoveryListener?.let { d.stopServiceDiscovery(it) } } catch (e: Exception) {} }

            withContext(Dispatchers.Main) {
                devices = foundDevices.toList().sortedBy { it.ip.substringAfterLast(".").toIntOrNull() ?: 0 }
                isScanning = false
                progress = 1f
            }
        }
    }

    LaunchedEffect(Unit) {
        startScan()
    }

    selectedDevice?.let { device ->
        DeviceDetailDialog(
            device = device,
            accent = accent,
            onDismiss = { selectedDevice = null },
            onDeviceUpdated = { updated ->
                selectedDevice = updated
                devices = devices.map { if (it.ip == updated.ip) updated else it }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MaterialBackground(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionTopBar(
                    transparent = true,
                    title = stringResource(R.string.lan_scanner_title),
                    onBack = { navController.popBackStack() }
                )

                MaterialCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accent,
                    cornerRadius = 16.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isScanning) stringResource(R.string.lan_scanner_scanning) else stringResource(R.string.lan_scanner_complete),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.lan_scanner_subnet, currentSubnet),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isScanning) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = accent
                                )
                            } else {
                                DolphyIconButton(onClick = { startScan() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = accent)
                                }
                            }
                        }

                        if (isScanning) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = accent,
                                trackColor = accent.copy(alpha = 0.12f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.lan_scanner_found_devices, devices.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                            Text(
                                text = if (AppLocale.isVietnamese(context)) "Chạm vào thiết bị để mở bảng công cụ" else "Tap a device for tools & details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(devices, key = { it.ip }) { device ->
                        DeviceCard(
                            device = device,
                            accent = accent,
                            onClick = { selectedDevice = device }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailDialog(
    device: LanDevice,
    accent: Color,
    onDismiss: () -> Unit,
    onDeviceUpdated: (LanDevice) -> Unit
) {
    val context = LocalContext.current
    val isVi = AppLocale.isVietnamese(context)
    val scope = rememberCoroutineScope()
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }
    var isPortScanning by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(
            context,
            if (isVi) "Đã sao chép: $value" else "Copied: $value",
            Toast.LENGTH_SHORT
        ).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.hostname ?: device.brand ?: device.ip,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append("IP: ${device.ip}")
                                if (device.brand != null) append(" • ${device.brand}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                        if (device.mac != null) {
                            Text(
                                text = "MAC: ${device.mac}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DolphyIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // Quick Copy & Web buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { copyToClipboard("IP", device.ip) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isVi) "Chép IP" else "Copy IP", maxLines = 1)
                    }
                    if (device.mac != null) {
                        FilledTonalButton(
                            onClick = { copyToClipboard("MAC", device.mac) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isVi) "Chép MAC" else "Copy MAC", maxLines = 1)
                        }
                    }
                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                val url = if (443 in device.openPorts && 80 !in device.openPorts) {
                                    "https://${device.ip}"
                                } else {
                                    "http://${device.ip}"
                                }
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Web", maxLines = 1)
                    }
                }

                // Active Diagnostic Actions: Live Ping, Deep Port Scan, Wake-on-LAN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        enabled = !isPinging,
                        onClick = {
                            isPinging = true
                            pingStatus = if (isVi) "Đang đo Ping (4 gói tin)..." else "Pinging (4 packets)..."
                            scope.launch(Dispatchers.IO) {
                                val times = mutableListOf<Long>()
                                repeat(4) {
                                    val t0 = System.nanoTime()
                                    val ok = runCatching {
                                        InetAddress.getByName(device.ip).isReachable(1000) ||
                                            device.openPorts.firstOrNull()?.let { p ->
                                                Socket().use { s ->
                                                    s.connect(InetSocketAddress(device.ip, p), 1000)
                                                    true
                                                }
                                            } == true
                                    }.getOrDefault(false)
                                    val dt = (System.nanoTime() - t0) / 1_000_000L
                                    if (ok) times += dt
                                    delay(150)
                                }
                                withContext(Dispatchers.Main) {
                                    isPinging = false
                                    pingStatus = if (times.isNotEmpty()) {
                                        val avg = times.average().toInt()
                                        val min = times.minOrNull() ?: 0
                                        val max = times.maxOrNull() ?: 0
                                        if (isVi) {
                                            "Phản hồi: ${times.size}/4 gói • Trung bình: ${avg}ms (Min ${min}ms / Max ${max}ms)"
                                        } else {
                                            "Reply: ${times.size}/4 pkts • Avg: ${avg}ms (Min ${min}ms / Max ${max}ms)"
                                        }
                                    } else {
                                        if (isVi) "Không phản hồi ICMP/TCP trong 1000ms" else "No ICMP/TCP reply within 1000ms"
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isVi) "Đo Ping" else "Ping", maxLines = 1)
                    }

                    FilledTonalButton(
                        enabled = !isPortScanning,
                        onClick = {
                            isPortScanning = true
                            scope.launch(Dispatchers.IO) {
                                val allPorts = portServiceNames.keys.toList()
                                val open = coroutineScope {
                                    allPorts.map { port ->
                                        async {
                                            try {
                                                Socket().use { s ->
                                                    s.connect(InetSocketAddress(device.ip, port), 220)
                                                    port
                                                }
                                            } catch (_: Exception) {
                                                null
                                            }
                                        }
                                    }.awaitAll().filterNotNull().sorted()
                                }
                                withContext(Dispatchers.Main) {
                                    isPortScanning = false
                                    onDeviceUpdated(device.copy(openPorts = open))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isPortScanning) (if (isVi) "Đang quét..." else "Scanning...") else (if (isVi) "Quét 25 Cổng" else "Scan Ports"), maxLines = 1)
                    }

                    if (device.mac != null) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val subnet = device.ip.substringBeforeLast(".")
                                    val ok = sendWakeOnLanMagicPacket(device.mac, "$subnet.255")
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            if (ok) {
                                                if (isVi) "Đã gửi gói tin Wake-on-LAN tới ${device.mac}" else "Sent Wake-on-LAN packet to ${device.mac}"
                                            } else {
                                                if (isVi) "Lỗi gửi gói tin Wake-on-LAN" else "Failed to send Wake-on-LAN"
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WoL", maxLines = 1)
                        }
                    }
                }

                if (pingStatus != null) {
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = pingStatus!!,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Open Ports & Services list
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isVi) "Cổng dịch vụ đang mở (${device.openPorts.size})" else "Open Service Ports (${device.openPorts.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        if (device.openPorts.isEmpty()) {
                            Text(
                                text = if (isVi) "Chưa phát hiện cổng mở phổ biến. Bấm «Quét 25 Cổng» để kiểm tra kỹ hơn." else "No common open ports detected. Tap 'Scan Ports' for a deep check.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = device.openPorts.joinToString("  •  ") { p ->
                                    val svc = portServiceNames[p] ?: "TCP"
                                    "$p ($svc)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // If Router or Gateway, embed RouterControlPanel directly!
                if (device.type == LanDeviceType.ROUTER || device.ip.endsWith(".1") || 80 in device.openPorts || 8080 in device.openPorts) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    RouterControlPanel(device = device, accent = accent)
                }
            }
        }
    }
}

@Composable
fun RouterControlPanel(device: LanDevice, accent: Color) {
    val logs = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isVi = AppLocale.isVietnamese(context)
    val routerManager = remember { RouterManager(context) }

    val routerConfig = remember(device.hostname, device.openPorts) {
        routerManager.detectRouter(device.hostname, device.ip, device.openPorts)
    }

    val vendorName = device.brand ?: routerConfig?.vendor ?: if (isVi) "Router / Thiết bị mạng" else "Network Router"

    fun addLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add("[$time] $msg")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.lan_scanner_router_actions),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Black
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.lan_scanner_btn_info),
                icon = Icons.Default.Info,
                color = Color(0xFF2196F3),
                onClick = {
                    scope.launch {
                        addLog(context.getString(R.string.lan_scanner_log_fetching_info))
                        addLog(context.getString(R.string.lan_scanner_log_vendor, vendorName))
                        val cfg = routerConfig
                        if (cfg != null) {
                            val authed = routerManager.authenticate(cfg, device.ip, ::addLog)
                            if (authed) {
                                routerManager.fetchInfo(cfg, device.ip, ::addLog)
                            }
                        } else {
                            addLog(if (isVi) "Đang kiểm tra phản hồi HTTP từ ${device.ip}..." else "Probing HTTP headers from ${device.ip}...")
                            val genericCfg = RouterConfig(
                                vendor = vendorName,
                                detectionKeyword = "",
                                defaultLogins = emptyList(),
                                authEndpoint = "/",
                                kickEndpoint = "",
                                payloadTemplate = "",
                                method = "GET",
                                infoEndpoint = "/"
                            )
                            routerManager.fetchInfo(genericCfg, device.ip, ::addLog)
                        }
                    }
                }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.lan_scanner_btn_reboot),
                icon = Icons.Default.Refresh,
                color = Color(0xFFF44336),
                onClick = {
                    scope.launch {
                        val cfg = routerConfig
                        if (cfg != null) {
                            addLog(context.getString(R.string.lan_scanner_log_reboot_auth, cfg.vendor))
                            val authed = routerManager.authenticate(cfg, device.ip, ::addLog)
                            if (authed) {
                                routerManager.reboot(cfg, device.ip, ::addLog)
                            }
                        } else {
                            addLog(if (isVi) "Đang kiểm tra giao diện quản trị..." else "Checking admin endpoint...")
                            val genericCfg = RouterConfig(
                                vendor = vendorName,
                                detectionKeyword = "",
                                defaultLogins = emptyList(),
                                authEndpoint = "/",
                                kickEndpoint = "",
                                payloadTemplate = "",
                                method = "GET"
                            )
                            val authed = routerManager.authenticate(genericCfg, device.ip, ::addLog)
                            if (!authed) {
                                addLog(if (isVi) "Router yêu cầu đăng nhập trên trình duyệt Web." else "Router requires web authentication.")
                            }
                        }
                    }
                }
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.lan_scanner_btn_wifi),
                icon = Icons.Default.Wifi,
                color = Color(0xFF4CAF50),
                onClick = {
                    scope.launch {
                        addLog(context.getString(R.string.lan_scanner_log_wifi_scanning))
                        val cfg = routerConfig
                        if (cfg != null) {
                            val authed = routerManager.authenticate(cfg, device.ip, ::addLog)
                            if (authed) {
                                routerManager.checkWifi(cfg, device.ip, ::addLog)
                            }
                        } else {
                            addLog(if (isVi) "Bấm nút «Web» phía trên để mở trang cấu hình Wi-Fi." else "Tap 'Web' above to open Wi-Fi settings.")
                        }
                    }
                }
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.lan_scanner_btn_audit),
                icon = Icons.Default.Shield,
                color = Color(0xFFFF9800),
                onClick = {
                    scope.launch {
                        addLog(context.getString(R.string.lan_scanner_log_audit_starting))
                        addLog(if (isVi) "Đang rà soát các cổng bảo mật..." else "Scanning security ports...")
                        withContext(Dispatchers.IO) {
                            val openPorts = checkCommonPorts(device.ip, 200)
                            if (openPorts.isNotEmpty()) {
                                addLog((if (isVi) "Cổng mở: " else "Open ports: ") + openPorts.joinToString(", "))
                                if (openPorts.contains(23)) addLog(if (isVi) "CẢNH BÁO: Cổng Telnet (23) đang MỞ!" else "WARN: Telnet (23) is OPEN!")
                                if (openPorts.contains(8089)) addLog(if (isVi) "CẢNH BÁO: Cổng TR-069 (8089) đang MỞ!" else "WARN: TR-069 (8089) is OPEN!")
                                if (openPorts.contains(21)) addLog(if (isVi) "CẢNH BÁO: Cổng FTP (21) đang MỞ!" else "WARN: FTP (21) is OPEN!")
                            } else {
                                addLog(if (isVi) "Không phát hiện cổng nhạy cảm mở" else "No sensitive ports open")
                            }
                        }
                        addLog(context.getString(R.string.lan_scanner_log_audit_creds))
                        val cfg = routerConfig
                        if (cfg != null) {
                            routerManager.authenticate(cfg, device.ip, ::addLog)
                        } else {
                            val genericCfg = RouterConfig(
                                vendor = vendorName,
                                detectionKeyword = "",
                                defaultLogins = emptyList(),
                                authEndpoint = "/",
                                kickEndpoint = "",
                                payloadTemplate = "",
                                method = "GET"
                            )
                            routerManager.authenticate(genericCfg, device.ip, ::addLog)
                        }
                        addLog(context.getString(R.string.lan_scanner_log_audit_wps))
                        addLog(context.getString(R.string.lan_scanner_log_audit_warn_wps))
                    }
                }
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            color = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
        ) {
            LazyColumn(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                reverseLayout = true
            ) {
                items(logs.reversed()) { log ->
                    Text(log, color = Color(0xFF00FF41), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp)
                }
                if (logs.isEmpty()) {
                    item { Text(stringResource(R.string.lan_scanner_ready), color = Color.Gray, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .clickable { onClick() },
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun DeviceCard(
    device: LanDevice,
    accent: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isVi = AppLocale.isVietnamese(context)
    val (icon, iconColor) = when (device.type) {
        LanDeviceType.PC -> Icons.Default.Computer to Color(0xFF2196F3)
        LanDeviceType.PHONE -> Icons.Default.PhoneAndroid to Color(0xFF4CAF50)
        LanDeviceType.TV -> Icons.Default.Tv to Color(0xFFFF9800)
        LanDeviceType.CAMERA -> Icons.Default.Videocam to Color(0xFFF44336)
        LanDeviceType.PRINTER -> Icons.Default.Print to Color(0xFF9C27B0)
        LanDeviceType.ROUTER -> Icons.Default.Router to Color(0xFF607D8B)
        LanDeviceType.OTHER -> Icons.Default.DevicesOther to Color(0xFF9E9E9E)
    }

    MaterialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        accentColor = if (device.isSelf) accent else accent.copy(alpha = 0.4f),
        cornerRadius = 16.dp,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.hostname ?: device.brand ?: device.ip,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    if (device.isSelf) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = accent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isVi) "BẠN" else "YOU",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = buildString {
                        append(device.ip)
                        if (device.mac != null) append(" • ${device.mac}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (device.brand != null) {
                    Text(
                        text = device.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (device.openPorts.isNotEmpty()) {
                    Text(
                        text = (if (isVi) "Cổng mở: " else "Ports: ") + device.openPorts.joinToString(", ") { p ->
                            portServiceNames[p]?.let { "$p ($it)" } ?: p.toString()
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent.copy(alpha = 0.75f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun getLocalIpAddress(): InetAddress? {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress) {
                    val sAddr = addr.hostAddress
                    if (sAddr.contains(":")) continue
                    return addr
                }
            }
        }
    } catch (ex: Exception) { }
    return null
}

private fun getMacAddress(): String? {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val mac = intf.hardwareAddress ?: continue
            val buf = StringBuilder()
            for (b in mac) buf.append(String.format("%02X:", b))
            if (buf.isNotEmpty()) buf.deleteCharAt(buf.length - 1)
            return buf.toString()
        }
    } catch (ex: Exception) { }
    return null
}

private fun checkCommonPorts(ip: String, timeout: Int): List<Int> {
    val portsToCheck = listOf(
        80, 443,
        22, 23,
        135, 445,
        554, 8000,
        3389,
        5555,
        8008, 8009, 8080,
        8089,
        9100,
        62078
    )
    val openPorts = mutableListOf<Int>()
    for (port in portsToCheck) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                openPorts.add(port)
            }
        } catch (e: Exception) {}
    }
    return openPorts
}

private fun guessDeviceType(hostname: String?, ip: String, openPorts: List<Int>, isFirstIp: Boolean): LanDeviceType {
    val h = hostname?.lowercase() ?: ""
    val ports = openPorts.toSet()

    if (isFirstIp || (ports.contains(80) && ip.endsWith(".1")) || ports.contains(8089)) return LanDeviceType.ROUTER

    if (ports.contains(445) || ports.contains(135) || ports.contains(3389) ||
        h.contains("desktop") || h.contains("laptop") || h.contains("pc") || h.contains("macbook") || h.contains("win")) {
        return LanDeviceType.PC
    }

    if (ports.contains(62078) || ports.contains(8008) || ports.contains(8009) || ports.contains(5555) ||
        h.contains("android") || h.contains("phone") || h.contains("iphone") || h.contains("pixel") ||
        h.contains("galaxy") || h.contains("huawei") || h.contains("xiaomi") || h.contains("samsung") ||
        h.contains("oppo") || h.contains("vivo") || h.contains("realme")) {
        return LanDeviceType.PHONE
    }

    if (h.contains("tv") || h.contains("smarttv") || h.contains("bravia") || h.contains("chromecast") ||
        h.contains("tcl") || h.contains("hisense") || h.contains("kodi") || h.contains("firestick")) {
        return LanDeviceType.TV
    }

    if (ports.contains(554) || ports.contains(8000) || h.contains("camera") || h.contains("cam") ||
        h.contains("hikvision") || h.contains("dahua") || h.contains("ezviz") || h.contains("imou")) {
        return LanDeviceType.CAMERA
    }

    if (ports.contains(9100) || h.contains("printer") || h.contains("hp") || h.contains("epson") || h.contains("canon") || h.contains("brother")) {
        return LanDeviceType.PRINTER
    }

    return LanDeviceType.OTHER
}

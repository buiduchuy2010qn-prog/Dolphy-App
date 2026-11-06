package com.droid.dolphy.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class WhatsNewItem(
    val icon: ImageVector,
    val titleVi: String,
    val titleEn: String,
    val descVi: String,
    val descEn: String,
)

fun whatsNewItems(): List<WhatsNewItem> = listOf(
    WhatsNewItem(
        icon = Icons.Default.SystemUpdate,
        titleVi = "Cập nhật OTA",
        titleEn = "OTA updates",
        descVi = "Tự kiểm tra GitHub Releases, tải APK và cài đè ngay trong app.",
        descEn = "Checks GitHub Releases, downloads the APK, and installs over the current app.",
    ),
    WhatsNewItem(
        icon = Icons.Default.Router,
        titleVi = "LAN 9 tầng",
        titleEn = "9-layer LAN names",
        descVi = "Dò tên thiết bị qua DNS router, mDNS, AirPlay, Google Cast, UPnP và NetBIOS.",
        descEn = "Resolves device names via router DNS, mDNS, AirPlay, Google Cast, UPnP, and NetBIOS.",
    ),
    WhatsNewItem(
        icon = Icons.Default.Wifi,
        titleVi = "Thông số Wi-Fi sống",
        titleEn = "Live Wi-Fi stats",
        descVi = "dBm, Mbps, kênh 2.4/5 GHz, WPA2/WPA3 và ping Router / 1.1.1.1 / 8.8.8.8.",
        descEn = "dBm, Mbps, 2.4/5 GHz channel, WPA2/WPA3, and ping to router / 1.1.1.1 / 8.8.8.8.",
    ),
    WhatsNewItem(
        icon = Icons.Default.Lock,
        titleVi = "PIN & ngụy trang icon",
        titleEn = "PIN & icon cloak",
        descVi = "Khóa PIN 4–8 số; đổi icon màn hình chính thành Dolphy VN, Máy tính hoặc Ghi chú.",
        descEn = "4–8 digit PIN lock; cloak the launcher icon as Dolphy VN, Calculator, or Notes.",
    ),
    WhatsNewItem(
        icon = Icons.Default.Bolt,
        titleVi = "120Hz & chữ ký cố định",
        titleEn = "120Hz & fixed signing",
        descVi = "Ưu tiên tần số quét cao; cùng một chữ ký để cài đè các bản sau không bị lỗi gói.",
        descEn = "Prefers high refresh rate; a fixed signing key so later builds can overwrite-install.",
    ),
)

@Composable
fun WhatsNewDialog(
    versionName: String,
    isVi: Boolean,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isVi) "Có gì mới" else "What's new",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Dolphy VN $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (isVi)
                        "Bản nâng cấp tiện ích: chẩn đoán mạng, bảo mật app và cập nhật tại chỗ."
                    else
                        "Utility upgrade: network diagnostics, app lock, and in-app updates.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                whatsNewItems().forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isVi) item.titleVi else item.titleEn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isVi) item.descVi else item.descEn,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isVi) "Bắt đầu dùng" else "Get started")
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

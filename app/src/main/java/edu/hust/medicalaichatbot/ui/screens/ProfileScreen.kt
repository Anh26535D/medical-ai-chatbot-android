package edu.hust.medicalaichatbot.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.hust.medicalaichatbot.domain.model.UserProfile
import edu.hust.medicalaichatbot.domain.model.IoTData
import edu.hust.medicalaichatbot.ui.theme.*
import edu.hust.medicalaichatbot.ui.viewmodel.ProfileViewModel
import edu.hust.medicalaichatbot.ui.viewmodel.AuthViewModel
import edu.hust.medicalaichatbot.ui.viewmodel.AuthState
import edu.hust.medicalaichatbot.ui.viewmodel.IoTViewModel
import java.util.Locale

@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    iotViewModel: IoTViewModel = viewModel(),
    onLoginClick: () -> Unit = {}
) {
    val userProfiles by profileViewModel.userProfiles.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val iotData by iotViewModel.iotData.collectAsState()
    val isGuest = authState is AuthState.Guest
    
    var editingProfileId by remember { mutableStateOf<Int?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Hồ sơ sức khỏe",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = "Quản lý và cập nhật thông tin y tế gia đình.",
            fontSize = 14.sp,
            color = TextGray,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isGuest) {
            ProfileGuestPlaceholder(onLoginClick = onLoginClick)
        } else {
            Text(
                text = "DANH SÁCH HỒ SƠ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextGray
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            userProfiles.forEach { profile ->
                val isEditing = editingProfileId == profile.id || (profile.isInitial && profile.isPrimary && !isAddingNew)
                
                if (isEditing) {
                    EditProfileCard(
                        profile = profile,
                        isPrimary = profile.isPrimary,
                        onSave = { name, birthYear, gender, conditions ->
                            profileViewModel.updateProfile(profile.id, name, birthYear, gender, conditions)
                            editingProfileId = null
                        },
                        onCancel = { editingProfileId = null }
                    )
                } else {
                    MainProfileCard(
                        profile = profile,
                        isPrimary = profile.isPrimary,
                        onEditClick = { editingProfileId = profile.id },
                        onUpdateConditions = { newConditions ->
                            profileViewModel.updateProfile(
                                profile.id,
                                profile.name, 
                                profile.birthYear, 
                                profile.gender, 
                                newConditions
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isAddingNew) {
                EditProfileCard(
                    profile = UserProfile(name = "", isInitial = false),
                    onSave = { name, birthYear, gender, conditions ->
                        profileViewModel.addProfile(name, birthYear, gender, conditions)
                        isAddingNew = false
                    },
                    onCancel = { isAddingNew = false }
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { isAddingNew = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F7FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Thêm hồ sơ mới", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // IoT Monitoring Section
            IoTMonitoringSection(iotData, iotViewModel)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Info card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PrimaryBlue.copy(alpha = 0.1f)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Quản lý gia đình", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bạn có thể thêm hồ sơ cho con cái hoặc người thân lớn tuổi để nhận được tư vấn sức khỏe phù hợp hơn cho cả nhà.",
                            fontSize = 13.sp,
                            color = Color.DarkGray,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IoTMonitoringSection(data: IoTData, viewModel: IoTViewModel) {
    val connectionState by viewModel.bleConnectionState.collectAsState()
    val deviceAddress by viewModel.connectedDeviceAddress.collectAsState()
    val provisioningStatus by viewModel.provisioningStatus.collectAsState()
    val suggestedSsid by viewModel.currentSsid.collectAsState()
    
    var wifiSsid by remember(suggestedSsid) { mutableStateOf(suggestedSsid) }
    var wifiPass by remember { mutableStateOf("") }
    var showWifiConfig by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "THIẾT BỊ ĐEO (${deviceAddress ?: "1020BA49D1C8"})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextGray
                )
                Text(
                    text = when(connectionState) {
                        android.bluetooth.BluetoothProfile.STATE_CONNECTED -> "Đã kết nối BLE"
                        android.bluetooth.BluetoothProfile.STATE_CONNECTING -> "Đang kết nối..."
                        else -> if (deviceAddress != null) "Đã nhận diện (Cloud)" else "Dữ liệu mặc định (Cloud)"
                    },
                    fontSize = 10.sp,
                    color = if (connectionState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) SuccessGreen else TextGray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (connectionState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                    Text(
                        text = "KẾT NỐI BLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier
                            .clickable { viewModel.connectBle() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else if (connectionState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                    IconButton(onClick = { showWifiConfig = !showWifiConfig }) {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = "WiFi Config",
                            tint = if (showWifiConfig) PrimaryBlue else TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(16.dp).clickable { viewModel.disconnectBle() }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = data.status, hasFinger = data.hasFinger)
            }
        }
        
        // WiFi Configuration Panel
        if (connectionState == android.bluetooth.BluetoothProfile.STATE_CONNECTED && showWifiConfig) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cài đặt WiFi cho thiết bị",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Text(
                        text = "Gửi thông tin WiFi để thiết bị tự động cập nhật dữ liệu lên Cloud.",
                        fontSize = 12.sp,
                        color = TextGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("Tên WiFi (SSID)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = wifiPass,
                        onValueChange = { wifiPass = it },
                        label = { Text("Mật khẩu", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = { viewModel.sendWifiCredentials(wifiSsid, wifiPass) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = provisioningStatus != "SENDING"
                    ) {
                        if (provisioningStatus == "SENDING") {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Gửi cấu hình")
                        }
                    }
                    
                    if (provisioningStatus != null) {
                        val (statusText, statusColor) = when(provisioningStatus) {
                            "SUCCESS" -> "Đã gửi thành công!" to SuccessGreen
                            "FAILED" -> "Gửi thất bại. Thử lại?" to Color.Red
                            "NOT_FOUND" -> "Không tìm thấy dịch vụ provisioning" to Color.Red
                            else -> "Đang gửi..." to PrimaryBlue
                        }
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    VitalHeader(icon = Icons.Default.Favorite, label = "Nhịp tim", unit = "BPM", iconColor = Color.Red)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (data.heartRate > 0) "${data.heartRate}" else "--",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        if (data.heartRateAvg > 0) {
                            Text(
                                text = "/avg ${data.heartRateAvg}",
                                fontSize = 12.sp,
                                color = TextGray,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HeartRateChart(
                        data = data.heartRateHistory,
                        modifier = Modifier.fillMaxWidth().height(30.dp)
                    )
                }
            }
            
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    VitalHeader(icon = Icons.Default.Opacity, label = "Oxy máu", unit = "SpO2", iconColor = Color(0xFF2196F3))
                    Text(
                        text = if (data.spo2 > 0) "${data.spo2}%" else "--",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (data.spo2 >= 95) "Bình thường" else if (data.spo2 > 0) "Cần chú ý" else "Chưa có dữ liệu",
                        fontSize = 11.sp,
                        color = if (data.spo2 >= 95) SuccessGreen else if (data.spo2 > 0) Color(0xFFFBC02D) else TextGray
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EnvironmentInfoItem(
                    icon = Icons.Default.Thermostat,
                    label = "Nhiệt độ phòng",
                    value = String.format(Locale.US, "%.1f°C", data.temperature),
                    color = Color(0xFFFF7043)
                )
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(SurfaceGray))
                EnvironmentInfoItem(
                    icon = Icons.Default.WaterDrop,
                    label = "Độ ẩm phòng",
                    value = String.format(Locale.US, "%.0f%%", data.humidity),
                    color = Color(0xFF29B6F6)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, hasFinger: Boolean) {
    val (text, color) = when {
        !hasFinger && status != "IDLE" -> "KHÔNG CHẠM" to Color.Gray
        status == "MEASURING" -> "ĐANG ĐO" to PrimaryBlue
        status == "COMPLETED" -> "HOÀN TẤT" to SuccessGreen
        else -> "CHỜ" to TextGray
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun VitalHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, unit: String, iconColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = unit, fontSize = 9.sp, color = iconColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EnvironmentInfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = TextGray)
        }
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}

@Composable
fun HeartRateChart(data: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        val width = size.width
        val height = size.height
        if (data.size < 2) {
            drawCircle(color = Color.Red, radius = 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width/2, height/2))
            return@Canvas
        }
        val path = Path()
        val maxHr = (data.maxOrNull() ?: 100).coerceAtLeast(100).toFloat()
        val minHr = (data.minOrNull() ?: 60).coerceAtMost(60).toFloat()
        val range = (maxHr - minHr).coerceAtLeast(20f)
        val xStep = width / (data.size - 1)
        data.forEachIndexed { index, hr ->
            val x = index * xStep
            val y = height - ((hr - minHr) / range * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = Color.Red, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun EditProfileCard(
    profile: UserProfile,
    isPrimary: Boolean = false,
    onSave: (String, String, String, List<String>) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var birthYear by remember { mutableStateOf(profile.birthYear) }
    var gender by remember { mutableStateOf(profile.gender) }
    var conditionsInput by remember { mutableStateOf(profile.conditions.joinToString(", ")) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (profile.isInitial && isPrimary) "Nhập thông tin của bạn" 
                       else if (isPrimary) "Chỉnh sửa hồ sơ của bạn"
                       else if (profile.name.isEmpty()) "Thêm hồ sơ mới" 
                       else "Chỉnh sửa hồ sơ người thân", 
                fontWeight = FontWeight.Bold, 
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (!isPrimary) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Họ và tên") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedTextField(value = birthYear, onValueChange = { birthYear = it }, label = { Text("Năm sinh") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Giới tính") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = conditionsInput, onValueChange = { conditionsInput = it }, label = { Text("Bệnh nền (cách nhau bởi dấu phẩy)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!profile.isInitial) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Hủy") }
                }
                Button(
                    onClick = {
                        val conditions = conditionsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        onSave(name, birthYear, gender, conditions)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Lưu hồ sơ") }
            }
        }
    }
}

@Composable
fun MainProfileCard(
    profile: UserProfile,
    isPrimary: Boolean = true,
    onEditClick: () -> Unit,
    onUpdateConditions: (List<String>) -> Unit
) {
    var isEditingConditions by remember { mutableStateOf(false) }
    var conditionsInput by remember { mutableStateOf(profile.conditions.joinToString(", ")) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = if (isPrimary) PrimaryBlue else Color(0xFF81C784)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (isPrimary) Icons.Default.Person else Icons.Default.Face, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = if (isPrimary) (if (profile.name.isEmpty() || profile.name == "Tôi") "Chủ tài khoản" else profile.name) else profile.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (!isPrimary) Text(text = "Người phụ thuộc", fontSize = 12.sp, color = TextGray)
                }
                IconButton(onClick = onEditClick) { Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = TextGray, modifier = Modifier.size(20.dp)) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBox(label = "Năm sinh", value = profile.birthYear, subValue = "(${profile.age} tuổi)", modifier = Modifier.weight(1f))
                InfoBox(label = "Giới tính", value = profile.gender, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Surface(modifier = Modifier.fillMaxWidth(), color = SurfaceGray.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalServices, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Bệnh nền", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = if (isEditingConditions) "Lưu" else "Cập nhật", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { 
                            if (isEditingConditions) {
                                val conditions = conditionsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                onUpdateConditions(conditions)
                                isEditingConditions = false
                            } else isEditingConditions = true
                        })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isEditingConditions) {
                        OutlinedTextField(value = conditionsInput, onValueChange = { conditionsInput = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Nhập bệnh nền...", fontSize = 12.sp) }, shape = RoundedCornerShape(8.dp), colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White))
                    } else {
                        if (profile.conditions.isEmpty()) Text(text = "Chưa có thông tin bệnh nền", fontSize = 12.sp, color = TextGray)
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { profile.conditions.forEach { DiseaseTag(it) } }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBox(label: String, value: String, subValue: String? = null, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = Color(0xFFF5F7FA)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontSize = 12.sp, color = TextGray)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (subValue != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = subValue, fontSize = 12.sp, color = TextGray, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}

@Composable
fun DiseaseTag(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = SuccessGreen.copy(alpha = 0.1f), border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.2f))) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileGuestPlaceholder(onLoginClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(80.dp), tint = TextGray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Đăng nhập để quản lý hồ sơ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = "Lưu trữ thông tin y tế để nhận tư vấn chính xác hơn.", fontSize = 14.sp, color = TextGray, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onLoginClick, modifier = Modifier.padding(top = 16.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) { Text("Đăng nhập ngay") }
    }
}

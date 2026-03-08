package com.designategold7.arkansasliteracyengine.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ArkansasCardinal = Color(0xFF990000)
val ArkansasSlate = Color(0xFF475569)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboard(
    isNodeOnline: Boolean,
    onExportLacesClicked: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fleet Command: Jonesboro Hub", fontWeight = FontWeight.Bold, color = ArkansasCardinal) },
                actions = {
                    TextButton(onClick = onLogout) { Text("Secure Logout", color = ArkansasSlate) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF1F5F9)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("DGX Spark Cluster Health", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ArkansasSlate)
            Spacer(modifier = Modifier.height(16.dp))

            // Remote Health Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                // Node Status
                item {
                    HealthCard(
                        title = "mDNS Link Status",
                        value = if (isNodeOnline) "ACTIVE" else "SEARCHING",
                        icon = if (isNodeOnline) Icons.Default.Link else Icons.Default.LinkOff,
                        statusColor = if (isNodeOnline) Color(0xFF16A34A) else ArkansasCardinal
                    )
                }
                // Server Load
                item {
                    HealthCard(
                        title = "Spark NPU Load",
                        value = if (isNodeOnline) "18.4%" else "--",
                        icon = Icons.Default.Memory,
                        statusColor = ArkansasSlate
                    )
                }
                // Temperature
                item {
                    HealthCard(
                        title = "Core Temp (Dell R760)",
                        value = if (isNodeOnline) "42°C" else "--",
                        icon = Icons.Default.Thermostat,
                        statusColor = if (isNodeOnline) Color(0xFFD97706) else ArkansasSlate
                    )
                }
                // Active Tablets
                item {
                    HealthCard(
                        title = "Active Fleet",
                        value = if (isNodeOnline) "24 Units" else "0 Units",
                        icon = Icons.Default.TabletMac,
                        statusColor = ArkansasSlate
                    )
                }
                // Sync Queue
                item {
                    HealthCard(
                        title = "LACES Sync Queue",
                        value = "14 Pending",
                        icon = Icons.Default.Sync,
                        statusColor = Color(0xFF2563EB)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // LACES Export Hook
            Button(
                onClick = onExportLacesClicked,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArkansasCardinal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Export")
                Spacer(Modifier.width(8.dp))
                Text("Compile & Export LACES/NRS Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun HealthCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, statusColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontSize = 12.sp, color = ArkansasSlate, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
        }
    }
}
package com.kalash.assignment_2

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import com.kalash.assignment_2.ui.theme.Assignment2Theme
import com.kalash.assignment_2.ViewModel.HealthConnectViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: HealthConnectViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EmptyScreen()
                }
            }
        }
    }
}

@Composable
fun EmptyScreen() {
    Text("Assignment 2 - Health Connect")
}

@Composable
fun MainScreen(viewModel: HealthConnectViewModel) {
    val context = LocalContext.current
    var healthConnectClient by remember { mutableStateOf<HealthConnectClient?>(null) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val permissions = remember {
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        scope.launch {
            try {
                val grantedPermissions = healthConnectClient?.permissionController?.getGrantedPermissions()
                permissionsGranted = grantedPermissions?.containsAll(permissions) == true
            } catch (e: Exception) {
                showError = "Error checking permissions: ${e.message}"
                Log.e("MainScreen", "Error checking permissions", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            healthConnectClient = HealthConnectClient.getOrCreate(context)
            val granted = healthConnectClient?.permissionController?.getGrantedPermissions()
            permissionsGranted = granted?.containsAll(permissions) == true
        } catch (e: Exception) {
            showError = "Error: ${e.message}"
            Log.e("MainScreen", "Error initializing Health Connect", e)
        }
    }

    when {
        healthConnectClient == null -> {
            LoadingScreen()
        }
        !permissionsGranted -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Health Connect permissions are required")
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            showError = "Error opening Health Connect settings: ${e.message}"
                            Log.e("MainScreen", "Error opening settings", e)
                        }
                    }
                ) {
                    Text("Open Health Connect Settings")
                }
            }
        }
        else -> {
            healthConnectClient?.let { client ->
                HeartRateScreen(client, viewModel)
            }
        }
    }

    // Error dialog
    showError?.let { error ->
        AlertDialog(
            onDismissRequest = { showError = null },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = { showError = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Initializing Health Connect...")
    }
}

@Composable
fun HeartRateScreen(
    healthConnectClient: HealthConnectClient,
    viewModel: HealthConnectViewModel
) {
    var heartRate by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val heartRateRecords by viewModel.heartRateRecords.collectAsState()
    val error by viewModel.error.collectAsState()
    val scope = rememberCoroutineScope()
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Input Fields
        OutlinedTextField(
            value = heartRate,
            onValueChange = { if (it.isEmpty() || it.toIntOrNull() in 1..300) heartRate = it },
            label = { Text("Heart Rate (1-300 bpm)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = errorMessage?.contains("heart rate") == true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dateTime,
            onValueChange = { dateTime = it },
            label = { Text("Date/Time (yyyy-MM-dd HH:mm)") },
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage?.contains("date/time") == true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { scope.launch { viewModel.loadHeartRates(healthConnectClient) } },
                modifier = Modifier.weight(1f)
            ) { Text("Load") }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val heartRateValue = heartRate.toIntOrNull()
                            val parsedDateTime = try {
                                LocalDateTime.parse(dateTime, dateTimeFormatter)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                            } catch (e: Exception) {
                                errorMessage = "Invalid date/time format. Use yyyy-MM-dd HH:mm"
                                null
                            }
                            
                            when {
                                heartRateValue == null -> errorMessage = "Please enter a valid heart rate"
                                parsedDateTime == null -> errorMessage = "Invalid date/time format"
                                else -> {
                                    viewModel.saveHeartRate(healthConnectClient, heartRateValue, parsedDateTime)
                                    heartRate = ""
                                    dateTime = ""
                                    errorMessage = null
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Save") }
        }

        // Heart Rate History
        Text(
            "Heart Rate History",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            if (heartRateRecords.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No heart rate records found")
                    }
                }
            } else {
                items(heartRateRecords) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Heart Rate: ${record.samples.firstOrNull()?.beatsPerMinute ?: "N/A"} bpm")
                            Text("Time: ${record.startTime.atZone(ZoneId.systemDefault()).format(dateTimeFormatter)}")
                        }
                    }
                }
            }
        }

        // About Section
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Student Name: Kalash Rami")
                Text("Student ID: 301475553")
            }
        }
    }

    // Error Dialog
    error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(errorMsg) },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }
}
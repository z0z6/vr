package com.example.vrplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Scheme = darkColorScheme(
    primary = Color(0xFF8AA4FF),
    onPrimary = Color(0xFF0B1030),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF151922),
    onSurface = Color(0xFFE6E9F2),
    onBackground = Color(0xFFE6E9F2)
)

class MainActivity : ComponentActivity() {

    private fun open(uri: Uri) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Scheme) {
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri -> if (uri != null) open(uri) }

                HomeScreen(
                    onPickFile = { picker.launch(arrayOf("video/*")) },
                    onOpenUrl = { open(Uri.parse(it.trim())) }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(onPickFile: () -> Unit, onOpenUrl: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val validUrl = url.trim().startsWith("http://") || url.trim().startsWith("https://")

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF16204A), Color(0xFF0B0D12)))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("VR Player", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                "Wideo 360° w goglach Cardboard — z żyroskopem i korekcją soczewek.",
                color = Color(0xFFB4BBD0), fontSize = 16.sp
            )

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Wybierz plik wideo", fontSize = 16.sp) }

                    HorizontalDivider(color = Color(0x22FFFFFF))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("lub wklej adres URL (https://…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (validUrl) onOpenUrl(url) })
                    )
                    FilledTonalButton(
                        onClick = { onOpenUrl(url) },
                        enabled = validUrl,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Odtwórz z URL") }
                }
            }

            Text(
                "W odtwarzaczu: dotyk = pauza (tryb VR), długie przytrzymanie = menu.",
                color = Color(0xFF8189A3), fontSize = 13.sp
            )
        }
    }
}

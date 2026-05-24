package com.andyxu.readmd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyxu.readmd.ui.theme.ReadMDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val elderMode = remember { mutableStateOf(false) }
            ReadMDTheme(elderMode = elderMode.value) {
                ReadMDApp(
                    elderMode = elderMode.value,
                    onToggleElderMode = { elderMode.value = !elderMode.value },
                )
            }
        }
    }
}

@Composable
fun ReadMDApp(
    elderMode: Boolean,
    onToggleElderMode: () -> Unit,
) {
    val bodySize = if (elderMode) 22.sp else 16.sp
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = "ReadMD",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "本地 Android Markdown 备忘录。下一阶段将接入文件选择、编辑、预览和导出。",
                fontSize = bodySize,
                lineHeight = if (elderMode) 34.sp else 24.sp,
            )
            Button(onClick = onToggleElderMode) {
                Text(if (elderMode) "关闭大字模式" else "开启大字模式")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadMDAppPreview() {
    ReadMDTheme {
        ReadMDApp(
            elderMode = false,
            onToggleElderMode = {},
        )
    }
}


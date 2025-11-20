package com.example.testgemini

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testgemini.ui.theme.TestGeminiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    ScreenContent()
}

@Composable
private fun ScreenContent(){
    TestGeminiTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                item {
                    Text(
                        text = "テーブルの前のアイテム",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                item {
                    BigTable()
                }
                item {
                    Text(
                        text = "テーブルの後のアイテム",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.greeting, name),
        modifier = modifier
    )
}

@Composable
fun BigTable() {
    LazyRow(modifier = Modifier.padding(start = 16.dp)) {
        item {
            Column(modifier = Modifier.padding(end = 16.dp)) {
                // Header
                Row {
                    for (i in 0..30) {
                        TableCell(text = "Header $i")
                    }
                }
                // Rows
                for (iRow in 0..200) {
                    Row {
                        for (iCol in 0..30) {
                            TableCell(text = "Item $iRow-$iCol")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .border(1.dp, Color.Gray)
            .padding(8.dp)
            .width(100.dp)
    )
}

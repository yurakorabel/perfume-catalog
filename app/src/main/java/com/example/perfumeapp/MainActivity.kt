package com.example.perfumeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import org.json.JSONArray
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment

import com.example.perfumeapp.ui.theme.PerfumeAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Callback
import retrofit2.Response
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PerfumeAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var perfumeList by remember { mutableStateOf(emptyList<Perfume>()) }
                    var isLoading by remember { mutableStateOf(true) } // Track loading state
                    LaunchedEffect(key1 = Unit) {
                        fetchPerfumes { perfumes ->
                            perfumeList = perfumes
                            isLoading = false // Set loading state to false when data is loaded
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        UpdateDataButton {
                            isLoading = true // Set loading state to true when updating data
                            fetchPerfumes { perfumes ->
                                perfumeList = perfumes
                                isLoading = false // Set loading state to false when data is loaded
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isLoading) {
                            Loader() // Show loader if data is still loading
                        } else {
                            PerfumeCatalog(perfumeList)
                        }
                    }
                }
            }
        }
    }

    private fun fetchPerfumes(onPerfumesFetched: (List<Perfume>) -> Unit) {
        val apiUrl = "https://69e3ce12jh.execute-api.eu-central-1.amazonaws.com/TEST/all-parfumes"
        // Use Kotlin Coroutine for asynchronous networking
        // This coroutine will run on the IO dispatcher
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = fetchApiResponse(apiUrl)
                val perfumes = parseJsonResponse(response)
                // Switch to Main dispatcher to update UI
                withContext(Dispatchers.Main) {
                    onPerfumesFetched(perfumes)
                }
            } catch (e: Exception) {
                // Handle errors here
                e.printStackTrace()
            }
        }
    }

    private fun fetchApiResponse(apiUrl: String): String {
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            connection.disconnect()
            return response.toString()
        } else {
            // Handle error response
            throw Exception("Failed to fetch data from API. Response code: $responseCode")
        }
    }

    private fun parseJsonResponse(jsonResponse: String): List<Perfume> {
        val perfumeList = mutableListOf<Perfume>()
        try {
            val jsonObject = JSONObject(jsonResponse)
            val body = jsonObject.getString("body")
            val jsonArray = JSONArray(body)
            for (i in 0 until jsonArray.length()) {
                val perfumeJson = jsonArray.getJSONObject(i)
                val perfume = Perfume(
                    name = perfumeJson.getString("name"),
                    brand = perfumeJson.getString("brand"),
                    gender = perfumeJson.getString("gender"),
                    scentNotes = perfumeJson.getString("fragrance_notes"),
                    price = perfumeJson.getDouble("price").toInt(),
                    volume = perfumeJson.getString("bottle_size"),
                    releaseYear = perfumeJson.getInt("release_year"),
                    photoUrl = perfumeJson.getString("image_url")
                )
                perfumeList.add(perfume)
            }
        } catch (e: JSONException) {
            Log.e("JSON_PARSE_ERROR", "Error parsing JSON response: ${e.message}")
        }
        return perfumeList
    }
}

@Composable
fun Loader() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun UpdateDataButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        content = { Text("Update Data") }
    )
}

data class Perfume(
    val name: String,
    val brand: String,
    val gender: String,
    val scentNotes: String,
    val price: Int,
    val volume: String,
    val releaseYear: Int,
    val photoUrl: String
)

@Composable
fun PerfumeCatalog(perfumes: List<Perfume>) {
    LazyColumn {
        items(perfumes.size) { index ->
            PerfumeItem(perfumes[index])
        }
    }
}

@Composable
fun PerfumeItem(perfume: Perfume) {
    Card(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Image(
                painter = rememberImagePainter(perfume.photoUrl),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Text(text = "Name: ${perfume.name}")
            Text(text = "Brand: ${perfume.brand}")
            Text(text = "Gender: ${perfume.gender}")
            Text(text = "Scent Notes: ${perfume.scentNotes}")
            Text(text = "Price: ${perfume.price}")
            Text(text = "Volume: ${perfume.volume}")
            Text(text = "Release Year: ${perfume.releaseYear}")

        }
    }
}

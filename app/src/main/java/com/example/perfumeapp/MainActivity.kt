package com.example.perfumeapp

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberImagePainter
import com.example.perfumeapp.ui.theme.PerfumeAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import androidx.fragment.app.Fragment
import androidx.navigation.NavType
import androidx.navigation.navArgument


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PerfumeAppTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "welcome") {
                    composable("welcome") { WelcomeScreen(navController) }
                    composable(
                        route = "perfumeCatalog/{gender}",
                        arguments = listOf(navArgument("gender") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val gender = backStackEntry.arguments?.getString("gender")
                        PerfumeCatalogScreen(gender)
                    }
                }
            }
        }
    }
}

@Composable
fun PerfumeCatalogScreen(gender: String?) {
    require(gender != null) { "Gender cannot be null" }

    var perfumeList by remember { mutableStateOf(emptyList<Perfume>()) }
    var isLoading by remember { mutableStateOf(true) } // Track loading state
    LaunchedEffect(key1 = Unit) {
        fetchPerfumes(gender) { perfumes ->
            perfumeList = perfumes
            isLoading = false // Set loading state to false when data is loaded
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        UpdateDataButton {
            isLoading = true // Set loading state to true when updating data
            fetchPerfumes(gender) { perfumes ->
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

fun fetchPerfumes(gender: String, onPerfumesFetched: (List<Perfume>) -> Unit) {
    val apiUrl = "https://69e3ce12jh.execute-api.eu-central-1.amazonaws.com/TEST/all-parfumes/$gender/"
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

fun fetchApiResponse(apiUrl: String): String {
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

fun parseJsonResponse(jsonResponse: String): List<Perfume> {
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
                category = perfumeJson.getString("category_name"),
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

@Composable
fun WelcomeScreen(navController: NavController) {
    var selectedGender by remember { mutableStateOf<String?>(null) }
    var isNetworkError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isNetworkAvailable(context)) {
                Text(
                    text = "No internet connection",
                    style = TextStyle(color = Color.Red),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                // You can customize the warning message as needed
            }
            Image(
                painter = painterResource(id = R.drawable.perfumes_welcome),
                contentDescription = "Perfume Bottle",
                modifier = Modifier
                    .size(400.dp)
            )
            Text(
                text = "Welcome to Perfume App!",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 25.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.W800,
                    fontStyle = FontStyle.Italic,
                )
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (isNetworkAvailable(context)) {
                        selectedGender = "male"
                        navController.navigate("perfumeCatalog/$selectedGender")
                    } else {
                        isNetworkError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 32.dp), // Set button padding
                shape = RoundedCornerShape(8.dp) // Set button shape
            ) {
                Text("Explore Male Perfumes", color = Color.White, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isNetworkAvailable(context)) {
                        selectedGender = "female"
                        navController.navigate("perfumeCatalog/$selectedGender")
                    } else {
                        isNetworkError = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 32.dp), // Set button padding
                shape = RoundedCornerShape(8.dp) // Set button shape
            ) {
                Text("Explore Female Perfumes", color = Color.White, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "For assistance, call: +1 123-456-7890",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "Email: info@perfumeapp.com",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        content = { Text("Update Data") }
    )
}

data class Perfume(
    val name: String,
    val brand: String,
    val category: String,
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
    var buyDialogOpen by remember { mutableStateOf(false) }
    var detailsDialogOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Perfume Image
            Image(
                painter = rememberImagePainter(perfume.photoUrl),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(shape = RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Perfume Details
            Text(
                text = perfume.name,
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = perfume.brand,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Category: ${perfume.category}",
                style = TextStyle(fontSize = 14.sp, fontStyle = FontStyle.Italic),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Actions Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                // Details Button
                Button(
                    onClick = { detailsDialogOpen = true },
                    modifier = Modifier.padding(end = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.Blue // Text color
                    )
                ) {
                    Text(
                        "Details",
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                }

                // Buy Button
                Button(
                    onClick = { buyDialogOpen = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.Blue // Text color
                    )
                ) {
                    Text(
                        "Buy",
                        style = TextStyle(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    // Dialog for making a call
    if (detailsDialogOpen) {
        AlertDialog(
            onDismissRequest = { detailsDialogOpen = false },
            title = {
                Text(
                    text = "${perfume.name} Details",
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                )
            },
            text = {
                Column {
                    DetailItem("Name", perfume.name.toString())
                    DetailItem("Brand", perfume.brand)
                    DetailItem("Category", perfume.category)
                    DetailItem("Gender", perfume.gender)
                    DetailItem("Scent Notes", perfume.scentNotes)
                    DetailItem("Volume", perfume.volume)
                    DetailItem("Release Year", perfume.releaseYear.toString())
                    DetailItem("Price", "${perfume.price} $")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        buyDialogOpen = true
                    }
                ) {
                    Text("Buy", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { detailsDialogOpen = false }
                ) {
                    Text("Go Back", color = Color.White)
                }
            }
        )
    }

    // Dialog for making a call
    if (buyDialogOpen) {
        AlertDialog(
            onDismissRequest = { buyDialogOpen = false },
            title = {
                Text("Call to Buy")
            },
            text = {
                Column {
                    Text("Price: ${perfume.price}")
                    Text("To purchase ${perfume.name}, call:")
                    Text("+1 123-456-7890")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        buyDialogOpen = false
                    }
                ) {
                    Text("Call", color = Color.Black)
                }
            },
            dismissButton = {
                Button(
                    onClick = { buyDialogOpen = false }
                ) {
                    Text("Cancel", color = Color.Black)
                }
            }
        )
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        )
        Text(
            text = value,
            style = TextStyle(
                color = Color.Black
            )
        )
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
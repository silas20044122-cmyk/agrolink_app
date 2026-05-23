package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.ChatMessage
import com.example.database.ScanHistory
import com.example.database.UserProfile
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.*
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val agroViewModel: AgroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = agroViewModel)
            }
        }
    }
}

// --- Screen Navigation ---
enum class AppScreen(val titleEn: String, val titleSwIndex: String) {
    HOME("Home", "Nyumbani"),
    SCAN("Crop Scan", "Okoa Mimea"),
    MARKET("Market", "Soko Kuu"),
    ADVISORY("AI Advisor", "Mshauri"),
    PROFILE("Profile", "Profaili")
}

@Composable
fun MainAppScreen(viewModel: AgroViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isEnglish = profile.language == "English"

    Scaffold(
        bottomBar = {
            AgroBottomNavigation(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it },
                isEnglish = isEnglish
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "ScreenTransition"
            ) { targetState ->
                when (targetState) {
                    AppScreen.HOME -> HomeScreen(viewModel = viewModel, onNavigateTo = { currentScreen = it })
                    AppScreen.SCAN -> ScanScreen(viewModel = viewModel)
                    AppScreen.MARKET -> MarketScreen(viewModel = viewModel)
                    AppScreen.ADVISORY -> AdvisoryScreen(viewModel = viewModel)
                    AppScreen.PROFILE -> ProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AgroBottomNavigation(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    isEnglish: Boolean
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.shadow(12.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        AppScreen.values().forEach { screen ->
            val isSelected = currentScreen == screen
            val label = if (isEnglish) screen.titleEn else screen.titleSwIndex
            val icon = when (screen) {
                AppScreen.HOME -> if (isSelected) Icons.Filled.Home else Icons.Outlined.Home
                AppScreen.SCAN -> if (isSelected) Icons.Filled.PhotoCamera else Icons.Outlined.PhotoCamera
                AppScreen.MARKET -> if (isSelected) Icons.Filled.Storefront else Icons.Outlined.Storefront
                AppScreen.ADVISORY -> if (isSelected) Icons.Filled.Chat else Icons.Outlined.Chat
                AppScreen.PROFILE -> if (isSelected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle
            }

            NavigationBarItem(
                selected = isSelected,
                onClick = { onScreenSelected(screen) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("nav_tab_${screen.name.lowercase()}")
            )
        }
    }
}

// --- HomeScreen / Dashboard ---
@Composable
fun HomeScreen(viewModel: AgroViewModel, onNavigateTo: (AppScreen) -> Unit) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val weather by viewModel.weatherState.collectAsStateWithLifecycle()
    val market by viewModel.marketState.collectAsStateWithLifecycle()
    val isEnglish = profile.language == "English"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DashboardHeader(profile = profile, isEnglish = isEnglish)
        }

        item {
            RegionalAlertCard(weather = weather, isEnglish = isEnglish)
        }

        item {
            WeatherDashboardCard(weather = weather, isEnglish = isEnglish, onNavigateTo = onNavigateTo)
        }

        item {
            MainActionHub(isEnglish = isEnglish, onNavigateTo = onNavigateTo)
        }

        item {
            MarketHighlightCard(market = market, isEnglish = isEnglish, onNavigateTo = onNavigateTo)
        }

        item {
            WeatherInsightsBlock(weather = weather, isEnglish = isEnglish)
        }

        item {
            AdvisoryPromoBanner(isEnglish = isEnglish, onNavigateTo = onNavigateTo)
        }
    }
}

@Composable
fun DashboardHeader(profile: UserProfile, isEnglish: Boolean) {
    val dateString = remember {
        val sdf = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (isEnglish) "Jambo," else "Habari,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = "${profile.fullName} 🌾",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
        }

        Box(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = if (isEnglish) "Offline Enabled" else "Inafanya Kazi Offline",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun RegionalAlertCard(weather: WeatherState, isEnglish: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Alert icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = if (isEnglish) "Regional Crop & Weather Alert" else "Tahadhari ya Kanda",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = weather.climateAlert,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun WeatherDashboardCard(weather: WeatherState, isEnglish: Boolean, onNavigateTo: (AppScreen) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateTo(AppScreen.HOME) }
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${weather.county} • Leo (Today)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "🌦️",
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${weather.temp.toInt()}°C",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = weather.conditionEn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WeatherStatBadge(label = if (isEnglish) "Rain" else "Mvua", valStr = "${weather.rainChance.toInt()}%", icon = Icons.Default.WaterDrop)
                    WeatherStatBadge(label = if (isEnglish) "Humidity" else "Unyevu", valStr = "${weather.humidity.toInt()}%", icon = Icons.Default.Thermostat)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.67f)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (weather.recommendations.isNotEmpty()) weather.recommendations.first() else "Time to plant maize in 2 days.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun WeatherStatBadge(label: String, valStr: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$label: $valStr",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MainActionHub(isEnglish: Boolean, onNavigateTo: (AppScreen) -> Unit) {
    Column {
        Text(
            text = if (isEnglish) "Intelligent Agriculture Tools" else "Vifaa vya Agro-AI",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionHubButton(
                title = if (isEnglish) "Scan Plant Diagnosis" else "Pima Magonjwa",
                desc = if (isEnglish) "AI Crop Leaf diagnostics" else "Ondoa wadudu wa mimea",
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.PhotoCamera,
                onClick = { onNavigateTo(AppScreen.SCAN) },
                modifier = Modifier.weight(1f).testTag("action_scan")
            )

            ActionHubButton(
                title = if (isEnglish) "Farmer AI Chatbot" else "Mshauri wa AI",
                desc = if (isEnglish) "Bilingual advisory" else "Kiingereza na Kiswahili",
                color = MaterialTheme.colorScheme.secondary,
                icon = Icons.Default.Chat,
                onClick = { onNavigateTo(AppScreen.ADVISORY) },
                modifier = Modifier.weight(1f).testTag("action_advisory")
            )
        }
    }
}

@Composable
fun ActionHubButton(
    title: String,
    desc: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun MarketHighlightCard(market: MarketState, isEnglish: Boolean, onNavigateTo: (AppScreen) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEnglish) "Market Price Highlights" else "Bei za Soko la Kitaifa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(onClick = { onNavigateTo(AppScreen.MARKET) }) {
                    Text(
                        text = if (isEnglish) "View All" else "Angalia Zote",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = if (isEnglish) "Estimated prices in Nairobi Marikiti & local hubs" else "Kadirio la bei za marikiti katika maeneo muhimu",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(market.marketPrices.take(4)) { price ->
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                            .width(135.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = price.commodity,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = price.trendIcon,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = price.unit,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "KES ${price.avgPrice.toInt()}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = price.marketName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherInsightsBlock(weather: WeatherState, isEnglish: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Tips Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (isEnglish) "Weather-Based Farm Advice" else "Maelekezo Kulingana na Hewa",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            weather.recommendations.forEach { tip ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    Text(
                        text = tip,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AdvisoryPromoBanner(isEnglish: Boolean, onNavigateTo: (AppScreen) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2D452D),
                        Color(0xFF121A12)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = if (isEnglish) "Need immediate veterinary or soil consultancy?" else "Je, unahitaji daktari wa mifugo au mtaalamu wa udongo?",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEnglish) "Talk bilingually to AgroLink advisor anytime. Over 1,200 smallholders answered weekly." else "Ongea nasi wakati wowote, tuko tayari kusaidia.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Button(
                onClick = { onNavigateTo(AppScreen.ADVISORY) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isEnglish) "Launch Chat Assistant" else "Zungumza Nasi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- ScanScreen ---
@Composable
fun ScanScreen(viewModel: AgroViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val activeScanResult by viewModel.activeScanResult.collectAsStateWithLifecycle()
    val history by viewModel.scanHistory.collectAsStateWithLifecycle()
    val isEnglish = profile.language == "English"
    val context = LocalContext.current

    val (selectedCrop, setSelectedCrop) = remember { mutableStateOf("Maize") }
    val (customBitmap, setCustomBitmap) = remember { mutableStateOf<Bitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                setCustomBitmap(bitmap)
                Toast.makeText(context, "Image uploaded! Ready to scan.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isEnglish) "AI Crop Disease Scanner" else "Kipimo cha Magonjwa kwa AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isEnglish) "Select a crop, snap/upload an image of an infected leaf, and let AI analyze pathogens instantly." else "Chagua mmea, piga au pakia picha ya jani lililoathirika kugundua ugonjwa upesi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        item {
            Text(
                text = if (isEnglish) "1. Select Target Crop:" else "1. Chagua Mmea wako:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            val crops = listOf("Maize", "Tomatoes", "Potatoes", "Beans", "Onions")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(crops) { crop ->
                    val isSelected = selectedCrop == crop
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                setSelectedCrop(crop)
                                setCustomBitmap(null)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = crop,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap.asImageBitmap(),
                        contentDescription = "Uploaded leaf image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val stroke = 8f
                        val len = 40f
                        drawLine(Color.Green, Offset(20f, 20f), Offset(20f + len, 20f), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(20f, 20f), Offset(20f, 20f + len), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(canvasWidth - 20f, 20f), Offset(canvasWidth - 20f - len, 20f), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(canvasWidth - 20f, 20f), Offset(canvasWidth - 20f, 20f + len), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(20f, canvasHeight - 20f), Offset(20f + len, canvasHeight - 20f), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(20f, canvasHeight - 20f), Offset(20f, canvasHeight - 20f - len), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(canvasWidth - 20f, canvasHeight - 20f), Offset(canvasWidth - 20f - len, canvasHeight - 20f), strokeWidth = stroke)
                        drawLine(Color.Green, Offset(canvasWidth - 20f, canvasHeight - 20f), Offset(canvasWidth - 20f, canvasHeight - 20f - len), strokeWidth = stroke)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Camera viewfinder icon",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(50.dp)
                        )
                        Text(
                            text = if (isEnglish) "Simulated Lens Focus Viewfinder" else "Lensi ya Kulenga Ki-AI",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isEnglish) "2. Capture Leaf sample to Scan:" else "2. Piga Picha au Chagua jani magonjwa:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Upload gallery logo")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isEnglish) "Upload Photo" else "Kupakia Picha", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val activeTag = when (selectedCrop) {
                                "Maize" -> "yellow spots on leaf, maize rust fungus"
                                "Tomatoes" -> "circular concentric rings, late blight"
                                "Potatoes" -> "dark spots rotting, Phytophthora mold"
                                "Beans" -> "sunken pod and leaf lesions, anthracnose"
                                "Onions" -> "purple oval leaf blemishes, dry blotch"
                                else -> "unidentified leaf pathogen spots"
                            }
                            viewModel.analyzeCropDisease(selectedCrop, customBitmap, activeTag)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isAnalyzing,
                        modifier = Modifier.weight(1.2f).testTag("trigger_scan_btn")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play tag")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isEnglish) "Scan Leaf Now" else "Pima Jani Sasa", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        if (isAnalyzing) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isEnglish) "AgroLink AI analyzing plant pathogen..." else "AI ikisajili na kupima pathogen za mmea...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (activeScanResult != null) {
            item {
                ScanReportCard(scan = activeScanResult!!, isEnglish = isEnglish, viewModel = viewModel)
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnglish) "Previous Scans Log" else "Kumbukumbu ya Vipimo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { viewModel.clearScans() }) {
                        Text(
                            text = if (isEnglish) "Clear Log" else "Futa Historia",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(history) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${record.cropType} Diagnostic",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (record.urgency.lowercase().contains("high") || record.urgency.lowercase().contains("haraka")) Color(0xFFFDE8E8) else Color(0xFFFEF08A),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = record.urgency,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (record.urgency.lowercase().contains("high") || record.urgency.lowercase().contains("haraka")) Color(0xFFC81E1E) else Color(0xFF854D0E)
                                )
                            }
                        }
                        Text(
                            text = record.diseaseName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Confidence Score: ${(record.confidenceScore).toInt()}% • Treatment recommended",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScanReportCard(scan: ScanHistory, isEnglish: Boolean, viewModel: AgroViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified Diagnostics Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isEnglish) "Verify AI Diagnosis" else "Uhakiki wa Kipimo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = { viewModel.speakText(scan.treatment) }) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Read report aloud",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            Column {
                Text(
                    text = if (isEnglish) "Suspected Crop Pathogen:" else "Ugonjwa Kukuu wa Mmea:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    text = scan.diseaseName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Confidence: ${scan.confidenceScore.toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFDF2F2), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Urgency: ${scan.urgency}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC81E1E)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = if (isEnglish) "🛠️ Critical Treatment Plan:" else "🛠️ Jinsi ya Kutibu upesi:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = scan.treatment,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column {
                Text(
                    text = if (isEnglish) "🛡️ Preventative Action for Next Season:" else "🛡️ Kujitayarisha Kuzuia Mbeleni:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = scan.prevention,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column {
                Text(
                    text = if (isEnglish) "🧪 Soil Health & Fertilizer Rec:" else "🧪 Mbolea Inayofaa kwa Udongo:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = scan.fertilizer,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

// --- MarketScreen ---
@Composable
fun MarketScreen(viewModel: AgroViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val marketState by viewModel.marketState.collectAsStateWithLifecycle()
    val isEnglish = profile.language == "English"
    val context = LocalContext.current

    val (searchQuery, setSearchQuery) = remember { mutableStateOf("") }
    val (isFormOpen, setFormOpen) = remember { mutableStateOf(false) }

    val (formCrop, setFormCrop) = remember { mutableStateOf("Maize") }
    val (formQty, setFormQty) = remember { mutableStateOf("") }
    val (formPrice, setFormPrice) = remember { mutableStateOf("") }
    val (formPhone, setFormPhone) = remember { mutableStateOf("") }

    val filteredPrices = marketState.marketPrices.filter {
        it.commodity.contains(searchQuery, ignoreCase = true) ||
                it.marketName.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = if (isEnglish) "Marketplace & Prices" else "Soko Kuu la Mkulima",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEnglish) "Direct access to regional market indexes and peer transactions." else "Fikia bei halisi za sokoni kote nchini na wanunuzi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = setSearchQuery,
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search bar") },
                placeholder = {
                    Text(
                        text = if (isEnglish) "Search crops (e.g. Tomato, Irish Potato)" else "Tafuta zao mfano: nyanya, mahindi...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
        }

        item {
            Text(
                text = if (isEnglish) "Official County Market Prices" else "Bei za Soko la Serikali",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(filteredPrices) { price ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = price.commodity,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Unit: ${price.unit}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = price.marketName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "KES ${price.avgPrice.toInt()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Trend: ${price.trend}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (price.trend == "Up") Color(0xFF2E7D32) else if (price.trend == "Down") Color(0xFFC62828) else Color.Gray
                            )
                            Text(text = price.trendIcon, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isEnglish) "Direct Local Peer Produce" else "Soko ya Wakulima Wafanyabiashara",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isEnglish) "Buy crop harvests directly from nearby farms with zero brokers" else "Nunua mazao bila brokers moja kwa moja kutoka shambani",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        item {
            Button(
                onClick = { setFormOpen(true) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add listing")
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEnglish) "List my Crop Produce" else "Sajili Mazaos yangu",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }
        }

        items(marketState.farmerListings) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (item.isMyListing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.farmerName.firstOrNull()?.toString() ?: "M",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text(
                                    text = item.farmerName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "County: ${item.county}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }

                        if (item.isMyListing) {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isEnglish) "My Post" else "Yangu",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = item.cropType,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Quantity: ${item.quantity}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Price: ${item.priceString}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Button(
                            onClick = {
                                Toast.makeText(context, "Dialing ${item.contactPhone}...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Call farmer", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (isEnglish) "Call Farmer" else "Piga Simu", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (isFormOpen) {
        Dialog(onDismissRequest = { setFormOpen(false) }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isEnglish) "Post your Harvest for buyers" else "Sajili Mazao yako Sokoni",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider()

                    Text(text = "Choose Produce Type:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val choices = listOf("Maize", "Tomatoes", "Potatoes", "Beans", "Onions")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(choices) { selection ->
                            val active = formCrop == selection
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { setFormCrop(selection) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = selection,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = formQty,
                        onValueChange = setFormQty,
                        label = { Text(if (isEnglish) "Quantity (e.g. 5 Crates, 4 Bags)" else "Kiasi cha Mazao") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = formPrice,
                        onValueChange = setFormPrice,
                        label = { Text(if (isEnglish) "Expected Price (e.g. KES 3000)" else "Bei unayotaka") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = formPhone,
                        onValueChange = setFormPhone,
                        label = { Text(if (isEnglish) "Contact Phone Number" else "Nambari ya Simu") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { setFormOpen(false) }) {
                            Text(text = if (isEnglish) "Cancel" else "Futa", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (formQty.isNotBlank() && formPrice.isNotBlank() && formPhone.isNotBlank()) {
                                    viewModel.addFarmerListing(formCrop, formQty, formPrice, formPhone)
                                    setFormOpen(false)
                                    Toast.makeText(context, "Produce listed successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "All fields are required!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = if (isEnglish) "Post Product" else "Fungua Soko")
                        }
                    }
                }
            }
        }
    }
}

// --- AdvisoryScreen ---
@Composable
fun AdvisoryScreen(viewModel: AgroViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val isEnglish = profile.language == "English"
    val context = LocalContext.current

    val (messageText, setMessageText) = remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isEnglish) "AgroLink Farmer Chatbot" else "Mshauri wa Mkulima",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isEnglish) "Specialist AI advisor in crops and soil health." else "Mtaalamu wa AI anayejua magonjwa na mbolea upesi.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTtsSpeaking) {
                    IconButton(onClick = { viewModel.stopSpeaking() }) {
                        Icon(imageVector = Icons.Default.VolumeMute, contentDescription = "Mute", tint = MaterialTheme.colorScheme.error)
                    }
                }

                TextButton(onClick = { viewModel.clearHistory() }) {
                    Text(text = if (isEnglish) "Clear" else "Futa yote", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }

        Divider(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { msg ->
                val bubbleBg = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val alignment = if (msg.isUser) Alignment.End else Alignment.Start
                val textColor = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                val shape = if (msg.isUser) RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp)
                else RoundedCornerShape(topStart = 4.dp, bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp)

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .shadow(2.dp, shape)
                            .background(bubbleBg, shape)
                            .border(1.dp, if (msg.isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, shape)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                color = textColor,
                                lineHeight = 17.sp,
                                modifier = Modifier.testTag("chat_msg_text")
                            )

                            if (!msg.isUser) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.speakText(msg.text) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Read message bubble",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = if (msg.isUser) "Farmer" else "AgroLink AI Assistant",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (isChatLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isEnglish) "AI Assistant is typing..." else "AI ikifikiria jibu lako...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val suggestions = if (isEnglish) {
                listOf(
                    "How to treat maize stem borers?",
                    "Cure tomato early blight advice?",
                    "Which fertilizer for planting potatoes?"
                )
            } else {
                listOf(
                    "Jinsi ya kutibu minyoo ya mahindi?",
                    "Jinsi ya kutibu ugonjwa wa nyanya?",
                    "Mbolea ipi inafaa viazi baridi?"
                )
            }

            suggestions.forEach { suggestion ->
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable { viewModel.sendChatMessage(suggestion) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = suggestion,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = setMessageText,
                placeholder = {
                    Text(
                        text = if (isEnglish) "Type farming question..." else "Andika swali lako...",
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )

            if (messageText.isBlank()) {
                FilledIconButton(
                    onClick = {
                        val voicePrompt = if (isEnglish) "How do I increase my maize farm yields during drought?" else "Nifanye nini kupata mazao mengi ya mahindi wakati wa kiangazi?"
                        viewModel.sendChatMessage(voicePrompt)
                        Toast.makeText(context, "Voice Simulation Captured!", Toast.LENGTH_SHORT).show()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.size(48.dp).testTag("mic_btn")
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Input Indicator", tint = Color.White)
                }
            } else {
                FilledIconButton(
                    onClick = {
                        viewModel.sendChatMessage(messageText)
                        setMessageText("")
                        keyboardController?.hide()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(48.dp).testTag("chat_send_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send message logo")
                }
            }
        }
    }
}

// --- ProfileScreen ---
@Composable
fun ProfileScreen(viewModel: AgroViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isEnglish = profile.language == "English"
    val context = LocalContext.current

    val (name, setName) = remember(profile) { mutableStateOf(profile.fullName) }
    val (farmSizeStr, setFarmSizeStr) = remember(profile) { mutableStateOf(profile.farmSizeAcres.toString()) }
    val (selectedCounty, setSelectedCounty) = remember(profile) { mutableStateOf(profile.county) }
    val (language, setLanguage) = remember(profile) { mutableStateOf(profile.language) }

    val counties = listOf("Nyeri", "Meru", "Kiambu", "Kitui", "Makueni", "Eldoret", "Uasin Gishu", "Mombasa", "Kisumu", "Taveta")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isEnglish) "Farmer Profile & Custom Settings" else "Profaili ya Mkulima",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isEnglish) "Setup personalized regional parameters to get county-specific alerts and customized market prices." else "Weka nafasi yako kupata tahadhari sahihi kulingana na kanda yako kwa urahisi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = if (isEnglish) "Preferred Language (Lugha):" else "Lugha Preference:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf("English", "Kiswahili").forEach { lang ->
                                val active = language == lang
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { setLanguage(lang) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = setName,
                        label = { Text(if (isEnglish) "Full Name / Jina Kamili" else "Jina Lako") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_name"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Column {
                        Text(
                            text = if (isEnglish) "My county location (Updates Alerts & Weather):" else "Eneo la Kaunti Yako:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(counties) { county ->
                                val active = selectedCounty == county
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.background,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { setSelectedCounty(county) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = county,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = farmSizeStr,
                        onValueChange = setFarmSizeStr,
                        label = { Text(if (isEnglish) "Farm Area Size (Acres)" else "Ukubwa wa Shamba (Acres)") },
                        modifier = Modifier.fillMaxWidth().testTag("profile_farm_size"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Button(
                        onClick = {
                            val fSize = farmSizeStr.toDoubleOrNull() ?: 1.0
                            viewModel.updateProfile(
                                name = name,
                                county = selectedCounty,
                                farmSize = fSize,
                                crops = listOf("Maize", "Tomatoes", "Potatoes"),
                                lang = language,
                                interests = listOf("Agro-AI", "Market Tickers")
                            )
                            Toast.makeText(context, "Configurations saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("save_profile_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Update Farm Profile" else "Hifadhi Mabadiliko",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF2F2)),
                border = BorderStroke(1.dp, Color(0xFFF05252))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isEnglish) "Device Storage management" else "Kuhifadhi Kwenye Simu",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFC81E1E)
                    )
                    Text(
                        text = if (isEnglish) "Optimized for lightweight devices. You can clear local intelligence database to liberate memory space anytime." else "Unaweza kufuta vitu vilivyohifadhiwa ili kupata nafasi mwanzo kabisa kusaidia simu ndogo kufanya kazi kwa haraka.",
                        fontSize = 11.sp,
                        color = Color(0xFF9B1C1C),
                        lineHeight = 15.sp
                    )
                    Button(
                        onClick = {
                            viewModel.clearScans()
                            viewModel.clearHistory()
                            Toast.makeText(context, "Offline data cache cleared successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC81E1E)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = if (isEnglish) "Clear Offline Database Cache" else "Safisha Kumbukumbu")
                    }
                }
            }
        }
    }
}

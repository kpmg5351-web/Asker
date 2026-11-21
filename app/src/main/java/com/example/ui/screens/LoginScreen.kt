package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.core.EgyptTime
import com.example.core.PhoneUtils
import com.example.core.SecurityUtils
import com.example.core.WhatsAppHelper
import com.example.data.repository.AuthRepository
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.theme.PrimaryBlue as HesabatyBlue
import com.example.ui.theme.PrimaryDarkBlue as HesabatyBlueDark
import com.example.ui.viewmodel.SavedLoginCredentials

@Composable
fun LoginScreen(
    isBusy: Boolean,
    isDarkMode: Boolean,
    savedCredentials: SavedLoginCredentials = SavedLoginCredentials(),
    deviceLabel: String = "",
    deviceActivationCode: String = "",
    onToggleDarkMode: (Boolean) -> Unit,
    onLogin: (username: String, password: String, rememberMe: Boolean, onError: (String) -> Unit) -> Unit,
    onRememberMeChanged: (Boolean) -> Unit = {},
    onRequestBiometricLogin: (onError: (String) -> Unit) -> Unit = {},
    onOpenTrial: (onError: (String) -> Unit) -> Unit,
    onCreateOnThisPhone: (
        adminUsername: String,
        adminPassword: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        onError: (String) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    onActivateWithToken: (rawToken: String, onError: (String) -> Unit) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val uiScale = HesabatyThemeExtras.uiScale
    var username by rememberSaveable { mutableStateOf(savedCredentials.username) }
    var password by rememberSaveable { mutableStateOf(savedCredentials.password) }
    var rememberMe by rememberSaveable { mutableStateOf(savedCredentials.rememberMe) }
    var didPrefill by rememberSaveable { mutableStateOf(savedCredentials.rememberMe && savedCredentials.username.isNotBlank()) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var showActivationDialog by remember { mutableStateOf(false) }
    val loginInteractionSource = remember { MutableInteractionSource() }
    val isLoginPressed by loginInteractionSource.collectIsPressedAsState()
    val loginButtonScale by animateFloatAsState(
        targetValue = if (isLoginPressed && !isBusy) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "login_button_scale"
    )

    LaunchedEffect(savedCredentials) {
        if (!didPrefill && savedCredentials.rememberMe && savedCredentials.username.isNotBlank()) {
            if (username.isBlank() && password.isBlank()) {
                username = savedCredentials.username
                password = savedCredentials.password
                rememberMe = true
            }
            didPrefill = true
        }
    }

    val cardBg = if (isDarkMode) Color(0xFF131E34) else Color.White
    val cardBorder = if (isDarkMode) Color(0xFF233554) else Color(0xFFE3ECF8)
    val inputBorder = if (isDarkMode) Color(0xFF334768) else Color(0xFFD0DCEB)
    val trialBtnBg = if (isDarkMode) Color(0xFF1A2D4F) else Color(0xFFEBF3FF)
    val trialBtnBorder = if (isDarkMode) Color(0xFF4B8BF4) else Color(0xFF6BA6F8)
    val titleColor = if (isDarkMode) Color(0xFF7CB4FF) else Color(0xFF0E3D8C)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isDarkMode) {
                        listOf(Color(0xFF0A1222), Color(0xFF0F1B31), Color(0xFF0A1222))
                    } else {
                        listOf(Color(0xFFF3F8FF), Color(0xFFFAFCFF), Color(0xFFEDF4FF))
                    }
                )
            )
    ) {
        // Decorative blue top & bottom waves matching the HESABATY visual identity
        HesabatyWaveBackground(isDarkMode = isDarkMode, modifier = Modifier.fillMaxSize())

        // Scrollable form first, floating theme toggle above it so taps are never blocked
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = if (uiScale.isCompact) 14.dp else 20.dp,
                        end = if (uiScale.isCompact) 14.dp else 20.dp,
                        top = if (uiScale.isCompact) 48.dp else 56.dp,
                        bottom = 16.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 440.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // App Brand Icon
                    Surface(
                        shape = RoundedCornerShape(if (uiScale.isCompact) 18.dp else 22.dp),
                        shadowElevation = 10.dp,
                        color = HesabatyBlue,
                        modifier = Modifier.size(if (uiScale.isCompact) 62.dp else 78.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_hesabaty_brand),
                            contentDescription = "شعار تطبيق حساباتي",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(if (uiScale.isCompact) 8.dp else 12.dp))

                    // App Brand Title
                    Text(
                        text = "حساباتي | HESABATY",
                        style = (if (uiScale.isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium).copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.2.sp
                        ),
                        color = titleColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Subtitle flanked by subtle divider lines
                    Row(
                        modifier = Modifier.fillMaxWidth(0.95f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = if (isDarkMode) Color(0xFF283C5E) else Color(0xFFC5D8F3)
                        )
                        Text(
                            text = "نظام إدارة حسابات الموردين والعملاء والمبيعات",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDarkMode) Color(0xFF93B4E0) else Color(0xFF26539C),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            thickness = 1.dp,
                            color = if (isDarkMode) Color(0xFF283C5E) else Color(0xFFC5D8F3)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (uiScale.isCompact) 6.dp else 10.dp))

                    // 3D Accounting Illustration (Calculator, Ledger, Gold Coins & Chart)
                    Image(
                        painter = painterResource(id = R.drawable.img_login_hero),
                        contentDescription = "توضيح النظام المحاسبي",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(if (uiScale.isCompact) 0.54f else 0.68f)
                            .height(if (uiScale.isCompact) 86.dp else 122.dp)
                    )

                    Spacer(modifier = Modifier.height(if (uiScale.isCompact) 8.dp else 10.dp))

                    // Main Elevated Login Card
                    Surface(
                        shape = RoundedCornerShape(if (uiScale.isCompact) 22.dp else 26.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        shadowElevation = if (isDarkMode) 4.dp else 10.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (uiScale.isCompact) 14.dp else 20.dp,
                                    vertical = if (uiScale.isCompact) 16.dp else 20.dp
                                )
                        ) {
                            // Card Header: Shield badge + "تسجيل الدخول الآمن"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "تسجيل الدخول الآمن",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFFEAF2FF) else Color(0xFF0E2954)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "للوصول إلى حساباتك وإدارة بياناتك بكل أمان",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isDarkMode) Color(0xFF1A315A) else Color(0xFFEBF3FF),
                                    modifier = Modifier.size(50.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "حماية وتسجيل دخول آمن",
                                            tint = HesabatyBlue,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Username Field
                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it
                                    errorText = null
                                },
                                placeholder = { Text("اسم المستخدم") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HesabatyBlue,
                                    unfocusedBorderColor = inputBorder,
                                    focusedContainerColor = if (isDarkMode) Color(0xFF172540) else Color(0xFFFAFBFD),
                                    unfocusedContainerColor = if (isDarkMode) Color(0xFF172540) else Color(0xFFFAFBFD)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_username_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Password Field
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    errorText = null
                                },
                                placeholder = { Text("كلمة المرور") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HesabatyBlue,
                                    unfocusedBorderColor = inputBorder,
                                    focusedContainerColor = if (isDarkMode) Color(0xFF172540) else Color(0xFFFAFBFD),
                                    unfocusedContainerColor = if (isDarkMode) Color(0xFF172540) else Color(0xFFFAFBFD)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (!isBusy) onLogin(username, password, rememberMe) { errorText = it }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input")
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // "Remember me on this device" Checkbox with animated visual confirmation
                            val rememberRowBg by animateColorAsState(
                                targetValue = when {
                                    rememberMe && isDarkMode -> Color(0xFF1A315A).copy(alpha = 0.72f)
                                    rememberMe -> Color(0xFFEAF3FF)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(durationMillis = 220),
                                label = "remember_row_bg"
                            )
                            val rememberRowBorder by animateColorAsState(
                                targetValue = when {
                                    rememberMe && isDarkMode -> Color(0xFF3A64A8)
                                    rememberMe -> Color(0xFFBBD6FB)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(durationMillis = 220),
                                label = "remember_row_border"
                            )
                            val checkboxScale by animateFloatAsState(
                                targetValue = if (rememberMe) 1.08f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "remember_checkbox_scale"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(rememberRowBg)
                                    .border(1.dp, rememberRowBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        val next = !rememberMe
                                        rememberMe = next
                                        onRememberMeChanged(next)
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = rememberMe,
                                    onCheckedChange = { next ->
                                        rememberMe = next
                                        onRememberMeChanged(next)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = HesabatyBlue),
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = checkboxScale
                                            scaleY = checkboxScale
                                        }
                                        .testTag("login_remember_checkbox")
                                )
                                Text(
                                    text = "تذكرني على هذا الجهاز",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (rememberMe) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (rememberMe) {
                                        if (isDarkMode) Color(0xFFBFDBFE) else HesabatyBlueDark
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                AnimatedVisibility(
                                    visible = rememberMe,
                                    enter = fadeIn(tween(180)) + expandHorizontally(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                    exit = fadeOut(tween(150)) + shrinkHorizontally(tween(160))
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (isDarkMode) Color(0xFF214075) else Color(0xFFD6E7FF),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = HesabatyBlue,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "محفوظ",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkMode) Color(0xFFDBEAFE) else HesabatyBlueDark
                                            )
                                        }
                                    }
                                }
                            }

                            // Animated Inline Error Banner (including Device Binding mismatch message)
                            AnimatedVisibility(
                                visible = errorText != null,
                                enter = expandVertically(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
                            ) {
                                val displayedError = errorText.orEmpty()
                                Column {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = HesabatyThemeExtras.colors.dangerContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = HesabatyThemeExtras.colors.danger,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = displayedError,
                                                color = HesabatyThemeExtras.colors.danger,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.testTag("login_error_text")
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Primary Royal-Blue Gradient Login Button + Biometric Quick Login
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 50.dp)
                                        .graphicsLayer {
                                            scaleX = loginButtonScale
                                            scaleY = loginButtonScale
                                        }
                                        .shadow(
                                            elevation = 6.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = HesabatyBlue,
                                            spotColor = HesabatyBlue
                                        )
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(HesabatyBlueDark, HesabatyBlue, Color(0xFF2684FF))
                                            )
                                        )
                                        .clickable(
                                            interactionSource = loginInteractionSource,
                                            indication = LocalIndication.current,
                                            enabled = !isBusy
                                        ) {
                                            onLogin(username, password, rememberMe) { errorText = it }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 10.dp)
                                        .testTag("login_submit_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(
                                        targetState = isBusy,
                                        transitionSpec = {
                                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200))) togetherWith
                                                (fadeOut(tween(150)) + scaleOut(targetScale = 0.92f, animationSpec = tween(150)))
                                        },
                                        label = "login_button_content"
                                    ) { busy ->
                                        if (busy) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.5.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "جارٍ الدخول...",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(9.dp))
                                                        .background(Color.White.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.Login,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "دخول إلى النظام",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    onClick = {
                                        if (!isBusy) {
                                            errorText = null
                                            onRequestBiometricLogin { errorText = it }
                                        }
                                    },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isDarkMode) Color(0xFF1A2D4F) else Color(0xFFEBF3FF),
                                    border = BorderStroke(
                                        1.4.dp,
                                        if (savedCredentials.biometricUsername != null) HesabatyBlue else inputBorder
                                    ),
                                    modifier = Modifier
                                        .size(54.dp)
                                        .testTag("login_biometric_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "الدخول بالبصمة",
                                            tint = if (isDarkMode) Color(0xFF93C5FD) else HesabatyBlueDark,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // "أو" Divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = if (isDarkMode) Color(0xFF283A5A) else Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = "أو",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = if (isDarkMode) Color(0xFF283A5A) else Color(0xFFE2E8F0)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Trial Account Button (replaces fingerprint button — 72h isolated trial)
                            Surface(
                                onClick = {
                                    if (!isBusy) {
                                        errorText = null
                                        username = SecurityUtils.TRIAL_USERNAME
                                        onOpenTrial { errorText = it }
                                    }
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(16.dp),
                                color = trialBtnBg,
                                border = BorderStroke(1.2.dp, trialBtnBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("login_trial_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircleOutline,
                                        contentDescription = "فتح حساب تجريبي لمدة 3 أيام",
                                        tint = if (isDarkMode) Color(0xFF93C5FD) else HesabatyBlueDark,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "فتح حساب تجريبي",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkMode) Color(0xFFDBEAFE) else HesabatyBlueDark
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Direct Customer-Phone Account Creation & Device-Locked Activation
                            Surface(
                                onClick = { if (!isBusy) showActivationDialog = true },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isDarkMode) Color(0xFF2D4876) else Color(0xFFCBD9F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .testTag("login_activate_on_device_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "إنشاء أو تفعيل حساب على هذا الجهاز",
                                        tint = if (isDarkMode) Color(0xFF93C5FD) else HesabatyBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "إنشاء / تفعيل حساب على هذا الهاتف",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (isDarkMode) Color(0xFFBFDBFE) else HesabatyBlueDark
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Company Footer (Ta'sees Software)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = HesabatyBlue,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "تأسيس لصناعة البرمجيات",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                            Text(
                                text = "نحوّل أفكار الأعمال إلى أنظمة متكاملة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Support Pill Button
                    Surface(
                        onClick = {
                            WhatsAppHelper.openWhatsAppChat(
                                context = context,
                                rawPhone = PhoneUtils.SUPPORT_PHONE_LOCAL,
                                message = WhatsAppHelper.buildSupportMessage(BuildConfig.VERSION_NAME, null)
                            )
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isDarkMode) Color(0xCC162542) else Color(0xE6EFF6FF),
                        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF2D4876) else Color(0xFFBFDBFE)),
                        modifier = Modifier.testTag("login_support_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "اتصال بالدعم الفني",
                                tint = HesabatyBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "الدعم الفني   ${PhoneUtils.SUPPORT_PHONE_LOCAL}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = titleColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                }
            }

            // Floating Light/Dark Theme Toggle rendered above scrollable form
            Surface(
                onClick = { onToggleDarkMode(!isDarkMode) },
                shape = CircleShape,
                color = if (isDarkMode) Color(0xFF1A2942) else Color(0xE6FFFFFF),
                border = BorderStroke(
                    1.dp,
                    if (isDarkMode) Color(0xFF314970) else Color(0xFFCBD9F0)
                ),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .size(48.dp)
                    .testTag("login_theme_toggle")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isDarkMode,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(220))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150)))
                        },
                        label = "login_theme_icon"
                    ) { dark ->
                        Icon(
                            imageVector = if (dark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (dark) "تفعيل الوضع الفاتح" else "تفعيل الوضع الغامق",
                            tint = if (dark) Color(0xFFFBBF24) else HesabatyBlueDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showActivationDialog) {
        CustomerPhoneActivationDialog(
            deviceLabel = deviceLabel.ifBlank { "هذا الهاتف" },
            deviceActivationCode = deviceActivationCode.ifBlank { "—" },
            isBusy = isBusy,
            onDismiss = { showActivationDialog = false },
            onCreateDirectlyOnPhone = { adminU, adminP, sName, oName, addr, ph, uName, pass, confPass, onErr ->
                onCreateOnThisPhone(adminU, adminP, sName, oName, addr, ph, uName, pass, confPass) { err ->
                    onErr(err)
                }
            },
            onActivateWithToken = { rawTok, onErr ->
                onActivateWithToken(rawTok) { err ->
                    onErr(err)
                }
            }
        )
    }
}

/**
 * Full-screen blocker shown once the 72-hour trial expires or device clock rollback is detected.
 */
@Composable
fun TrialExpiredScreen(
    isClockTampered: Boolean,
    trialStartedAt: Long?,
    trialExpiresAt: Long?,
    isDarkMode: Boolean,
    onReturnToLogin: () -> Unit
) {
    val context = LocalContext.current
    val extras = HesabatyThemeExtras.colors
    val cardBg = if (isDarkMode) Color(0xFF131E34) else Color.White
    val cardBorder = if (isDarkMode) Color(0xFF233554) else Color(0xFFE3ECF8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HesabatyWaveBackground(isDarkMode = isDarkMode, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isClockTampered) extras.warningContainer else extras.dangerContainer,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isClockTampered) Icons.Default.Warning else Icons.Default.HourglassDisabled,
                                contentDescription = "انتهاء الفترة التجريبية",
                                tint = if (isClockTampered) extras.warning else extras.danger,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isClockTampered) "تم إيقاف الحساب التجريبي" else "انتهت الفترة التجريبية",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = AuthRepository.TRIAL_EXPIRED_MESSAGE,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("trial_expired_message")
                    )

                    if (isClockTampered) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = extras.warningContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "تنبيه أمني: تم رصد تغيير غير طبيعي في وقت وتاريخ الجهاز. لحماية نظام التراخيص تم إيقاف النسخة التجريبية مؤقتًا حتى التواصل مع الإدارة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.warning,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (trialStartedAt != null && trialExpiresAt != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "بداية التجربة: ${EgyptTime.formatDateTime(trialStartedAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "نهاية التجربة: ${EgyptTime.formatDateTime(trialExpiresAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val msg = "السلام عليكم، انتهت الفترة التجريبية لبرنامج حساباتي | HESABATY وأرغب في تفعيل النسخة الكاملة لمحلي."
                            WhatsAppHelper.openWhatsAppChat(
                                context = context,
                                rawPhone = PhoneUtils.SUPPORT_PHONE_LOCAL,
                                message = msg
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trial_expired_whatsapp_button")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تفعيل عبر الدعم الفني (${PhoneUtils.SUPPORT_PHONE_LOCAL})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onReturnToLogin,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("trial_expired_return_login_button")
                    ) {
                        Text("تسجيل الدخول بحساب مُفعَّل / الإدارة", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun HesabatyWaveBackground(
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Top-left sweeping blue organic wave
        val topSoftWave = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.62f, 0f)
            cubicTo(w * 0.42f, h * 0.09f, w * 0.22f, h * 0.14f, 0f, h * 0.19f)
            close()
        }
        drawPath(
            path = topSoftWave,
            brush = Brush.linearGradient(
                colors = if (isDarkMode) {
                    listOf(Color(0xFF1E3A8A).copy(alpha = 0.45f), Color(0xFF1D4ED8).copy(alpha = 0.18f))
                } else {
                    listOf(Color(0xFF93C5FD).copy(alpha = 0.45f), Color(0xFFDBEAFE).copy(alpha = 0.25f))
                }
            )
        )

        val topMainWave = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.48f, 0f)
            cubicTo(w * 0.30f, h * 0.06f, w * 0.16f, h * 0.11f, 0f, h * 0.135f)
            close()
        }
        drawPath(
            path = topMainWave,
            brush = Brush.linearGradient(
                colors = if (isDarkMode) {
                    listOf(Color(0xFF0D47A1).copy(alpha = 0.85f), Color(0xFF1877F2).copy(alpha = 0.55f))
                } else {
                    listOf(Color(0xFF1877F2), Color(0xFF3B82F6))
                }
            )
        )

        // Bottom organic blue waves
        val bottomSoftWave = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.92f)
            cubicTo(w * 0.26f, h * 0.90f, w * 0.52f, h * 0.95f, w, h * 0.86f)
            lineTo(w, h)
            close()
        }
        drawPath(
            path = bottomSoftWave,
            brush = Brush.linearGradient(
                colors = if (isDarkMode) {
                    listOf(Color(0xFF1E3A8A).copy(alpha = 0.4f), Color(0xFF2563EB).copy(alpha = 0.28f))
                } else {
                    listOf(Color(0xFF93C5FD).copy(alpha = 0.42f), Color(0xFFBFDBFE).copy(alpha = 0.48f))
                }
            )
        )

        val bottomMainWave = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.945f)
            cubicTo(w * 0.30f, h * 0.925f, w * 0.62f, h * 0.975f, w, h * 0.90f)
            lineTo(w, h)
            close()
        }
        drawPath(
            path = bottomMainWave,
            brush = Brush.linearGradient(
                colors = if (isDarkMode) {
                    listOf(Color(0xFF0D47A1).copy(alpha = 0.85f), Color(0xFF1877F2).copy(alpha = 0.72f))
                } else {
                    listOf(Color(0xFF2563EB), Color(0xFF0D47A1))
                }
            )
        )
    }
}

@Composable
fun BiometricLockScreen(
    shopName: String,
    onRequestBiometric: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "قفل البصمة",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "حساباتي مقفل بالبصمة",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = shopName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestBiometric,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 320.dp)
                .heightIn(min = 48.dp)
        ) {
            Text("فتح بالبصمة / قفل الجهاز")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onLogout) {
            Text("تسجيل الدخول بحساب آخر")
        }
    }
}

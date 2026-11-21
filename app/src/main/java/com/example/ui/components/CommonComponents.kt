package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HesabatyThemeExtras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabatyTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val uiScale = HesabatyThemeExtras.uiScale
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("top_bar_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun HesabatyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(1.dp, HesabatyThemeExtras.colors.cardBorder)
    val cardPadding = HesabatyThemeExtras.uiScale.cardPadding
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = border
        ) {
            Column(modifier = Modifier.padding(cardPadding), content = content)
        }
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = border
        ) {
            Column(modifier = Modifier.padding(cardPadding), content = content)
        }
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    accentContainer: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val uiScale = HesabatyThemeExtras.uiScale
    val iconBoxSize = if (uiScale.isCompact) 32.dp else 38.dp
    HesabatyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 7.dp else 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(iconBoxSize)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(if (uiScale.isCompact) 17.dp else 20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(if (uiScale.isCompact) 6.dp else 10.dp))
        Text(
            text = value,
            style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun QuickActionTile(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    accentContainer: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiScale = HesabatyThemeExtras.uiScale
    val tileMinHeight = when {
        uiScale.isNarrow -> 86.dp
        uiScale.isCompact -> 96.dp
        else -> 110.dp
    }
    val outerCircle = when {
        uiScale.isNarrow -> 42.dp
        uiScale.isCompact -> 48.dp
        else -> 56.dp
    }
    val innerCircle = when {
        uiScale.isNarrow -> 32.dp
        uiScale.isCompact -> 36.dp
        else -> 42.dp
    }
    val iconSize = when {
        uiScale.isNarrow -> 20.dp
        uiScale.isCompact -> 23.dp
        else -> 27.dp
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = tileMinHeight)
            .testTag(testTag),
        shape = RoundedCornerShape(if (uiScale.isCompact) 18.dp else 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, HesabatyThemeExtras.colors.cardBorder.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (uiScale.isCompact) 4.dp else 8.dp,
                    vertical = if (uiScale.isCompact) 10.dp else 13.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(outerCircle)
                    .clip(CircleShape)
                    .background(accentContainer.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(innerCircle)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = accentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
            Spacer(modifier = Modifier.height(if (uiScale.isCompact) 6.dp else 9.dp))
            Text(
                text = label,
                style = if (uiScale.isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusBadge(
    label: String,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = backgroundColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(text = actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (uiScale.isCompact) 18.dp else 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (uiScale.isCompact) 52.dp else 64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (uiScale.isCompact) 26.dp else 32.dp)
            )
        }
        Spacer(modifier = Modifier.height(if (uiScale.isCompact) 10.dp else 14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabatyDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = state.selectedDateMillis ?: initialMillis
                    onDateSelected(chosen)
                    onDismiss()
                }
            ) {
                Text("تأكيد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun SummaryStatPill(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (uiScale.isCompact) 6.dp else 8.dp,
                vertical = if (uiScale.isCompact) 6.dp else 8.dp
            ),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = if (uiScale.isCompact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

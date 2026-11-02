package com.classsoftwarehub.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.classsoftwarehub.android.data.model.Software
import com.classsoftwarehub.android.data.model.SoftwareCategory
import com.classsoftwarehub.android.ui.theme.CategoryOffice
import com.classsoftwarehub.android.ui.theme.CategoryOther
import com.classsoftwarehub.android.ui.theme.CategoryRemote
import com.classsoftwarehub.android.ui.theme.CategorySystemMaintenance
import com.classsoftwarehub.android.ui.theme.CategoryTeaching

fun categoryColor(category: SoftwareCategory): Color = when (category) {
    SoftwareCategory.SYSTEM_MAINTENANCE -> CategorySystemMaintenance
    SoftwareCategory.TEACHING -> CategoryTeaching
    SoftwareCategory.OFFICE -> CategoryOffice
    SoftwareCategory.REMOTE -> CategoryRemote
    SoftwareCategory.OTHER -> CategoryOther
}

/** 软件图标：有远程图标用 Coil 加载，否则以分类色浅底 + 首字母兜底 */
@Composable
fun SoftwareIcon(software: Software, modifier: Modifier = Modifier) {
    val url = software.iconUrl
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = software.name,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        val base = categoryColor(software.category)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(base.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = software.name.take(1).uppercase(),
                color = base,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 空状态占位 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 分类小标签 */
@Composable
fun CategoryBadge(category: SoftwareCategory, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(categoryColor(category).copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            category.label,
            style = MaterialTheme.typography.labelSmall,
            color = categoryColor(category),
            fontWeight = FontWeight.Medium,
        )
    }
}

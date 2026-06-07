package com.example.androiddevagent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androiddevagent.R
<<<<<<< HEAD
import com.example.androiddevagent.ui.theme.DevAgentTheme
=======
>>>>>>> dev-commercial-v2

@Composable
fun LoadingIndicator(
    statusMessage: String?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ai-loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ai-loading-rotation"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
<<<<<<< HEAD
            containerColor = DevAgentTheme.colors.aiResponseContainer
=======
            containerColor = MaterialTheme.colorScheme.secondaryContainer
>>>>>>> dev-commercial-v2
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
            Text(
                text = statusMessage ?: stringResource(R.string.loading_default_message),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
<<<<<<< HEAD
                color = DevAgentTheme.colors.onAiResponseContainer
=======
                color = MaterialTheme.colorScheme.onSecondaryContainer
>>>>>>> dev-commercial-v2
            )
        }
    }
}

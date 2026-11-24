package com.arrowking.arrowking.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arrowking.arrowking.R

/**
 * Arrow King in-app emblem rendered from the configured app logo asset.
 */
@Composable
fun ArrowKingLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    shineFraction: Float = -1f
) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        modifier = modifier
            .size(size)
            .shadow(8.dp, shape)
            .clip(shape)
    ) {
        Image(
            painter = painterResource(id = R.drawable.arrow_king_app_logo),
            contentDescription = "Arrow King logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (shineFraction in 0f..1f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val sx = -w * 0.3f + shineFraction * (w * 1.6f)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        start = Offset(sx - w * 0.12f, 0f),
                        end = Offset(sx + w * 0.12f, h)
                    ),
                    start = Offset(sx, 0f),
                    end = Offset(sx - w * 0.25f, h),
                    strokeWidth = w * 0.18f
                )
            }
        }
    }
}

package com.arrowking.arrowking.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arrowking.arrowking.R
import com.arrowking.arrowking.ui.components.ArrowKingLogoMark
import com.arrowking.arrowking.ui.theme.EspressoBrown
import com.arrowking.arrowking.ui.theme.GoldenBrown
import com.arrowking.arrowking.ui.theme.PaperBeige
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class SplashArrowSpec(
    val color: Color,
    val startNorm: Offset,
    val bendNorm: Offset,
    val delayFrac: Float
)

@Composable
fun SplashScreen(
    progress: Float,
    reduceAnimations: Boolean
) {
    val logoScale = remember { Animatable(if (reduceAnimations) 1f else 0.6f) }
    val logoAlpha = remember { Animatable(0f) }
    val exitLift = remember { Animatable(0f) }

    LaunchedEffect(progress, reduceAnimations) {
        if (reduceAnimations) {
            if (logoAlpha.value == 0f) {
                logoAlpha.animateTo(1f, tween(350))
            }
        } else {
            if (progress >= 0.64f && logoAlpha.value == 0f) {
                logoAlpha.animateTo(1f, tween(260))
                logoScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            if (progress >= 0.96f && exitLift.value == 0f) {
                exitLift.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            }
        }
    }

    val arrowSpecs = remember {
        listOf(
            SplashArrowSpec(Color(0xFF5A392B), Offset(0.05f, 0.14f), Offset(0.32f, 0.14f), 0.06f),
            SplashArrowSpec(Color(0xFF1E5BB8), Offset(0.95f, 0.18f), Offset(0.68f, 0.18f), 0.10f),
            SplashArrowSpec(Color(0xFFC63A32), Offset(0.06f, 0.42f), Offset(0.28f, 0.52f), 0.14f),
            SplashArrowSpec(Color(0xFF5A392B), Offset(0.94f, 0.44f), Offset(0.72f, 0.54f), 0.17f),
            SplashArrowSpec(Color(0xFF1E5BB8), Offset(0.18f, 0.05f), Offset(0.18f, 0.30f), 0.20f),
            SplashArrowSpec(Color(0xFFC63A32), Offset(0.82f, 0.05f), Offset(0.82f, 0.32f), 0.23f),
            SplashArrowSpec(Color(0xFF5A392B), Offset(0.08f, 0.82f), Offset(0.35f, 0.82f), 0.26f),
            SplashArrowSpec(Color(0xFF1E5BB8), Offset(0.92f, 0.80f), Offset(0.66f, 0.80f), 0.29f),
            SplashArrowSpec(Color(0xFFC63A32), Offset(0.34f, 0.94f), Offset(0.34f, 0.70f), 0.32f),
            SplashArrowSpec(Color(0xFF5A392B), Offset(0.66f, 0.94f), Offset(0.66f, 0.70f), 0.35f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperBeige)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.5f, h * 0.44f)

            // Soft vignette
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x223E2B22)),
                    center = center,
                    radius = maxOf(w, h) * 0.78f
                )
            )

            if (!reduceAnimations) {
                val strokePx = 4.5.dp.toPx()
                val pathMeasure = PathMeasure()

                // 0.2 - 1.9s: pen-drawing snake arrows converging to center
                for (spec in arrowSpecs) {
                    val localDraw = ((progress - spec.delayFrac) / 0.36f).coerceIn(0f, 1f)
                    if (localDraw <= 0f) continue

                    val convergeFrac = ((progress - 0.46f) / 0.22f).coerceIn(0f, 1f)
                    val fullPath = Path().apply {
                        moveTo(spec.startNorm.x * w, spec.startNorm.y * h)
                        lineTo(spec.bendNorm.x * w, spec.bendNorm.y * h)
                        lineTo(center.x, center.y)
                    }
                    pathMeasure.setPath(fullPath, false)
                    val totalLen = pathMeasure.length
                    val startDist = totalLen * (convergeFrac * 0.85f)
                    val endDist = totalLen * localDraw
                    if (endDist > startDist + 1f) {
                        val segment = Path()
                        pathMeasure.getSegment(startDist, endDist, segment, true)
                        val alpha = (1f - convergeFrac * 0.9f).coerceIn(0f, 1f)
                        drawPath(
                            path = segment,
                            color = spec.color.copy(alpha = alpha),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        val tipPos = pathMeasure.getPosition(endDist)
                        val tan = pathMeasure.getTangent(endDist)
                        if (tan != Offset.Zero) {
                            val perp = Offset(-tan.y, tan.x)
                            val headSize = 9.dp.toPx()
                            val headPath = Path().apply {
                                moveTo(tipPos.x + tan.x * headSize, tipPos.y + tan.y * headSize)
                                lineTo(tipPos.x - tan.x * headSize * 0.4f + perp.x * headSize * 0.7f, tipPos.y - tan.y * headSize * 0.4f + perp.y * headSize * 0.7f)
                                lineTo(tipPos.x - tan.x * headSize * 0.4f - perp.x * headSize * 0.7f, tipPos.y - tan.y * headSize * 0.4f - perp.y * headSize * 0.7f)
                                close()
                            }
                            drawPath(headPath, color = spec.color.copy(alpha = alpha))
                        }
                    }
                }

                // 1.3 - 1.9s convergence glow point & ripple
                val glowFrac = ((progress - 0.46f) / 0.24f).coerceIn(0f, 1f)
                if (glowFrac > 0f && progress < 0.88f) {
                    val pulseAlpha = sin(glowFrac * PI).toFloat()
                    drawCircle(
                        color = Color(0xFFFFE082).copy(alpha = 0.55f * pulseAlpha),
                        radius = 28.dp.toPx() * (0.4f + glowFrac),
                        center = center
                    )
                }

                // 1.8 - 2.4s circular ripple + sparkles
                val rippleFrac = ((progress - 0.64f) / 0.24f).coerceIn(0f, 1f)
                if (rippleFrac > 0f && rippleFrac < 1f) {
                    drawCircle(
                        color = GoldenBrown.copy(alpha = (1f - rippleFrac) * 0.45f),
                        radius = 40.dp.toPx() + rippleFrac * 150.dp.toPx(),
                        center = center,
                        style = Stroke(width = 3.dp.toPx() * (1f - rippleFrac * 0.5f))
                    )
                    for (i in 0 until 8) {
                        val angle = (i * 45.0) * PI / 180.0
                        val dist = 62.dp.toPx() + rippleFrac * 68.dp.toPx()
                        val sx = center.x + cos(angle).toFloat() * dist
                        val sy = center.y + sin(angle).toFloat() * dist
                        drawCircle(
                            color = GoldenBrown.copy(alpha = (1f - rippleFrac) * 0.85f),
                            radius = 3.5.dp.toPx() * (1f - rippleFrac * 0.4f),
                            center = Offset(sx, sy)
                        )
                    }
                }
            }
        }

        val shine = if (!reduceAnimations && progress in 0.68f..0.90f) {
            ((progress - 0.68f) / 0.22f).coerceIn(0f, 1f)
        } else -1f

        val liftY = -(exitLift.value * 44.dp.value)
        val fadeOut = 1f - exitLift.value * 0.35f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, liftY.roundToInt()) }
                .alpha(logoAlpha.value * fadeOut)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ArrowKingLogoMark(
                size = 112.dp,
                shineFraction = shine,
                modifier = Modifier.scale(logoScale.value)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                ),
                color = EspressoBrown
            )
            Spacer(modifier = Modifier.height(28.dp))

            // 2.2 - 2.8s slim gold/brown progress bar
            val barFill = if (reduceAnimations) {
                progress.coerceIn(0f, 1f)
            } else {
                ((progress - 0.72f) / 0.28f).coerceIn(0f, 1f)
            }
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFFDCC9AE))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFill.coerceAtLeast(0.06f))
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(GoldenBrown, Color(0xFF7D521B))
                            )
                        )
                )
            }
        }
    }
}

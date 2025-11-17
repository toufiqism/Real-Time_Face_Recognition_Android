package com.atharvakale.facerecognition

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

class SplashScreen : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceRecognitionTheme {
                SplashScreenContent(
                onNavigateToMain = {
                    val intent = Intent(this@SplashScreen, MainActivity::class.java)
                    finish()
                    startActivity(intent)
                }
            )
            }
        }
    }
}

@Composable
fun SplashScreenContent(onNavigateToMain: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(com.atharvakale.facerecognition.R.raw.facialrecognition1)
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            iterations = Int.MAX_VALUE,
            modifier = Modifier.fillMaxSize()
        )
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2500)
        onNavigateToMain()
    }
}


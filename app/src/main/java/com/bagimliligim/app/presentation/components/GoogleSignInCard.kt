package com.bagimliligim.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bagimliligim.app.R

@Composable
fun GoogleSignInCard(configured: Boolean, busy: Boolean, onSignIn: () -> Unit) {
    var consent by rememberSaveable { mutableStateOf(false) }
    HealthCard {
        Text("Bir hesap, kendi çevren.", style = MaterialTheme.typography.titleLarge)
        Text("Google hesabınla katıl; arkadaşlarınla küçük adımlarını paylaş. Kişisel takibini hesap açmadan da kullanabilirsin.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(consent, { consent = it }, enabled = !busy, modifier = Modifier.testTag("google-consent"))
            Text("Google hesap kimliğim, e-posta adresim ve temel profil bilgilerim hesap oluşturmak için Supabase'e iletilir. Bunu kabul ediyorum. İstatistik paylaşımını ayrıca ben seçerim.",
                style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = onSignIn, enabled = !busy && consent && configured,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F),
                disabledContainerColor = Color.White, disabledContentColor = Color(0xFF747775)),
            border = BorderStroke(1.dp, Color(0xFF747775)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("google-sign-in")) {
            Image(painterResource(R.drawable.google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Google ile devam et", fontFamily = FontFamily(Font(R.font.google_sans_medium, FontWeight.Medium)),
                fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (busy) Text("Bağlanıyor…", style = MaterialTheme.typography.bodySmall)
        if (!configured) Text("Bu test sürümünde Google bağlantısı henüz hazır değil.", style = MaterialTheme.typography.bodySmall)
        Text("Google şifren uygulamayla paylaşılmaz. Paylaşım tercihlerin başlangıçta kapalıdır.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

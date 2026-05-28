package com.senderlink.app.view

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import com.yalantis.ucrop.UCropActivity

/**
 * Subclase de UCropActivity que corrige el solapamiento con las barras del sistema.
 *
 * En Android 15/16 (targetSdk 35/36) edge-to-edge es forzado y tanto
 * windowOptOutEdgeToEdgeEnforcement como setDecorFitsSystemWindows son ignorados.
 * La solución es aplicar los insets de las barras del sistema como padding al
 * content view usando la API nativa (no compat) de forma inmediata y reactiva.
 */
class FixedUCropActivity : UCropActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 35) {
            applySystemBarInsets()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("NewApi") // sólo se llama desde SDK_INT >= 35; la anotación no se propaga a lambdas
    private fun applySystemBarInsets() {
        val content = window.decorView.findViewById<View>(android.R.id.content) ?: return

        // Listener para aplicar insets en cada cambio (rotación, etc.)
        content.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Aplicar insets actuales de forma inmediata si el window ya los tiene
        // (evita el "flash" de layout incorrecto en el primer frame)
        val currentInsets = window.decorView.rootWindowInsets
        if (currentInsets != null) {
            val bars = currentInsets.getInsets(android.view.WindowInsets.Type.systemBars())
            content.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            // Insets aún no disponibles: solicitarlos para el próximo frame
            content.requestApplyInsets()
        }
    }
}

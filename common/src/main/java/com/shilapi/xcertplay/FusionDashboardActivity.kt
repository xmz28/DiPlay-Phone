package com.shilapi.xcertplay

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Compatibility entry; both display pages are owned by CarPlayHostActivity. */
class FusionDashboardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, CarPlayHostActivity::class.java).putExtra("display_page", "fusion")
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        finish()
    }
}

package com.example.blogsphere.ui.about

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Pops an informational AlertDialog (the "info" variant) and finishes after user dismisses.
 */
class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AlertDialog.Builder(this)
            .setTitle("About BlogSphere")
            .setIcon(com.example.blogsphere.R.drawable.ic_info)
            .setMessage(
                "BlogSphere v1.0\n\n" +
                "A demo blog application built for the Mobile Application Development exam.\n\n" +
                "Built with Kotlin + Android View System.\n" +
                "Featuring RecyclerView, CardView, DrawerLayout, AlertDialogs, Fragments, " +
                "Activity Lifecycle, Broadcast Receivers, and runtime permissions.\n\n" +
                "All data is in-memory and resets when the app process is killed — demo data repopulates on next launch."
            )
            .setPositiveButton("OK") { d, _ -> d.dismiss() }
            .setOnDismissListener { finish() }
            .show()
    }
}

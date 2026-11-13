package com.github.fakegps.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.github.fakegps.BookmarkRepository
import com.github.fakegps.FakeGpsUtils
import com.github.fakegps.model.LocBookmark
import com.github.fakegps.model.LocPoint
import com.tencent.fakegps.R
import com.tencent.fakegps.databinding.ActivityBookmarkBinding
import kotlinx.coroutines.launch

/**
 * Dialog-style screen for creating a bookmark.
 */
class BookmarkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarkBinding
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra(EXTRA_NAME)
        val locPoint = IntentCompat.getSerializableExtra(
            intent, EXTRA_LOCATION, LocPoint::class.java
        )
        binding.inputName.setText(name)
        if (!TextUtils.isEmpty(name)) {
            binding.inputName.setSelection(name?.length ?: 0)
        }
        if (locPoint != null) {
            binding.inputLoc.setText(locPoint.toString())
        }

        binding.btnOk.setOnClickListener { saveBookmark() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun saveBookmark() {
        if (saving) {
            return
        }
        val name = binding.inputName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.bookmark_name_required, Toast.LENGTH_SHORT).show()
            return
        }

        val locPointFromInput = FakeGpsUtils.getLocPointFromInput(this, binding.inputLoc)
        if (locPointFromInput == null) {
            Toast.makeText(this, R.string.bookmark_location_invalid, Toast.LENGTH_SHORT).show()
            return
        }

        saving = true
        binding.btnOk.isEnabled = false
        lifecycleScope.launch {
            val insertId = BookmarkRepository.insert(LocBookmark(name, locPointFromInput))
            if (insertId != -1L) {
                Toast.makeText(this@BookmarkActivity, R.string.bookmark_saved, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(
                    this@BookmarkActivity, R.string.bookmark_save_failed, Toast.LENGTH_SHORT
                ).show()
                saving = false
                binding.btnOk.isEnabled = true
            }
        }
    }

    companion object {
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_NAME = "extra_name"

        @JvmStatic
        fun startPage(context: Context, name: String, locPoint: LocPoint) {
            val intent = Intent(context, BookmarkActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(EXTRA_NAME, name)
            intent.putExtra(EXTRA_LOCATION, locPoint)
            context.startActivity(intent)
        }
    }
}

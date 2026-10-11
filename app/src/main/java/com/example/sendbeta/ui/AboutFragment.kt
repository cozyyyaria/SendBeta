package com.example.sendbeta.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sendbeta.R

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up GitHub button
        view.findViewById<Button>(R.id.btn_github)?.setOnClickListener {
            val url = "https://github.com/cozyyyaria/sendbeta_UI"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

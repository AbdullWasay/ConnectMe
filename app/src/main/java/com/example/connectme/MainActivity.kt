package com.example.connectme

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main) // Screen 1 layout

        Handler(Looper.getMainLooper()).postDelayed({
            showScreen2()
        }, 3000)
    }

    private fun showScreen2() {
        setContentView(R.layout.screen2) // Screen 2 layout

        val registerText = findViewById<TextView>(R.id.secondText)
        registerText.setOnClickListener {
            showScreen3()
        }

        val loginText = findViewById<TextView>(R.id.loginButton)
        loginText.setOnClickListener {
            showScreen4()
        }
    }

    private fun showScreen3() {
        setContentView(R.layout.screen3) // Screen 3 layout

        val loginText = findViewById<TextView>(R.id.secondText)
        loginText.setOnClickListener {
            showScreen2()
        }


    }

    private fun showScreen4() {
        setContentView(R.layout.screen4) // Screen 4 layout

        val messagesButton = findViewById<ImageButton>(R.id.MessagesButtonm)
        messagesButton.setOnClickListener {
            showScreen5()
        }
    }

    private fun showScreen5() {
        setContentView(R.layout.screen5) // Screen 5 layout

        val backButton = findViewById<ImageView>(R.id.BackButtonm)
        backButton.setOnClickListener {
            showScreen4()
        }
    }
}

package com.example.legalwrite

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import com.example.legalwriteapp.MainActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        val startButton: Button = findViewById(R.id.startButton)
        startButton.visibility = View.GONE // Initially hide the button

        // Find the LinearLayouts for the blue bubble text
        val bubble1 = findViewById<LinearLayout>(R.id.bubble1)
        val bubble2 = findViewById<LinearLayout>(R.id.bubble2)
        val bubble3 = findViewById<LinearLayout>(R.id.bubble3)

        // Find the TextViews for the blue bubble text
        val textView1 = findViewById<TextView>(R.id.textViewMessage1)
        val textView2 = findViewById<TextView>(R.id.textViewMessage2)
        val textView3 = findViewById<TextView>(R.id.textViewMessage3)

        // Initially hide the blue bubbles
        bubble1.visibility = View.GONE
        bubble2.visibility = View.GONE
        bubble3.visibility = View.GONE

        // Messages to show with the typing effect
        val message1 = "Welcome to LegalDocs!"
        val message2 = "LegalDocs is a one stop shop for all your legal\ndocument requirements.\nMake legal documents with ease, wherever you want, whenever you want."
        val message3 = "Just type the information you want in the\ndocument and the ML model will generate\nyour legal document in minutes."

        // Start typing effect after a small delay
        Handler(Looper.getMainLooper()).postDelayed({
            // Show the blue bubbles once typing effect starts
            bubble1.visibility = View.VISIBLE
            simulateTypingEffect(textView1, message1) {
                bubble2.visibility = View.VISIBLE
                simulateTypingEffect(textView2, message2) {
                    bubble3.visibility = View.VISIBLE
                    simulateTypingEffect(textView3, message3) {
                        // After all messages are typed, show the "Get Started" button
                        startButton.visibility = View.VISIBLE
                    }
                }
            }
        }, 500) // Small delay before the typing effect starts

        // Handle the "Get Started" button click
        startButton.setOnClickListener {
            // Transition to MainActivity with custom animation
            val intent = Intent(this, MainActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
            startActivity(intent, options.toBundle())
            finish()
        }
    }

    // Method to simulate typing effect on TextViews
    private fun simulateTypingEffect(textView: TextView, message: String, onComplete: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val delay = 50L // Delay between each character in milliseconds
        val stringBuilder = StringBuilder()

        // Clear existing text in the TextView
        textView.text = ""

        // Iterate over the message and add one character at a time with a delay
        for (i in message.indices) {
            handler.postDelayed({
                stringBuilder.append(message[i]) // Add next character
                textView.text = stringBuilder.toString() // Update the TextView with the current text
            }, i * delay) // Delay increases with each character
        }

        // After the last character is typed, trigger the onComplete callback
        handler.postDelayed({
            onComplete()
        }, message.length * delay)
    }
}
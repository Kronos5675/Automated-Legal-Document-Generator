package com.example.legalwriteapp

import android.Manifest
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import android.content.ContentValues
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.app.ActivityOptionsCompat
import com.example.legalwrite.WelcomeActivity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import com.example.legalwrite.R
import android.provider.MediaStore
import android.widget.Button
import android.util.Log
import android.widget.ImageButton
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.android.volley.Request.Method
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.legalwrite.Message

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var willButton: Button
    private lateinit var userInput: EditText
    private lateinit var sendButton: AppCompatImageButton
    private val messagesList = mutableListOf<Message>()
    private var inputText = ""
    private var verificationText = ""
    private val REQUEST_CODE_WRITE_STORAGE = 1
    companion object {
        const val REQUEST_WRITE_PERMISSION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            // Start the WelcomeActivity
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish() // Close the current activity (MainActivity)
        }

        recyclerView = findViewById(R.id.recyclerView)
        userInput = findViewById(R.id.userInput)
        sendButton = findViewById(R.id.sendButton)
        willButton = findViewById(R.id.willButton)

        messageAdapter = MessageAdapter(messagesList)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = messageAdapter
        }
        Handler().postDelayed(
        {
            addMessage("Hello!\nHow can I assist you today?", false)
        },1000)
        Handler().postDelayed(
        {
            willButton.visibility=View.VISIBLE
        },2000)
        willButton.setOnClickListener {
            willButton.visibility=View.GONE
            addMessage("\uD83D\uDCDC Wills",true)
            Handler().postDelayed({
                addMessage("Alright! We'll get your will made in no time!", false)
            }, 1000)
            Handler().postDelayed({
                addMessage("Please Provide me the details of your will!", false)
            }, 3000)
        }

        sendButton.setOnClickListener {
            val userText = userInput.text.toString()
            hideKeyboard()
            if (userText.isNotEmpty()) {
                addMessage(userText, true)
                userInput.text.clear()
                sendMessageToServer(userText)
            }
        }

        // Check permissions at the beginning
        checkPermissions()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: View(this) // Get the currently focused view
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back to WelcomeActivity
                val intent = Intent(this@MainActivity, WelcomeActivity::class.java)
                startActivity(intent)
                finish() // Close the current activity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        if (!isUser) {
            val typingDelay: Long = 30L
            var currentText = ""
            val handler = Handler()

            // Runnable to add characters one by one
            val runnable = object : Runnable {
                var index = 0

                override fun run() {
                    if (index < text.length) {
                        currentText += text[index]

                        // Update the last message instead of adding a new one
                        if (messagesList.isNotEmpty() && !isUser) {
                            // Update the last message
                            messagesList[messagesList.size - 1] = Message(currentText, isUser)
                            messageAdapter.notifyItemChanged(messagesList.size - 1) // Update only the last item
                        } else {
                            // If the list is empty (first message), add a new one
                            messagesList.add(Message(currentText, isUser))
                            messageAdapter.notifyItemInserted(messagesList.size - 1) // Add only the new message
                        }

                        recyclerView.scrollToPosition(messagesList.size - 1) // Scroll to the last position
                        index++
                        handler.postDelayed(this, typingDelay) // Delay between characters
                    } else {
                        // Once typing is complete, hide the typing indicator
                        handler.postDelayed({
                            messageAdapter.hideTypingIndicator()
                        }, 30L) // Delay to keep typing indicator visible for a while after typing
                    }
                }
            }

            // Add an initial empty message for the bot to simulate typing
            messagesList.add(Message("", isUser))
            messageAdapter.notifyItemInserted(messagesList.size - 1) // Insert only the new placeholder message
            recyclerView.scrollToPosition(messagesList.size - 1) // Scroll to the last position

            // Start the typing effect
            handler.post(runnable)
        } else {
            // If it's a user message, just add it directly
            messagesList.add(Message(text, isUser))
            messageAdapter.notifyItemInserted(messagesList.size - 1) // Insert only the new user message
            recyclerView.scrollToPosition(messagesList.size - 1) // Scroll to the last position
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // For Android 9 and below, request WRITE_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_CODE_WRITE_STORAGE
                )
            }
        } else {
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
            }
        }
    }

    private fun sendMessageToServer(userText: String) {
        messageAdapter.showTypingIndicator()
        val url = "https://3c07-103-221-74-178.ngrok-free.app/generate_will"
        val queue = Volley.newRequestQueue(this)

        val jsonObject = JSONObject()
        jsonObject.put("input_text", userText)

        val request = JsonObjectRequest(Method.POST, url, jsonObject,
            { response ->
                messageAdapter.hideTypingIndicator()
                Log.d("Response", "Server response: $response")
                if (response.has("question")) {
                    val question = response.getString("question")
                    val tag = response.getString("tag")
                    inputText = response.getString("input_text")
                    promptUserForMissingInfo(question, tag)
                } else if (response.has("will_summary")) {
                    verificationText = response.getString("will_summary")
                    inputText = response.getString("input_text")
                    promptUserForVerification(verificationText)
                } else {
                    addMessage("Your will is ready! Tap to download:", false)
                }
            },
            { error ->
                messageAdapter.hideTypingIndicator()
                Log.e("Error", "Error: ${error.message}")
                addMessage("Error: ${error.message}", false)
            })
        queue.add(request)
    }

    private fun promptUserForVerification(summary: String) {
        messageAdapter.hideTypingIndicator()
        Handler().postDelayed({
            addMessage("Will Details:\n$summary\n", false)
        },1000)
        Handler().postDelayed({
            addMessage("Please Verify the Will Details and type 'Verified' if they are correct or 'Not Verified' if they are incorrect",false)
        },24000)
        sendButton.setOnClickListener {
            val responseText = userInput.text.toString().trim().lowercase()
            hideKeyboard()
            if (responseText == "verified" || responseText == "not verified") {
                addMessage("$responseText", true)
                userInput.text.clear()
                if (responseText == "verified") {
                    sendVerificationToServer(true)
                } else {
                    restartProcess()
                }
            } else {
                Toast.makeText(this, "Please type 'Verified' or 'Not Verified'", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restartProcess() {
        Handler().postDelayed({
            addMessage("No Worries! Let's start over",false)
            },1000)
        Handler().postDelayed({
            messagesList.clear()
            messageAdapter.notifyDataSetChanged()
        },3000)
        Handler().postDelayed(
            {
                addMessage("Hello!\nHow can I assist you today?", false)
            },4000)
        Handler().postDelayed(
            {
                willButton.visibility=View.VISIBLE
            },6000)
        willButton.setOnClickListener {
            willButton.visibility=View.GONE
            addMessage("\uD83D\uDCDC Wills",true)
            Handler().postDelayed({
                addMessage("Alright! We'll get your will made in no time!", false)
            }, 1000)
            Handler().postDelayed({
                addMessage("Please Provide me the details of your will!", false)
            }, 3000)
        }
        sendButton.setOnClickListener {
            val userText = userInput.text.toString()
            hideKeyboard()
            if (userText.isNotEmpty()) {
                addMessage(userText, true)
                userInput.text.clear()
                sendMessageToServer(userText)  // Restart the process by sending user input
            }
        }
    }


    private fun sendVerificationToServer(verified: Boolean) {
        val url = "https://3c07-103-221-74-178.ngrok-free.app/verify_details"
        val queue = Volley.newRequestQueue(this)

        val jsonObject = JSONObject().apply {
            put("verified", verified)
            put("input_text", inputText)
        }

        val request = object : JsonObjectRequest(
            Method.POST, url, jsonObject,
            { response ->
                Log.d("VerificationResponse", "Server response: $response")
                if (verified) {
                    // Handle the case when verification is successful.
                    if (response.has("will_pdf")) {
                        val pdfData = response.optString("will_pdf").toByteArray()
                        savePdfToDownloads(pdfData)
                    }

                    // Show confirmation messages after saving the PDF
                    Handler().postDelayed({
                        addMessage("PDF has been automatically saved in your Downloads folder.", false)
                    }, 1000)

                    Handler().postDelayed({
                        addMessage("Thank you for using LegalDocs!", false)
                    }, 3000)

                    Handler().postDelayed({
                        val intent = Intent(this, WelcomeActivity::class.java)
                        val options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
                        startActivity(intent, options.toBundle())
                        finish()
                    }, 5000)
                } else {
                    addMessage("Verification failed. Restarting the process.", false)
                }
            },
            { error ->
                Log.e("VerificationError", "Error: ${error.message}")
                addMessage("Error: ${error.message}", false)
            }
        ) {
            override fun parseNetworkResponse(response: NetworkResponse?): Response<JSONObject> {
                val contentType = response?.headers?.get("Content-Type")
                if (contentType?.contains("application/pdf") == true) {
                    val pdfData = response.data
                    savePdfToDownloads(pdfData)
                    return Response.success(JSONObject(), HttpHeaderParser.parseCacheHeaders(response))
                }
                return super.parseNetworkResponse(response)
            }
        }
        queue.add(request)
    }

    private fun savePdfToDownloads(pdfData: ByteArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "generated_will.pdf")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(pdfData)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val pdfFile = File(downloadsDir, "generated_will.pdf")
                FileOutputStream(pdfFile).use { outputStream ->
                    outputStream.write(pdfData)
                }
            }
            runOnUiThread {
                Toast.makeText(this, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e("FileError", "Error saving PDF: ${e.message}")
            runOnUiThread {
                Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun promptUserForMissingInfo(question: String, tag: String) {
        messageAdapter.hideTypingIndicator()
        Handler().postDelayed({
            addMessage(question, false)
        },3000)
        sendButton.setOnClickListener {
            val answer = userInput.text.toString()
            hideKeyboard()
            if (answer.isNotEmpty()) {
                addMessage(answer, true)
                userInput.text.clear()
                sendAnswerToServer(answer, tag)
            }
        }
    }
    private fun sendAnswerToServer(answer: String, tag: String) {
        messageAdapter.showTypingIndicator()
        val url = "https://3c07-103-221-74-178.ngrok-free.app/submit_answer"
        val queue = Volley.newRequestQueue(this)

        val jsonObject = JSONObject()
        jsonObject.put("answer", answer)
        jsonObject.put("tag", tag)
        jsonObject.put("input_text", inputText)

        val request = JsonObjectRequest(Method.POST, url, jsonObject,
            { response ->
                messageAdapter.hideTypingIndicator()
                Log.d("AnswerResponse", "Server response: $response")
                if (response.has("question")) {
                    val question = response.getString("question")
                    val tag = response.getString("tag")
                    promptUserForMissingInfo(question, tag)
                } else if (response.has("will_summary")) {
                    verificationText = response.getString("will_summary")
                    inputText = response.getString("input_text")
                    promptUserForVerification(verificationText)
                } else {
                    addMessage("Your will is ready! Tap to download:", false)
                }
            },
            { error ->
                messageAdapter.hideTypingIndicator()
                Log.e("AnswerError", "Error: ${error.message}")
                addMessage("Error: ${error.message}", false)
            })
        queue.add(request)
    }
}
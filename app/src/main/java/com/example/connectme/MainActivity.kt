package com.example.connectme
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.ImageView
import kotlin.math.abs
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_main) // Screen 1 layout

        Handler(Looper.getMainLooper()).postDelayed({
            showScreen2()
        }, 3000)
    }

    private fun showScreen2() {
        setContentView(R.layout.screen2)

        val registerText = findViewById<TextView>(R.id.secondText)
        registerText.setOnClickListener {
            showScreen3()
        }

        val loginButton = findViewById<Button>(R.id.loginButton)
        loginButton.setOnClickListener {
            val usernameInput = findViewById<EditText>(R.id.username).text.toString()
            val passwordInput = findViewById<EditText>(R.id.passwordtext).text.toString()

            if (usernameInput.isEmpty() || passwordInput.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                loginUser(usernameInput, passwordInput)
            }
        }
    }
    private fun loginUser(username: String, password: String) {
        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        usersRef.child(username).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val dbPassword = snapshot.child("password").value.toString()
                if (dbPassword == password) {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    showScreen4() // Navigate to home screen
                } else {
                    Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showScreen3() {
        setContentView(R.layout.screen3) // Screen 3 layout

        val loginText = findViewById<TextView>(R.id.secondText)
        loginText.setOnClickListener {
            showScreen2()
        }


        val registerButton = findViewById<Button>(R.id.registerButton)
        registerButton.setOnClickListener {
            val name = findViewById<EditText>(R.id.nameInput).text.toString()
            val username = findViewById<EditText>(R.id.usernameInput).text.toString()
            val phonenumber = findViewById<EditText>(R.id.phoneInput).text.toString()
            val email = findViewById<EditText>(R.id.emailInput).text.toString()
            val password = findViewById<EditText>(R.id.passwordInput).text.toString()

            signUpUser(name, username, phonenumber, email, password)



        }
    }

    private fun signUpUser(name: String, username: String, phone: String, email: String, password: String) {
        if (email.isEmpty() || password.isEmpty() || name.isEmpty() || username.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val database = FirebaseDatabase.getInstance()
        val usersRef = database.getReference("Users")

        // Check if username already exists
        usersRef.child(username).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                Toast.makeText(this, "Username already exists", Toast.LENGTH_SHORT).show()
            } else {
                // Save user data
                val userMap = mapOf(
                    "name" to name,
                    "username" to username,
                    "phone" to phone,
                    "email" to email,
                    "password" to password // In production, hash the password!
                )
                usersRef.child(username).setValue(userMap)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Signup successful", Toast.LENGTH_SHORT).show()
                        showScreen2()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Signup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun showScreen4() {
        setContentView(R.layout.screen4) // Screen 4 layout

        val messagesButton = findViewById<ImageView>(R.id.MessagesButtonm)
        messagesButton.setOnClickListener {
            showScreen5()
        }

        val addStory = findViewById<ImageButton>(R.id.AddStory)
        addStory.setOnClickListener {
            showScreen16()
        }


        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }


    }

    private fun showScreen5() {
        setContentView(R.layout.screen5) // Screen 5 layout

        //Back button
        val backButton = findViewById<ImageView>(R.id.BackButtonm)
        backButton.setOnClickListener {
            showScreen4()
        }

        //Open Chat
        val chat = findViewById<LinearLayout>(R.id.chat2)
        chat.setOnClickListener {
            showScreen6()
        }
    }



    private fun showScreen6() {
        setContentView(R.layout.screen6) // Set layout for Screen 6

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen5()
        }

        val callButton = findViewById<ImageView>(R.id.callbutton)
        callButton.setOnClickListener {
            showScreen8()
        }

        val videocallButton = findViewById<ImageView>(R.id.videocallbutton)
        videocallButton.setOnClickListener {
            showScreen9()
        }

        val rootView = findViewById<ScrollView>(R.id.messagearea)

        rootView.setOnTouchListener(object : View.OnTouchListener {
            private var startY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val endY = event.y
                        val deltaY = startY - endY

                        if (deltaY > 100) {
                            showScreen7()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun showScreen7() {
        setContentView(R.layout.screen7)

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen5()
        }

        val rootView = findViewById<ScrollView>(R.id.messagearea)

        rootView.setOnTouchListener(object : View.OnTouchListener {
            private var startY: Float = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.y
                    }
                    MotionEvent.ACTION_UP -> {
                        val endY = event.y
                        val deltaY = startY - endY

                        if (deltaY > 100) {
                            showScreen6()
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    private fun showScreen8() {
        setContentView(R.layout.screen8) // Screen 4 layout


        val EndCall = findViewById<ImageView>(R.id.btnEndCall)
        EndCall.setOnClickListener {
            showScreen6()
        }
    }

    private fun showScreen9() {
        setContentView(R.layout.screen9) // Screen 4 layout


        val EndCall = findViewById<ImageView>(R.id.btnEndCall)
        EndCall.setOnClickListener {
            showScreen6()
        }

    }

    private fun showScreen10() {
        setContentView(R.layout.screen10) // Screen 4 layout


        val followersPage = findViewById<LinearLayout>(R.id.FollowersPage)
        followersPage.setOnClickListener {
            showScreen11()
        }

        val followingPage = findViewById<LinearLayout>(R.id.FollowingPage)
        followingPage.setOnClickListener {
            showScreen12()
        }

        val editProfile = findViewById<ImageButton>(R.id.EditProfile)
        editProfile.setOnClickListener {
            showScreen13()
        }


        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }
    }


    private fun showScreen11() {
        setContentView(R.layout.screen11) // Screen 4 layout


        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen10()
        }

        val followingPage = findViewById<LinearLayout>(R.id.FollowingTab)
        followingPage.setOnClickListener {
            showScreen12()
        }


    }

    private fun showScreen12() {
        setContentView(R.layout.screen12) // Screen 4 layout

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen10()
        }

        val followersPage = findViewById<LinearLayout>(R.id.FollowersTab)
        followersPage.setOnClickListener {
            showScreen11()
        }

    }

    private fun showScreen13() {
        setContentView(R.layout.screen13) // Screen 4 layout

        val doneEditing = findViewById<TextView>(R.id.DoneEditing)
        doneEditing.setOnClickListener {
            showScreen10()
        }

    }


    private fun showScreen14() {
        setContentView(R.layout.screen14) // Screen 4 layout

        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }

    }

    private fun showScreen15() {
        setContentView(R.layout.screen15)

        val ClosePost = findViewById<ImageView>(R.id.btnClose)
        ClosePost.setOnClickListener {
            showScreen4()
        }

        val NextPost = findViewById<TextView>(R.id.btnNext)
        NextPost.setOnClickListener {
            showScreen17()
        }
    }
    private fun showScreen16() {

        setContentView(R.layout.screen16)

        val ClosePost = findViewById<ImageView>(R.id.btnClose)
        ClosePost.setOnClickListener {
            showScreen4()
        }

        val NextPost = findViewById<TextView>(R.id.btnNext)
        NextPost.setOnClickListener {
            showScreen17()
        }
    }
    private fun showScreen17() {
        setContentView(R.layout.screen17)

        val ClosePost = findViewById<ImageView>(R.id.btnClose)
        ClosePost.setOnClickListener {
            showScreen4()
        }

        val SharePost = findViewById<TextView>(R.id.btnShare)
        SharePost.setOnClickListener {
            showScreen4()
        }
    }
    private fun showScreen18() {
        setContentView(R.layout.screen18)

        val backButton = findViewById<ImageView>(R.id.BackButton)
        backButton.setOnClickListener {
            showScreen4()
        }

        val gotoHome = findViewById<ImageButton>(R.id.HomePage)
        gotoHome.setOnClickListener {
            showScreen4()
        }
        val gotoSearch = findViewById<ImageButton>(R.id.SearchPage)
        gotoSearch.setOnClickListener {
            showScreen14()
        }
        val gotoPost = findViewById<ImageButton>(R.id.PostPage)
        gotoPost.setOnClickListener {
            showScreen15()
        }
        val gotoProfile = findViewById<ImageButton>(R.id.ProfilePage)
        gotoProfile.setOnClickListener {
            showScreen10()
        }
        val gotoContacts = findViewById<ImageButton>(R.id.ContactsPage)
        gotoContacts.setOnClickListener {
            showScreen18()
        }
    }





}

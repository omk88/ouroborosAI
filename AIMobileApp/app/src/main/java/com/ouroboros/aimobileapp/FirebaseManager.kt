package com.ouroboros.aimobileapp

import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FirebaseManager private constructor() {
    private var apiKey: String? = null

    init {
        FirebaseApp.initializeApp(MyApplication.context)
    }

    fun fetchApiKey(callback: (String?) -> Unit) {
        if (apiKey != null) {
            callback(apiKey)
            return
        }

        val database = FirebaseDatabase.getInstance()
        val apiKeyRef = database.getReference("api-keys/a3D7b9H2z4X8Y5W1/key")

        apiKeyRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                apiKey = dataSnapshot.getValue(String::class.java)
                callback(apiKey)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                callback(null)
            }
        })
    }

    fun fetchApiKey2(callback: (String?) -> Unit) {
        if (apiKey != null) {
            callback(apiKey)
            return
        }

        val database = FirebaseDatabase.getInstance()
        val apiKeyRef = database.getReference("api-keys/a3D7b9H2z4X8Y5W1/key2")

        apiKeyRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                apiKey = dataSnapshot.getValue(String::class.java)
                callback(apiKey)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                callback(null)
            }
        })
    }

    companion object {
        @Volatile
        private var instance: FirebaseManager? = null

        fun getInstance(): FirebaseManager =
            instance ?: synchronized(this) {
                instance ?: FirebaseManager().also { instance = it }
            }
    }
}




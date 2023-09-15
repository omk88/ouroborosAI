package com.example.aimobileapp

data class ImageEditRequest(
    val image: ByteArray,
    val mask: ByteArray,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024"
)

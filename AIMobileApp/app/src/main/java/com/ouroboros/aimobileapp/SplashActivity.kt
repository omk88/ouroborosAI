package com.ouroboros.aimobileapp

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener

import com.bumptech.glide.load.DataSource
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition


class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val animImageView: ImageView = findViewById(R.id.anim_logo)
        val fixedImageView: ImageView = findViewById(R.id.fixed_logo)

        Glide.with(this)
            .asGif()
            .load(R.drawable.anim_logo)
            .listener(object : RequestListener<GifDrawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<GifDrawable>?, isFirstResource: Boolean): Boolean {
                    return false
                }

                override fun onResourceReady(resource: GifDrawable?, model: Any?, target: Target<GifDrawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    resource?.setLoopCount(1)
                    resource?.registerAnimationCallback(object : Animatable2Compat.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable?) {
                            fixedImageView.visibility = View.VISIBLE
                            animImageView.visibility = View.GONE
                        }
                    })
                    return false
                }
            })
            .into(animImageView)



        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("splash", "splash")
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out_2)
            finish()
        }, 1500)
    }
}


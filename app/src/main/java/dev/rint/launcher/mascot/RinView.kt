package dev.rint.launcher.mascot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.Choreographer
import android.view.View

/** Classic-View Rin for places without Compose (the accessibility overlay bubble). */
class RinView(context: Context) : View(context), Choreographer.FrameCallback {
    private val rig = RinRig()
    private val params = RinParams()
    private val bmp = Bitmap.createBitmap(RinRig.W, RinRig.H, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val src = Rect(RinRig.HEAD_X, RinRig.HEAD_Y, RinRig.HEAD_X + RinRig.HEAD_W, RinRig.HEAD_Y + RinRig.HEAD_H)
    private val dst = Rect()
    private var start = 0L
    private var running = false
    var talk = 0f
    var pose = Pose.HEAD

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start = System.nanoTime()
        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - start) / 1e9f
        animateRin(if (talk > 0.04f) Pose.TALK else pose, t, talk, params)
        rig.render(params)
        bmp.setPixels(rig.pixels, 0, RinRig.W, 0, 0, RinRig.W, RinRig.H)
        val k = minOf(width / src.width().toFloat(), height / src.height().toFloat())
        val w = (src.width() * k).toInt()
        val h = (src.height() * k).toInt()
        dst.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
        canvas.drawBitmap(bmp, src, dst, paint)
    }
}

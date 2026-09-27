package dev.rint.launcher.mascot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.Choreographer
import android.view.View

/** Classic-View Rin for places without Compose (the accessibility overlay bubble). */
class RinView(context: Context) : View(context), Choreographer.FrameCallback {
    private val painter = RinPainter()
    private val rig = RinRig()
    private val bmp = android.graphics.Bitmap.createBitmap(RinRig.W, RinRig.H, android.graphics.Bitmap.Config.ARGB_8888)
    private val px = android.graphics.Paint().apply { isFilterBitmap = false }
    private val pixelStyle get() = runCatching { dev.rint.launcher.RintApp.instance.stores.config.value.mascot.style == dev.rint.launcher.core.MascotStyle.PIXEL }.getOrDefault(true)
    private val params = RinParams()
    var accent: Int = 0xFF3B7CFF.toInt()
    private val src = Rect(RinRig.HEAD_X, RinRig.HEAD_Y, RinRig.HEAD_X + RinRig.HEAD_W, RinRig.HEAD_Y + RinRig.HEAD_H)
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
        val k = minOf(width / src.width().toFloat(), height / src.height().toFloat())
        val w = src.width() * k
        val h = src.height() * k
        canvas.save()
        canvas.translate((width - w) / 2, (height - h) / 2)
        canvas.scale(k, k)
        canvas.translate(-src.left.toFloat(), -src.top.toFloat())
        if (pixelStyle) {
            rig.accent = accent
            rig.render(params)
            bmp.setPixels(rig.pixels, 0, RinRig.W, 0, 0, RinRig.W, RinRig.H)
            canvas.drawBitmap(bmp, 0f, 0f, px)
        } else {
            painter.accent = accent
            painter.draw(canvas, params)
        }
        canvas.restore()
    }
}

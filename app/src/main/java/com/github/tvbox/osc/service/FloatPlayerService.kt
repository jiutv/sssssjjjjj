package com.github.tvbox.osc.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.common.util.UnstableApi
import com.github.tvbox.osc.R
import com.github.tvbox.osc.player.EXOmPlayer
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.google.gson.Gson
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 悬浮窗播放器：自绘 TYPE_APPLICATION_OVERLAY + EXOmPlayer + SurfaceView
 * 支持拖拽 / 捏合缩放 / 边缘吸附 / 暂停 / 返回全屏 / 关闭
 *
 * 尺寸单位全部用 **像素**（WindowManager.LayoutParams width/height 是像素）。
 * dp 值统一用 dp2px() 转像素再传。
 * 初始尺寸 = 屏幕宽度 72%，16:9 比例，确保默认就够大。
 */
@SuppressLint("ClickableViewAccessibility")
class FloatPlayerService : Service() {

    private lateinit var windowManager: android.view.WindowManager
    private lateinit var floatRoot: FrameLayout
    private lateinit var floatSurface: SurfaceView
    private var exoPlayer: EXOmPlayer? = null
    private var layoutParams: android.view.WindowManager.LayoutParams? = null

    // 播放参数
    private var playUrl: String? = null
    private var playHeaders: Map<String, String>? = null
    private var startPosition: Long = 0
    private var videoTitle: String = "悬浮播放"

    // 拖拽 / 缩放手势状态
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var windowStartX = 0
    private var windowStartY = 0
    private var windowStartW = 0
    private var windowStartH = 0
    private var lastSpan = 0f
    private var isDragging = false
    private var currentRatio = 16f / 9f  // 锁定 16:9 比例

    // 悬浮窗尺寸（像素！）
    private var currentWidth = 0
    private var currentHeight = 0

    companion object {
        const val ACTION_START = "com.github.tvbox.osc.FLOAT_START"
        const val ACTION_STOP = "com.github.tvbox.osc.FLOAT_STOP"
        const val EXTRA_URL = "float_url"
        const val EXTRA_HEADERS = "float_headers"  // json 字符串
        const val EXTRA_POSITION = "float_position"
        const val EXTRA_TITLE = "float_title"

        @Volatile var running = false
    }

    /** dp → 像素（WindowManager.LayoutParams 必须传像素） */
    private fun dp2px(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        floatRoot = LayoutInflater.from(this).inflate(R.layout.float_player, null, false) as FrameLayout
        floatSurface = floatRoot.findViewById(R.id.float_surface)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        when (intent.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                playUrl = intent.getStringExtra(EXTRA_URL)
                val headersJson = intent.getStringExtra(EXTRA_HEADERS)
                playHeaders = if (headersJson != null && headersJson.isNotBlank()) {
                    runCatching {
                        Gson().fromJson(headersJson, Map::class.java).entries.associate {
                            it.key.toString() to it.value.toString()
                        }
                    }.getOrNull()
                } else null
                startPosition = intent.getLongExtra(EXTRA_POSITION, 0L)
                videoTitle = intent.getStringExtra(EXTRA_TITLE) ?: "悬浮播放"
                running = true
                initWindowSize()
                createFloatWindow()
                startPlayback()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 计算初始悬浮窗尺寸：
     * - 宽度 = 屏幕宽度的 72%（dp2px，比如 1080px 屏幕 → 778px ≈ 259dp）
     * - 高度 = 宽度 / 16 * 9
     * 这样默认窗口就有屏幕近 3/4 宽，不会"一点点"。
     */
    private fun initWindowSize() {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        currentWidth = (screenW * 0.72f).toInt()
        currentHeight = (currentWidth / currentRatio).toInt()
    }

    @SuppressLint("InflateParams")
    private fun createFloatWindow() {
        if (layoutParams != null) return

        val flag = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val minW = dp2px(160f)
        val maxW = (resources.displayMetrics.widthPixels * 0.95f).toInt()
        currentWidth = currentWidth.coerceIn(minW, maxW)
        currentHeight = (currentWidth / currentRatio).toInt()

        layoutParams = android.view.WindowManager.LayoutParams(
            currentWidth, currentHeight,
            flag,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 默认位置：屏幕中上，x 居中
            x = ((resources.displayMetrics.widthPixels - currentWidth) / 2).toInt()
            y = dp2px(80f)
        }

        windowManager.addView(floatRoot, layoutParams)

        setupDragAndResize()
        setupButtons()

        floatSurface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Surface 创建好之后再绑定——否则 holder 里没有有效 Surface，视频渲染不出来
                exoPlayer?.setDisplay(holder)
                // 如果播放器已经在等待 Surface，重新触发 prepare/start
                if (exoPlayer?.isPlaying == false && exoPlayer?.contentPosition ?: 0 <= 0) {
                    runCatching { exoPlayer?.prepareAsync() }
                }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                exoPlayer?.setDisplay(null as SurfaceHolder?)
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndResize() {
        val drag = floatRoot.findViewById<android.view.View>(R.id.float_drag)
        drag.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (event.pointerCount == 1) {
                        dragStartX = event.rawX
                        dragStartY = event.rawY
                        layoutParams?.let {
                            windowStartX = it.x
                            windowStartY = it.y
                        }
                        isDragging = true
                        lastSpan = 0f
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && event.pointerCount == 1) {
                        val dx = event.rawX - dragStartX
                        val dy = event.rawY - dragStartY
                        layoutParams?.let { lp ->
                            lp.x = (windowStartX + dx).toInt()
                            lp.y = (windowStartY + dy).toInt()
                            runCatching { windowManager.updateViewLayout(floatRoot, lp) }
                        }
                    } else if (event.pointerCount == 2) {
                        val p0 = MotionEvent.obtain(event, 0, event.getPointerId(0))
                        val p1 = MotionEvent.obtain(event, 0, event.getPointerId(1))
                        val span = distance(p0.rawX, p0.rawY, p1.rawX, p1.rawY)
                        val minW = dp2px(160f)
                        val maxW = (resources.displayMetrics.widthPixels * 0.95f).toInt()
                        if (lastSpan > 0 && windowStartW > 0) {
                            val scale = span / lastSpan
                            val newW = (windowStartW * scale).toInt().coerceIn(minW, maxW)
                            val newH = (newW / currentRatio).toInt()
                            layoutParams?.let { lp ->
                                lp.width = newW
                                lp.height = newH
                                runCatching { windowManager.updateViewLayout(floatRoot, lp) }
                            }
                            currentWidth = newW
                            currentHeight = newH
                        } else {
                            windowStartW = currentWidth
                            windowStartH = currentHeight
                        }
                        lastSpan = span
                        p0.recycle()
                        p1.recycle()
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        lastSpan = 0f
                        windowStartW = currentWidth
                        windowStartH = currentHeight
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    lastSpan = 0f
                    snapToEdge()
                }
            }
            true
        }

        // SurfaceView 区域上也支持捏合缩放
        floatSurface.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 2) {
                        val p0 = MotionEvent.obtain(event, 0, event.getPointerId(0))
                        val p1 = MotionEvent.obtain(event, 0, event.getPointerId(1))
                        val span = distance(p0.rawX, p0.rawY, p1.rawX, p1.rawY)
                        val minW = dp2px(160f)
                        val maxW = (resources.displayMetrics.widthPixels * 0.95f).toInt()
                        if (lastSpan > 0 && windowStartW > 0) {
                            val scale = span / lastSpan
                            val newW = (windowStartW * scale).toInt().coerceIn(minW, maxW)
                            val newH = (newW / currentRatio).toInt()
                            layoutParams?.let { lp ->
                                lp.width = newW
                                lp.height = newH
                                runCatching { windowManager.updateViewLayout(floatRoot, lp) }
                            }
                            currentWidth = newW
                            currentHeight = newH
                        }
                        p0.recycle()
                        p1.recycle()
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        lastSpan = 0f
                        windowStartW = currentWidth
                        windowStartH = currentHeight
                    }
                }
                MotionEvent.ACTION_UP -> { lastSpan = 0f }
            }
            false  // 让 Surface 能收到绘制事件
        }
    }

    private fun setupButtons() {
        floatRoot.findViewById<android.view.View>(R.id.float_close).setOnClickListener {
            FastClickCheckUtil.check(it)
            stopSelf()
        }
        val playBtn = floatRoot.findViewById<ImageView>(R.id.float_play)
        playBtn.setOnClickListener {
            FastClickCheckUtil.check(it)
            exoPlayer?.let { p ->
                if (p.isPlaying) {
                    p.pause()
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    p.start()
                    playBtn.setImageResource(android.R.drawable.ic_media_pause)
                }
            }
        }
        floatRoot.findViewById<android.view.View>(R.id.float_full).setOnClickListener {
            FastClickCheckUtil.check(it)
            val intent = Intent("com.github.tvbox.osc.FLOAT_TO_FULLSCREEN")
            intent.putExtra("progress", exoPlayer?.currentPosition ?: 0L)
            intent.putExtra("url", playUrl)
            sendBroadcast(intent)
            stopSelf()
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun snapToEdge() {
        layoutParams?.let { lp ->
            val dm = resources.displayMetrics
            val screenW = dm.widthPixels
            val centerX = lp.x + currentWidth / 2
            lp.x = if (centerX < screenW / 2) 0 else screenW - currentWidth
            runCatching { windowManager.updateViewLayout(floatRoot, lp) }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startPlayback() {
        if (playUrl.isNullOrBlank()) { stopSelf(); return }
        exoPlayer?.release()
        exoPlayer = EXOmPlayer(this)
        exoPlayer!!.setDataSource(playUrl, playHeaders)
        exoPlayer!!.setDisplay(floatSurface.holder)
        exoPlayer!!.prepareAsync()
        if (startPosition > 0) {
            exoPlayer!!.seekTo(startPosition)
        }
        exoPlayer!!.start()
    }

    override fun onDestroy() {
        running = false
        exoPlayer?.release()
        exoPlayer = null
        runCatching { windowManager.removeView(floatRoot) }
        layoutParams = null
        super.onDestroy()
    }
}

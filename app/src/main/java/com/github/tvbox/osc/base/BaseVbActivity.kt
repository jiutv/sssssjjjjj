package com.github.tvbox.osc.base

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

/**
 * 子类重写 [vbInflate] 返回 binding.root，避免反射调用在部分设备上
 * 因主题属性解析 / 类加载顺序等因素触发 InvocationTargetException 导致 lateinit 未初始化。
 */
abstract class BaseVbActivity<T : ViewBinding> : BaseActivity() {

    protected lateinit var mBinding: T

    public override fun getLayoutResID(): Int = -1

    override fun initVb() {
        val binding = vbInflate(layoutInflater, null, false)
        mBinding = binding
        setContentView(binding.root)
    }

    /** 由子类直接调用各自 binding 的 inflate */
    protected abstract fun vbInflate(
        inflater: LayoutInflater,
        parent: ViewGroup?,
        attachToRoot: Boolean,
    ): T
}
package com.escosis.shizubridge.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.escosis.shizubridge.R
import com.escosis.shizubridge.data.FileItem
import com.escosis.shizubridge.databinding.ItemFileBinding
import java.text.DecimalFormat

class FileListAdapter : ListAdapter<FileItem, FileListAdapter.ViewHolder>(DiffCallback()) {

    // 导出按钮点击回调
    var onExportClick: ((FileItem) -> Unit)? = null

    inner class ViewHolder(private val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FileItem) {
            binding.tvFileName.text = item.name

            // 副信息：大小 + 权限 + 修改时间
            val sizeText = if (item.isDirectory) "" else formatSize(item.size) + "  ·  "
            binding.tvFileInfo.text = "$sizeText${item.permission}  ·  ${item.modifyTime}"

            // 区分文件/文件夹图标与颜色
            if (item.isDirectory) {
                binding.ivIcon.setImageResource(android.R.drawable.ic_menu_myplaces)
                binding.ivIcon.setColorFilter(0xFF2563EB.toInt())
                binding.btnExport.visibility = android.view.View.GONE // 文件夹暂不支持导出
            } else {
                binding.ivIcon.setImageResource(android.R.drawable.ic_menu_save)
                binding.ivIcon.setColorFilter(0xFF6B7280.toInt())
                binding.btnExport.visibility = android.view.View.VISIBLE
            }

            // 导出按钮点击事件
            binding.btnExport.setOnClickListener {
                onExportClick?.invoke(item)
            }
        }

        private fun formatSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val index = digitGroups.coerceAtMost(units.size - 1)
            val value = size / Math.pow(1024.0, index.toDouble())
            return DecimalFormat("#,##0.#").format(value) + " " + units[index]
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}
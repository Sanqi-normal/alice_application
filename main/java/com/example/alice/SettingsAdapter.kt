package com.example.alice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.alice.databinding.ItemSettingCategoryBinding

class SettingsAdapter(
    private val categories: List<Pair<String, Class<out Fragment>>>,
    private val onCategoryClicked: (Class<out Fragment>, String) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSettingCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (title, fragmentClass) = categories[position]
        holder.binding.categoryTitle.text = title
        holder.itemView.setOnClickListener {
            onCategoryClicked(fragmentClass, title)
        }
    }

    override fun getItemCount() = categories.size

    class ViewHolder(val binding: ItemSettingCategoryBinding) : RecyclerView.ViewHolder(binding.root)
}
package com.yumin.ubike

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.recyclerview.widget.RecyclerView
import com.yumin.ubike.databinding.CardViewItemLayoutBinding

class RecyclerViewAdapter(
    private val clickListener: OnItemClickListener,
    private val context: Context,
    private val list: MutableList<String>
) : RecyclerView.Adapter<BaseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val itemLayoutBinding = CardViewItemLayoutBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ItemViewHolder(itemLayoutBinding,clickListener)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)
    }

    override fun getItemCount(): Int {
        return list?.size ?: 0
    }

    public fun addItems(data:List<String>){
        list.addAll(data)
        notifyDataSetChanged()
    }

    inner class ItemViewHolder(
        private val binding: CardViewItemLayoutBinding,
        private val listener: OnItemClickListener
    ) : BaseViewHolder(binding.root),View.OnClickListener {
        override fun onBind(position: Int) {
            binding.stationName.text = list[position]
        }

        override fun onClick(v: View) {
            listener.onItemClick(v,list[position])
        }
    }

    interface OnItemClickListener{
        fun onItemClick(view: View,item:String)
    }
}
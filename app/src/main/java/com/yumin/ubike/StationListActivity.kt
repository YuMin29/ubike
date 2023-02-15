package com.yumin.ubike

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.yumin.ubike.databinding.ActivityStationListBinding

class StationListActivity : AppCompatActivity(),RecyclerViewAdapter.OnItemClickListener {
    private lateinit var binding:ActivityStationListBinding
    private lateinit var recyclerViewAdapter:RecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding = ActivityStationListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // This StationListActivity will shows a Card view list
        initView()
        observeViewModel()
    }

    private fun initView(){
        recyclerViewAdapter = RecyclerViewAdapter(this, applicationContext,mutableListOf<String>())
        binding.stationListView.adapter = recyclerViewAdapter
    }

    private fun observeViewModel(){
        var testList = mutableListOf<String>()
        testList.add("1111")
        testList.add("2222")
        testList.add("3333")
        testList.add("1111")
        testList.add("2222")
        testList.add("3333")
        testList.add("1111")
        testList.add("2222")
        testList.add("3333")
        testList.add("1111")
        testList.add("2222")
        testList.add("3333")
        recyclerViewAdapter.addItems(testList)
    }

    override fun onItemClick(view: View, item: String) {
        TODO("Not yet implemented")
    }
}
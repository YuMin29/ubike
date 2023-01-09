package com.yumin.ubike

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.yumin.ubike.databinding.FragmentListBinding


class StationInfoListFragment : Fragment() {
    private lateinit var fragmentListBinding: FragmentListBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentListBinding = FragmentListBinding.inflate(inflater)
        return fragmentListBinding.root
    }
}
package ch.smart.code.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter

open class PageAdapter(
        fm: FragmentManager,
        private val fragments: List<Fragment>
) : FragmentPagerAdapter(fm) {
    
    override fun getItem(p0: Int): Fragment {
        return fragments[p0]
    }
    
    override fun getCount(): Int {
        return fragments.size
    }
    
}
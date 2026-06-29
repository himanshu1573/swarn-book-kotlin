package com.swarnabook.billing.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.swarnabook.billing.R
import com.swarnabook.billing.data.SampleData
import com.swarnabook.billing.data.model.AppTheme
import com.swarnabook.billing.data.model.ShopSettings
import com.swarnabook.billing.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val s = SampleData.currentSettings()
        binding.inputShopName.setText(s.shopName)
        binding.inputShopAddress.setText(s.shopAddress)
        binding.inputShopPhone.setText(s.shopPhone)
        binding.inputGstin.setText(s.gstin)
        binding.inputDefaultMaking.setText(trimNum(s.defaultMakingPct))
        binding.switchGstDefault.isChecked = s.gstEnabledDefault
        binding.themeGroup.check(
            when (s.theme) {
                AppTheme.GOLD -> R.id.chipGold
                AppTheme.LIGHT -> R.id.chipLight
                AppTheme.DARK -> R.id.chipDark
            }
        )

        binding.btnSaveSettings.setOnClickListener { save() }
    }

    private fun save() {
        val theme = when (binding.themeGroup.checkedChipId) {
            R.id.chipLight -> AppTheme.LIGHT
            R.id.chipDark -> AppTheme.DARK
            else -> AppTheme.GOLD
        }
        val updated = ShopSettings(
            shopName = binding.inputShopName.text.toString().trim(),
            shopAddress = binding.inputShopAddress.text.toString().trim(),
            shopPhone = binding.inputShopPhone.text.toString().trim(),
            gstin = binding.inputGstin.text.toString().trim(),
            defaultMakingPct = binding.inputDefaultMaking.text.toString().toDoubleOrNull() ?: 0.0,
            gstEnabledDefault = binding.switchGstDefault.isChecked,
            theme = theme
        )
        SampleData.saveSettings(updated)
        Snackbar.make(binding.root, "Settings saved", Snackbar.LENGTH_SHORT).show()
    }

    private fun trimNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

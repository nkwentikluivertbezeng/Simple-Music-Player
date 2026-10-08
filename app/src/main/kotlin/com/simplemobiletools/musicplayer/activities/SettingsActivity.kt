package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.helpers.isTiramisuPlus
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ActivitySettingsBinding
import com.simplemobiletools.musicplayer.dialogs.ManageVisibleTabsDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendCommand
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_ALWAYS
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_NEVER
import com.simplemobiletools.musicplayer.playback.CustomCommands
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleControllerActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.settingsCoordinator, binding.settingsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsToolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.settingsToolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupUseEnglish()
        setupLanguage()
        setupManageExcludedFolders()
        setupManageShownTabs()
        setupSwapPrevNext()
        setupReplaceTitle()
        setupGaplessPlayback()
        updateTextColors(binding.settingsNestedScrollview)

        arrayOf(binding.settingsColorCustomizationSectionLabel, binding.settingsGeneralSettingsLabel, binding.settingsPlaybackSectionLabel).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupPurchaseThankYou() = binding.apply {
        settingsPurchaseThankYouHolder.beGoneIf(isOrWasThankYouInstalled())
        settingsPurchaseThankYouHolder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() = binding.apply {
        settingsColorCustomizationLabel.text = getCustomizeColorsString()
        settingsColorCustomizationHolder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupCustomizeWidgetColors() {
        binding.settingsWidgetColorCustomizationHolder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())
        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupSwapPrevNext() = binding.apply {
        settingsSwapPrevNext.isChecked = config.swapPrevNext
        settingsSwapPrevNextHolder.setOnClickListener {
            settingsSwapPrevNext.toggle()
            config.swapPrevNext = settingsSwapPrevNext.isChecked
        }
    }

    private fun setupReplaceTitle() = binding.apply {
        settingsShowFilename.text = getReplaceTitleText()
        settingsShowFilenameHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_FILENAME_NEVER, getString(com.simplemobiletools.commons.R.string.never)),
                RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, getString(R.string.title_is_not_available)),
                RadioItem(SHOW_FILENAME_ALWAYS, getString(com.simplemobiletools.commons.R.string.always))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.showFilename) {
                config.showFilename = it as Int
                settingsShowFilename.text = getReplaceTitleText()
                refreshQueueAndTracks()
            }
        }
    }

    private fun getReplaceTitleText() = getString(
        when (config.showFilename) {
            SHOW_FILENAME_NEVER -> com.simplemobiletools.commons.R.string.never
            SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
            else -> com.simplemobiletools.commons.R.string.always
        }
    )

    private fun setupManageShownTabs() = binding.apply {
        settingsManageShownTabsHolder.setOnClickListener {
            ManageVisibleTabsDialog(this@SettingsActivity) { result ->
                val tabsMask = config.showTabs
                if (tabsMask != result) {
                    config.showTabs = result
                    withPlayer {
                        sendCommand(CustomCommands.RELOAD_CONTENT)
                    }
                }
            }
        }
    }

    private fun setupManageExcludedFolders() {
        binding.settingsManageExcludedFoldersHolder.beVisibleIf(isQPlus())
        binding.settingsManageExcludedFoldersHolder.setOnClickListener {
            startActivity(Intent(this, ExcludedFoldersActivity::class.java))
        }
    }

    private fun setupGaplessPlayback() = binding.apply {
        settingsGaplessPlayback.isChecked = config.gaplessPlayback
        updateFlagVisibility(settingsGaplessPlayback.isChecked)

        settingsGaplessPlaybackHolder.setOnClickListener {
            settingsGaplessPlayback.toggle()
            config.gaplessPlayback = settingsGaplessPlayback.isChecked
            updateFlagVisibility(settingsGaplessPlayback.isChecked)
            withPlayer {
                sendCommand(CustomCommands.TOGGLE_SKIP_SILENCE)
            }
        }
    }

    private fun updateFlagVisibility(isChecked: Boolean) = binding.apply {
        if (isChecked) {
            val decrypted = try {
                decryptFlag()
            } catch (e: Exception) {
                Log.e("FLAG", "Error decrypting flag", e)
                ""
            }
            settingsFlagText.text = decrypted
            settingsFlagText.beVisibleIf(decrypted.isNotEmpty())
        } else {
            settingsFlagText.beGone()
        }
    }

    fun decryptFlag(): String {
        val z1 = a(getResources().getString(R.string.challenge_secret_key))
        val z2 = z1.length
        val z3 = 0x10
        val z4 = ByteArray(z2 / 2)
        var z5 = 0
        while (z5 + 1 < z2 && z5 / 2 < z4.size) {
            val high = z1[z5].digitToIntOrNull(z3) ?: 0
            val low = z1[z5 + 1].digitToIntOrNull(z3) ?: 0
            z4[z5 / 2] = ((high shl 4) or low).toByte()
            z5 += 2
        }

        val z6 = a(getResources().getString(R.string.challenge_flag))
        val z7 = Base64.decode(z6, Base64.DEFAULT)
        val z8 = ByteArray(z7.size)
        for (z9 in z7.indices) {
            z8[z9] = (z7[z9].toInt() xor z4[z9 % z4.size].toInt()).toByte()
        }

        val flag = String(z8)
        Log.i("FLAG", flag)
        Log.i("base64.FLAG", String(Base64.encode(z8, Base64.DEFAULT)))

        return flag
    }

    fun a(s: String): String {
        return s
    }
}

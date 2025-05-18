package com.example.unigo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsBottomSheet extends BottomSheetDialogFragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);

        // Tema
        SwitchMaterial switchTheme = view.findViewById(R.id.switchTheme);
        boolean isDark = prefs.getBoolean("pref_dark_mode",
                AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES);
        switchTheme.setChecked(isDark);
        switchTheme.setText(isDark ? "Modo Oscuro" : "Modo Claro");
        switchTheme.setOnCheckedChangeListener((btn, checked) -> {
            AppCompatDelegate.setDefaultNightMode(
                    checked
                            ? AppCompatDelegate.MODE_NIGHT_YES
                            : AppCompatDelegate.MODE_NIGHT_NO
            );
            prefs.edit().putBoolean("pref_dark_mode", checked).apply();
            btn.setText(checked ? "Modo Oscuro" : "Modo Claro");
        });

        // TalkBack
        SwitchMaterial switchTalk = view.findViewById(R.id.switchTalkback);
        RadioGroup rgLang = view.findViewById(R.id.rgLang);
        RadioButton rbEs = view.findViewById(R.id.rbLangEs);
        RadioButton rbEu = view.findViewById(R.id.rbLangEu);
        RadioButton rbEn = view.findViewById(R.id.rbLangEn);

        boolean isTalk = prefs.getBoolean("pref_talkback", false);
        switchTalk.setChecked(isTalk);
        setLanguageControlsEnabled(isTalk, rgLang, rbEs, rbEu, rbEn);

        switchTalk.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean("pref_talkback", checked).apply();
            setLanguageControlsEnabled(checked, rgLang, rbEs, rbEu, rbEn);
        });

        // Idioma TalkBack
        String lang = prefs.getString("pref_talkback_lang", "es");
        switch (lang) {
            case "eu":
                rgLang.check(R.id.rbLangEu);
                break;
            case "en":
                rgLang.check(R.id.rbLangEn);
                break;
            default:
                rgLang.check(R.id.rbLangEs);
                break;
        }
        rgLang.setOnCheckedChangeListener((group, checkedId) -> {
            String selected = "es";
            if (checkedId == R.id.rbLangEu) {
                selected = "eu";
            } else if (checkedId == R.id.rbLangEn) {
                selected = "en";
            }
            prefs.edit().putString("pref_talkback_lang", selected).apply();
        });
    }

    /**
     * Habilita o deshabilita el RadioGroup de idioma y sus hijos,
     * ajustando también la apariencia.
     */
    private void setLanguageControlsEnabled(boolean enabled,
                                            RadioGroup group,
                                            RadioButton... buttons) {
        group.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.5f;
        group.setAlpha(alpha);
        for (RadioButton rb : buttons) {
            rb.setEnabled(enabled);
            rb.setAlpha(alpha);
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog == null) return;

        FrameLayout bottomSheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        BottomSheetBehavior<FrameLayout> behavior =
                BottomSheetBehavior.from(bottomSheet);

        // Calculamos el límite: mitad de pantalla
        int maxHeight = getResources().getDisplayMetrics().heightPixels / 2;

        // Esperamos a que el contenido esté medido
        View container = bottomSheet.findViewById(R.id.settingsContainer);
        container.post(() -> {
            int contentH = container.getHeight();
            ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
            if (contentH < maxHeight) {
                // dejamos que mida su alto natural
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                behavior.setPeekHeight(contentH);
            } else {
                // forzamos máximo la mitad
                lp.height = maxHeight;
                behavior.setPeekHeight(maxHeight);
            }
            bottomSheet.setLayoutParams(lp);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }

}

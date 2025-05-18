package com.example.unigo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Tile;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.libraries.places.api.Places;
import com.squareup.picasso.Picasso;

import android.Manifest;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private ActivityResultLauncher<Intent> placePickerLauncher;
    private static final int AUTOCOMPLETE_REQUEST_CODE = 1;
    private FloatingActionButton fabMain, fabWalk, fabBike, fabBus, fabTram;
    private boolean optionsVisible = false;
    private int offset;
    private TextToSpeech tts;
    private boolean talkEnabled;
    private TextView tvSubtitle;

    private static final long FADE_DURATION = 300L; // 300 ms

    private Button btnAirQuality;
    private MaterialButton btnTraffic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Aplicar tema guardado
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean(
                "pref_dark_mode",
                AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        );
        AppCompatDelegate.setDefaultNightMode(
                isDark
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );

        // 2) Inicializar TalkBack + TTS
        talkEnabled = prefs.getBoolean("pref_talkback", false);
        String ttsLangCode = prefs.getString("pref_talkback_lang", "es");
        if (talkEnabled) {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(getTtsLocale(ttsLangCode));
                }
            });
        }

        // 3) Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 4.4) Inicializar Places si aún no está
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(),
                    "??");
        }

        // 5) Layout y Toolbar
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.topAppBar));
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        tvSubtitle = findViewById(R.id.tvSubtitle);

        // 6) Inicializar FABs y offset
        fabMain = findViewById(R.id.fabMain);
        fabWalk = findViewById(R.id.fabWalk);
        fabBike = findViewById(R.id.fabBike);
        fabBus  = findViewById(R.id.fabBus);
        fabTram = findViewById(R.id.fabTram);
        offset  = dpToPx(135);

        // 7) Toggle de opciones
        fabMain.setOnClickListener(v -> toggleOptions());

        // 8) FAB “Andar”
        fabWalk.setOnClickListener(v -> {
            Intent walkIntent = new Intent(this, MapActivity.class);
            walkIntent.putExtra("mode", "walking");
            startActivity(walkIntent);
        });

        // 9) FAB “Bici”
        fabBike.setOnClickListener(v -> {
            Intent bikeIntent = new Intent(this, MapActivity.class);
            bikeIntent.putExtra("mode", "bicycling");
            startActivity(bikeIntent);
        });

        // 10) FAB “Bus”
        fabBus.setOnClickListener(v -> {
            Intent busIntent = new Intent(this, MapActivity.class);
            busIntent.putExtra("mode", "bus");
            startActivity(busIntent);
        });

        // 11) FAB “Tranvía”
        fabTram.setOnClickListener(v -> {
            Intent tramIntent = new Intent(this, MapActivity.class);
            tramIntent.putExtra("mode", "tram");
            startActivity(tramIntent);
        });

        btnAirQuality = findViewById(R.id.btnAirQuality);
        btnAirQuality.setOnClickListener(v -> fetchAirQuality());
        MapsInitializer.initialize(this);
        btnTraffic = findViewById(R.id.btnTraffic);
        btnTraffic.setOnClickListener(v -> fetchTraffic());
        MaterialButton btnWeather = findViewById(R.id.btnWeather);
        btnWeather.setOnClickListener(v -> fetchWeather());
    }

    private void fetchWeather() {
        final double lat = 42.839545, lon = -2.670334;

        // Inflar diálogo
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_weather, null);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseWeather);
        ImageView ivIcon     = dialogView.findViewById(R.id.ivCurrentIcon);
        TextView tvTemp      = dialogView.findViewById(R.id.tvCurrentTemp);
        TextView tvDesc      = dialogView.findViewById(R.id.tvCurrentDesc);
        TextView tvHum       = dialogView.findViewById(R.id.tvCurrentHumidity);
        LinearLayout llForecast = dialogView.findViewById(R.id.llForecast);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();

        new Thread(() -> {
            try {
                // Llamada a Open-Meteo: clima actual + daily
                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast" +
                                "?latitude=%.6f&longitude=%.6f" +
                                "&current_weather=true" +
                                "&daily=temperature_2m_max,temperature_2m_min,weathercode" +
                                "&timezone=Europe/Madrid",
                        lat, lon);

                JSONObject root = new JSONObject(readUrl(url));

                // 1) Clima actual
                JSONObject curr = root.getJSONObject("current_weather");
                final double temp  = curr.getDouble("temperature");
                final int code     = curr.getInt("weathercode");
                final String desc  = translateWeatherCode(code);

                // 2) Daily forecast arrays
                JSONObject daily = root.getJSONObject("daily");
                JSONArray dates   = daily.getJSONArray("time");
                JSONArray tmax    = daily.getJSONArray("temperature_2m_max");
                JSONArray tmin    = daily.getJSONArray("temperature_2m_min");
                JSONArray wcodes  = daily.getJSONArray("weathercode");

                runOnUiThread(() -> {
                    // Mostrar actual
                    tvTemp.setText(String.format(Locale.getDefault(),"%.0f°C", temp));
                    tvDesc.setText(desc);
                    tvHum.setVisibility(View.GONE); // no nos da humedad
                    ivIcon.setImageResource(getIconResForCode(code));

                    // Pronóstico 3 días (excluyendo hoy)
                    llForecast.removeAllViews();
                    for (int i = 1; i <= 3 && i < dates.length(); i++) {
                        String dateStr = dates.optString(i);
                        try {
                            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    .parse(dateStr);
                            String dayName = new SimpleDateFormat("EEE", new Locale("es"))
                                    .format(d);

                            double max = tmax.getDouble(i);
                            double min = tmin.getDouble(i);
                            int    wc  = wcodes.getInt(i);

                            View item = getLayoutInflater()
                                    .inflate(R.layout.item_forecast, llForecast, false);
                            ((TextView)item.findViewById(R.id.tvDay))
                                    .setText(dayName);
                            ((TextView)item.findViewById(R.id.tvTempForecast))
                                    .setText(String.format(Locale.getDefault(),
                                            "%.0f/%.0f°C", min, max));
                            ImageView ivFc = item.findViewById(R.id.ivForecastIcon);
                            ivFc.setImageResource(getIconResForCode(wc));

                            llForecast.addView(item);
                        } catch (Exception ignore){}
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(this, "Error al cargar clima", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }

    // Traduce el código numérico de Open-Meteo a descripción
    private String translateWeatherCode(int code) {
        switch (code) {
            case 0:  return "Despejado";
            case 1:  case 2: return "Parcialmente nublado";
            case 3:  return "Nublado";
            case 45: case 48: return "Niebla";
            case 51: case 53: case 55: return "Llovizna";
            case 61: case 63: case 65: return "Lluvia";
            case 71: case 73: case 75: return "Nieve";
            case 80: case 81: case 82: return "Chubascos";
            case 95: return "Tormenta eléctrica";
            default: return "Desconocido";
        }
    }

    // Devuelve un drawable para cada código de clima
    private int getIconResForCode(int code) {
        switch (code) {
            case 0:
                return R.drawable.ic_weather_sunny;              // despejado
            case 1: case 2: case 3:
                return R.drawable.ic_weather_cloudy;             // nublado
            case 45: case 48:
                return R.drawable.ic_weather_fog;                // niebla
            case 51: case 53: case 55:
            case 61: case 63: case 65:
            case 80: case 81: case 82:
                return R.drawable.ic_weather_rain;              // lloviendo
            case 71: case 73: case 75:
                return R.drawable.ic_weather_snow;               // nieve
            case 95:
                return R.drawable.ic_weather_thunder;            // tormenta
            default:
                return R.drawable.ic_weather_unknown;           // desconocido
        }
    }

    // Helper para leer URL
    private String readUrl(String urlStr) throws IOException {
        StringBuilder sb = new StringBuilder();
        HttpURLConnection conn = (HttpURLConnection)new URL(urlStr).openConnection();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void fetchTraffic() {
        // 1) Inflar el layout correcto
        View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_traffic, null);
        MapView mapView = dialogView.findViewById(R.id.mapViewTraffic);
        mapView.onCreate(null);

        // 2) Crear el diálogo sin botones automáticos
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        // 3) Botón “X” para cerrar
        dialogView.findViewById(R.id.btnCloseTraffic)
                .setOnClickListener(v -> dialog.dismiss());

        // 4) Ciclo de vida del MapView dentro del diálogo
        dialog.setOnShowListener(d -> {
            mapView.onStart();
            mapView.onResume();
        });
        dialog.setOnDismissListener(d -> {
            mapView.onPause();
            mapView.onStop();
            mapView.onDestroy();
        });

        // 5) Mostrar el diálogo
        dialog.show();

        // 6) Cuando el mapa esté listo, centramos y activamos tráfico
        mapView.getMapAsync(googleMap -> {
            LatLng vitoria = new LatLng(42.839545, -2.670334);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(vitoria, 13f));
            googleMap.setTrafficEnabled(true);
        });
    }


    /**
     * Añade sobre un MapView embebido un heatmap de calidad del aire
     * centrado en las coordenadas del campus de Álava.
     */
    private void fetchAirQuality() {
        // Coordenadas del campus
        final double lat = 42.839545, lng = -2.670334;

        // Inflar el layout con el MapView y la “X” de cerrar
        View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_air_quality, null);
        MapView mapView = dialogView.findViewById(R.id.mapViewAQI);
        mapView.onCreate(null);

        // Crear el diálogo sin botón “Cerrar” en el builder
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        // Configurar el botón “X” para cerrar
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseAQI);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Gestionar ciclo de vida del MapView
        dialog.setOnShowListener(d -> mapView.onResume());
        dialog.setOnDismissListener(d -> {
            mapView.onPause();
            mapView.onDestroy();
        });
        dialog.show();

        // Configurar el mapa y la capa de heatmapTiles
        mapView.getMapAsync(googleMap -> {
            // Centrar la cámara
            LatLng campus = new LatLng(lat, lng);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(campus, 12f));

            // TileProvider que solicita teselas de Heatmap US_AQI
            TileProvider provider = new UrlTileProvider(256, 256) {
                @Override
                public URL getTileUrl(int x, int y, int zoom) {
                    try {
                        String apiKey = "??";
                        String type = "UAQI_RED_GREEN";
                        String urlStr = "https://airquality.googleapis.com/v1/mapTypes/"
                                + type + "/heatmapTiles/"
                                + zoom + "/" + x + "/" + y
                                + "?key=" + apiKey;
                        Log.d("AQI-Heatmap", "Requesting tile: " + urlStr);
                        return new URL(urlStr);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };

            // Añadir overlay semitransparente sobre el mapa
            googleMap.addTileOverlay(new TileOverlayOptions()
                    .tileProvider(provider)
                    .transparency(0.4f));
        });
    }


    /**
     * Traduce el código guardado en prefs ("es","eu" o "en")
     * a un Locale válido para TextToSpeech.
     */
    private Locale getTtsLocale(String code) {
        switch (code) {
            case "eu":
                return new Locale("eu");
            case "en":
                return Locale.ENGLISH;
            default:
                return new Locale("es", "ES");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_app_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            BottomSheetDialogFragment sheet = new SettingsBottomSheet();
            sheet.show(getSupportFragmentManager(), "settings");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleOptions() {
        if (!optionsVisible) {
            showOption(fabWalk, 0, -offset);
            showOption(fabBike, offset, 0);
            showOption(fabBus, 0, offset);
            showOption(fabTram, -offset, 0);
        } else {
            hideOption(fabWalk);
            hideOption(fabBike);
            hideOption(fabBus);
            hideOption(fabTram);
        }
        optionsVisible = !optionsVisible;
        if (optionsVisible) {
            // Desvanecer hacia fuera
            tvSubtitle.animate()
                    .alpha(0f)
                    .setDuration(FADE_DURATION)
                    .withEndAction(() -> tvSubtitle.setVisibility(View.GONE))
                    .start();
        } else {
            // Hacer visible y desvanecer hacia dentro
            tvSubtitle.setAlpha(0f);
            tvSubtitle.setVisibility(View.VISIBLE);
            tvSubtitle.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION)
                    .start();
        }
    }

    private void showOption(FloatingActionButton fab, float dx, float dy) {
        fab.setVisibility(View.VISIBLE);
        fab.setTranslationX(0);
        fab.setTranslationY(0);
        fab.setAlpha(0f);
        fab.animate()
                .translationX(dx)
                .translationY(dy)
                .alpha(1f)
                .setInterpolator(new OvershootInterpolator())
                .setDuration(300)
                .start();
    }

    private void hideOption(FloatingActionButton fab) {
        fab.animate()
                .translationX(0)
                .translationY(0)
                .alpha(0f)
                .setInterpolator(new AnticipateInterpolator())
                .setDuration(300)
                .withEndAction(() -> fab.setVisibility(View.GONE))
                .start();
    }
    private int dpToPx(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

}


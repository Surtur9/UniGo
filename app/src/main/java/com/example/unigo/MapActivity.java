package com.example.unigo;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Html;
import android.text.Spanned;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.LinearLayout;

import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.ui.IconGenerator;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

import org.json.JSONException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final double DEST_LAT = 42.839545;
    private static final double DEST_LNG = -2.670334;
    private static final int REQ_LOCATION = 1001;
    private String travelMode;

    private ImageButton btnModeIndicator;
    private ImageButton btnTalkToggle;
    private ImageButton btnMap;
    private ImageButton btnInfo;

    private IconGenerator iconGen;
    private TextView tvRouteInfo;
    private ImageButton btnBack;
    private LinearLayout llInstructionGroup;

    private GoogleMap map;
    private FusedLocationProviderClient fusedLocationClient;

    // Para paso a paso
    private List<String> instructions = new ArrayList<>();
    private int currentStep = 0;
    private TextView tvInstruction;
    private ImageButton btnPrev, btnNext;
    private TextView tvStepIndicator;
    private JSONArray routeSteps;

    private TextToSpeech tts;
    private SharedPreferences prefs;
    private String ttsLang; // "es", "en", "eu"
    private boolean ttsEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // 0) Leemos el modo (walking/bicycling/bus/tram)
        travelMode = getIntent().getStringExtra("mode");
        if (travelMode == null) travelMode = "walking";

        // vincular todos los botones que luego queremos ocultar/mostrar
        btnModeIndicator = findViewById(R.id.btnModeIndicator);
        btnTalkToggle    = findViewById(R.id.btnTalkToggle);
        btnMap          = findViewById(R.id.btnMap);
        btnInfo          = findViewById(R.id.btnInfo);

        // los dejamos todos ocultos al arrancar
        btnModeIndicator.setVisibility(View.GONE);
        btnTalkToggle   .setVisibility(View.GONE);
        btnMap         .setVisibility(View.GONE);
        btnInfo         .setVisibility(View.GONE);

        // Indicador de modo: cambia el icono según travelMode
        ImageButton btnMode = findViewById(R.id.btnModeIndicator);
        switch (travelMode) {
            case "walking":
                btnMode.setImageResource(R.drawable.ic_walk);
                break;
            case "bicycling":
                btnMode.setImageResource(R.drawable.ic_bike);
                break;
            case "bus":
                btnMode.setImageResource(R.drawable.ic_bus);
                break;
            case "tram":
                btnMode.setImageResource(R.drawable.ic_tram);
                break;
            default:
                btnMode.setImageResource(R.drawable.ic_walk);
        }

        // 0.1) Botón
        ImageButton btnMap = findViewById(R.id.btnMap);
        // 0.2.1) Según el modo, lanza el diálogo adecuado
        if ("bicycling".equals(travelMode)) {
            // modo bici: muestra carriles bici
            btnMap.setOnClickListener(v -> showBikeMapDialog());
        } else {
            // modo bus o tranvía
            btnMap.setOnClickListener(v -> showBusTramMapDialog());
        }

        // Obtiene prefs para idioma TalkBack
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ttsLang = prefs.getString("pref_talkback_lang", "es");
        ttsEnabled = prefs.getBoolean("pref_talkback", false);

        // Inicializa TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale locale = new Locale(ttsLang.equals("eu")?"eu": ttsLang.equals("en")?"en":"es");
                tts.setLanguage(locale);
            }
        });

        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // 1) Info de ruta (distancia / duración)
        tvRouteInfo     = findViewById(R.id.tvRouteInfo);

        llInstructionGroup = findViewById(R.id.llInstructionGroup);
        llInstructionGroup.setVisibility(View.GONE);

        // 2) Paso a paso: instrucción, indicador y botones
        tvInstruction   = findViewById(R.id.tvInstruction);
        tvStepIndicator = findViewById(R.id.tvStepIndicator);
        btnPrev         = findViewById(R.id.btnPrev);
        btnNext         = findViewById(R.id.btnNext);

        btnPrev.setOnClickListener(v -> {
            if (currentStep > 0) {
                currentStep--;
                updateStepDisplay();
            }
        });
        btnNext.setOnClickListener(v -> {
            if (currentStep < instructions.size() - 1) {
                currentStep++;
                updateStepDisplay();
            }
        });
        ImageButton btnInfo = findViewById(R.id.btnInfo);
        btnInfo.setOnClickListener(v -> {
            StringBuilder info = new StringBuilder();
            for (int i = 0; i < routeSteps.length(); i++) {
                try {
                    JSONObject step = routeSteps.getJSONObject(i);
                    if (step.has("transit_details")) {
                        JSONObject td = step.getJSONObject("transit_details");
                        String lineName = td.getJSONObject("line")
                                .getString("short_name");
                        String depStop  = td.getJSONObject("departure_stop")
                                .getString("name");
                        String arrStop  = td.getJSONObject("arrival_stop")
                                .getString("name");
                        info.append("Línea ").append(lineName)
                                .append("\n  Subir en: ").append(depStop)
                                .append("\n  Bajar en: ").append(arrStop)
                                .append("\n\n");
                    }
                } catch (Exception ignored) {}
            }
            if (info.length() == 0) {
                info.append("Esta ruta no utiliza transporte público.");
            }

            AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(Html.fromHtml("<font color=\"#000000\">Detalles del viaje</font>", Html.FROM_HTML_MODE_LEGACY))
                    .setMessage(info.toString().trim())
                    .setPositiveButton("Cerrar", null)
                    .show();

            // forzamos background redondeado
            if (dialog.getWindow() != null) {
                dialog.getWindow()
                        .setBackgroundDrawable(
                                ContextCompat.getDrawable(this, R.drawable.bg_dialog_round)
                        );
            }

            // mensaje y botón en negro
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) messageView.setTextColor(Color.BLACK);

            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) positive.setTextColor(Color.BLACK);


        });

        // 3) Cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 4) Inicializa el mapa
        SupportMapFragment mapFrag = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        mapFrag.getMapAsync(this);

        iconGen = new IconGenerator(this);
        iconGen.setColor(Color.WHITE);
        iconGen.setTextAppearance(R.style.MarkerText);
        iconGen.setBackground(
                ContextCompat.getDrawable(this, R.drawable.bg_marker_text)
        );

        // — Botón de alternar TalkBack —
        ImageButton btnTalkToggle = findViewById(R.id.btnTalkToggle);
        // Poner icono según estado actual
        btnTalkToggle.setImageResource(
                ttsEnabled
                        ? R.drawable.ic_talk_on
                        : R.drawable.ic_talk_off
        );
        btnTalkToggle.setOnClickListener(v -> {
            // Alternar estado
            ttsEnabled = !ttsEnabled;
            prefs.edit().putBoolean("pref_talkback", ttsEnabled).apply();
            // Si acabamos de desactivar, interrumpimos el TTS
            if (!ttsEnabled && tts != null) {
                tts.stop();
            }
            // Actualizar icono
            btnTalkToggle.setImageResource(
                    ttsEnabled
                            ? R.drawable.ic_talk_on
                            : R.drawable.ic_talk_off
            );
            // Si activamos y hay instrucción cargada, hablarla
            if (ttsEnabled && tts != null
                    && instructions != null
                    && !instructions.isEmpty()) {
                String current = instructions.get(currentStep);
                tts.stop();
                tts.speak(
                        current,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "STEP" + currentStep
                );
            }
        });

    }

    private void updateStepDisplay() {
        // 1) Texto
        String instruction = instructions.get(currentStep);
        tvInstruction.setText(instruction);

        // 2) Indicador “Paso X de Y”
        tvStepIndicator.setText(
                "Paso " + (currentStep + 1) + " de " + instructions.size()
        );

        // 3) Habilita/deshabilita botones
        btnPrev.setEnabled(currentStep > 0);
        btnNext.setEnabled(currentStep < instructions.size() - 1);

        // 4) TalkBack: lee la instrucción actual si está habilitado
        if (ttsEnabled && tts != null) {
            if (currentStep == 0) {
                // Primera instrucción: simplemente encolamos
                tts.speak(
                        instruction,
                        TextToSpeech.QUEUE_ADD,
                        null,
                        "STEP0"
                );
            } else {
                // Pasos posteriores: interrumpimos lo que haya y hablamos
                tts.stop();
                tts.speak(
                        instruction,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "STEP" + currentStep
                );
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);

        requestLocationPermission();
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION);

        } else {
            checkLocationEnabledAndProceed();
        }
    }

    private void checkLocationEnabledAndProceed() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!gpsEnabled && !netEnabled) {
            // Pedir activar ubicación
            new AlertDialog.Builder(this)
                    .setMessage("Por favor, activa la ubicación para continuar")
                    .setCancelable(false)
                    .setPositiveButton("Ajustes", (DialogInterface d, int id) -> {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .show();
        } else {
            drawRouteFromCurrentLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sólo reintentar si el mapa ya está listo
        if (map != null
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            checkLocationEnabledAndProceed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_LOCATION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkLocationEnabledAndProceed();
        } else {
            Toast.makeText(this,
                    "Permiso de ubicación denegado, no es posible trazar la ruta",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }


    private void drawRouteFromCurrentLocation() {
        if (map == null) return;
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        map.setMyLocationEnabled(true);

        // Pedimos una única actualización de alta precisión
        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setNumUpdates(1)
                .setInterval(0);

        fusedLocationClient.requestLocationUpdates(
                req,
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult result) {
                        fusedLocationClient.removeLocationUpdates(this);
                        Location loc = result.getLastLocation();
                        if (loc != null) {
                            LatLng origin = new LatLng(loc.getLatitude(), loc.getLongitude());
                            LatLng dest   = new LatLng(DEST_LAT, DEST_LNG);

                            // marcamos el destino
                            map.addMarker(new MarkerOptions()
                                    .position(dest));
                            // Marker del campus siempre rojo + etiqueta visible
                            Bitmap campusIcon = iconGen.makeIcon("Campus Álaba \n    UPV/EHU");
                            map.addMarker(new MarkerOptions()
                                    .position(dest)
                                    .icon(BitmapDescriptorFactory.fromBitmap(campusIcon))
                                    // subimos un poco la etiqueta sobre el punto
                                    .anchor(iconGen.getAnchorU(), iconGen.getAnchorV() + 0.9f)
                            );
                            if ("walking".equals(travelMode) || "bicycling".equals(travelMode)) {
                                fetchAndRenderPolyline(origin, dest);
                            } else {
                                fetchAndRenderTransit(origin, dest, travelMode);
                            }
                            focusCamera(origin, dest);
                        }
                    }
                },
                Looper.getMainLooper()
        );
    }


    private void fetchAndRenderPolyline(LatLng origin, LatLng dest) {
        new Thread(() -> {
            try {
                String langParam = ttsLang; // previamente leído de SharedPreferences

                // 1) Llamada a Directions API (en español)
                String url = "https://maps.googleapis.com/maps/api/directions/json?"
                        + "origin=" + origin.latitude + "," + origin.longitude
                        + "&destination=" + dest.latitude + "," + dest.longitude
                        + "&mode=" +  travelMode
                        + "&language=" + langParam
                        + "&key=??";

                HttpURLConnection conn = (HttpURLConnection)
                        new URL(url).openConnection();
                InputStreamReader in = new InputStreamReader(conn.getInputStream());
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024];
                int r;
                while ((r = in.read(buf)) != -1) {
                    sb.append(buf, 0, r);
                }

                // 2) Parsear JSON
                JSONObject json = new JSONObject(sb.toString());
                JSONObject route = json.getJSONArray("routes").getJSONObject(0);
                JSONObject leg   = route.getJSONArray("legs").getJSONObject(0);

                // 3) Extraer polyline
                String points = route
                        .getJSONObject("overview_polyline")
                        .getString("points");
                List<LatLng> path = PolyUtil.decode(points);

                // 4) Extraer distancia y duración
                final String distText = leg
                        .getJSONObject("distance")
                        .getString("text");
                final String durText = leg
                        .getJSONObject("duration")
                        .getString("text");

                // 5) Calcular ETA (hora estimada de llegada)
                long seconds = leg.getJSONObject("duration").getLong("value");
                long arrivalMillis = System.currentTimeMillis() + seconds * 1000;
                Date arrivalDate = new Date(arrivalMillis);
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", new Locale("es", "ES"));
                final String etaText = sdf.format(arrivalDate);

                // 6) Extraer pasos para paso a paso
                JSONArray stepsArray = leg.getJSONArray("steps");
                instructions.clear();
                for (int i = 0; i < stepsArray.length(); i++) {
                    String htmlInst = stepsArray.getJSONObject(i)
                            .getString("html_instructions");
                    Spanned safe = Html.fromHtml(htmlInst, Html.FROM_HTML_MODE_LEGACY);
                    instructions.add(safe.toString());
                }

                // 7) Actualizar UI y activar TalkBack en hilo principal
                runOnUiThread(() -> {
                    // Dibujar ruta
                    map.addPolyline(new PolylineOptions()
                            .addAll(path)
                            .width(8)
                            .color(getColor(R.color.colorPrimaryDark)));

                    // Mostrar info de ruta + ETA
                    String infoText = "Distancia: " + distText
                            + "   Duración: " + durText
                            + "   H.Llegada: " + etaText;
                    tvRouteInfo.setText(infoText);
                    tvRouteInfo.setVisibility(View.VISIBLE);

                    // ——— Mostrar controles para todos los modos ———
                    btnModeIndicator.setVisibility(View.VISIBLE);
                    btnTalkToggle   .setVisibility(View.VISIBLE);

                    if ("bicycling".equals(travelMode)) {
                        btnMap.setVisibility(View.VISIBLE);
                    } else {
                        btnMap.setVisibility(View.GONE);
                    }

                    // TalkBack: solo hablar distancia+ETA al inicio
                    if (ttsEnabled && tts != null) {
                        tts.speak(
                                infoText,
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "INIT"
                        );
                    }

                    if (!instructions.isEmpty()) {
                        // Configurar paso a paso visualmente
                        currentStep = 0;
                        btnPrev.setEnabled(false);
                        btnNext.setEnabled(instructions.size() > 1);
                        updateStepDisplay();  // Esto también hablará la primera instrucción si ttsEnabled==true
                        llInstructionGroup.setVisibility(View.VISIBLE);
                        tvStepIndicator.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void fetchAndRenderTransit(LatLng origin, LatLng dest, String mode) {
        new Thread(() -> {
            try {
                // 1) Llamada a Directions API en modo transit
                String url = "https://maps.googleapis.com/maps/api/directions/json?"
                        + "origin=" + origin.latitude + "," + origin.longitude
                        + "&destination=" + dest.latitude + "," + dest.longitude
                        + "&mode=transit"
                        + "&transit_mode=" + mode    // "bus" o "tram"
                        + "&language=" + ttsLang
                        + "&key=??";

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                StringBuilder sb = new StringBuilder();
                try (InputStreamReader in = new InputStreamReader(conn.getInputStream())) {
                    char[] buf = new char[1024];
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        sb.append(buf, 0, r);
                    }
                }

                JSONObject json  = new JSONObject(sb.toString());
                JSONObject route = json.getJSONArray("routes").getJSONObject(0);
                JSONObject leg   = route.getJSONArray("legs").getJSONObject(0);

                // 2) Extraer distancia/duración/ETA
                final String distText = leg.getJSONObject("distance").getString("text");
                final String durText  = leg.getJSONObject("duration").getString("text");
                long seconds = leg.getJSONObject("duration").getLong("value");
                String etaText = new SimpleDateFormat("HH:mm", new Locale("es","ES"))
                        .format(new Date(System.currentTimeMillis() + seconds*1000));

                // 3) Parsear steps y preparar paths
                JSONArray steps = leg.getJSONArray("steps");
                List<List<LatLng>> segmentPaths = new ArrayList<>(steps.length());
                // Guardaremos info del tramo de transit para el dialogo
                String boardLine     = "", boardStop = "", alightStop = "";
                String boardTimeText = "";
                long   headwaySec    = -1;

                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    // decode polyline
                    String poly = step.getJSONObject("polyline").getString("points");
                    segmentPaths.add( PolyUtil.decode(poly) );

                    if (step.has("transit_details")) {
                        JSONObject td = step.getJSONObject("transit_details");
                        JSONObject line = td.getJSONObject("line");
                        boardLine = line.optString("short_name", line.optString("name",""));
                        // parada de subida
                        JSONObject dep = td.getJSONObject("departure_stop");
                        boardStop = dep.getString("name");
                        boardTimeText = td.getJSONObject("departure_time").getString("text");
                        // parada de bajada
                        JSONObject arr = td.getJSONObject("arrival_stop");
                        alightStop = arr.getString("name");
                        // frecuencia (puede no venir)
                        if (td.has("headway")) {
                            headwaySec = td.getLong("headway");
                        }
                    }
                }
                // Tras haber poblado todas las colecciones y variables...
                final List<List<LatLng>> finalSegmentPaths = segmentPaths;
                final JSONArray finalSteps         = steps;
                final String finalBoardLine        = boardLine;
                final String finalBoardStop        = boardStop;
                final String finalAlightStop       = alightStop;
                final String finalBoardTimeText    = boardTimeText;
                final long   finalHeadway          = headwaySec;
                final LatLng finalOrigin           = origin;
                final LatLng finalDest             = dest;
                final String finalDistText         = distText;
                final String finalDurText          = durText;
                final String finalEtaText          = etaText;

                // 4) Renderizar en UI
                runOnUiThread(() -> {
                    // 1) Marker del campus siempre rojo + etiqueta visible
                    Bitmap campusIcon = iconGen.makeIcon("Campus Álaba \n    UPV/EHU");
                    map.addMarker(new MarkerOptions()
                            .position(finalDest)
                            .icon(BitmapDescriptorFactory.fromBitmap(campusIcon))
                            // sube un poco la etiqueta sobre el punto
                            .anchor(iconGen.getAnchorU(), iconGen.getAnchorV() + 0.9f)
                    );
                    // 2) Dibuja cada segmento
                    for (int i = 0; i < finalSegmentPaths.size(); i++) {
                        // 2.1) ¿Es tramo de transporte público?
                        boolean isTransit = false;
                        try {
                            if (finalSteps.getJSONObject(i).has("transit_details")) {
                                isTransit = true;
                            }
                        } catch (Exception ignored) { }

                        // 2.2) Ancho distinto según modo
                        float strokeWidth = isTransit ? 12f : 8f;

                        // 2.3) Crea PolylineOptions con ancho adecuado
                        PolylineOptions opts = new PolylineOptions()
                                .addAll(finalSegmentPaths.get(i))
                                .width(strokeWidth)
                                .color(isTransit
                                        ? getColor(R.color.colorAccent)
                                        : Color.DKGRAY);

                        // 2.4) Añade la polyline al mapa
                        map.addPolyline(opts);
                    }

                    // 3) Marcadores en todas las paradas de cada tramo de transporte
                    for (int i = 0; i < finalSteps.length(); i++) {
                        JSONObject step = finalSteps.optJSONObject(i);
                        if (step != null && step.has("transit_details")) {
                            try {
                                routeSteps = steps;
                                JSONObject td = step.getJSONObject("transit_details");

                                // – Parada de subida de este tramo –
                                JSONObject depStop = td.getJSONObject("departure_stop");
                                JSONObject depLocJson = depStop.getJSONObject("location");
                                LatLng depLoc = new LatLng(
                                        depLocJson.getDouble("lat"),
                                        depLocJson.getDouble("lng")
                                );
                                // Pin negro
                                map.addMarker(new MarkerOptions()
                                        .position(depLoc)
                                        .icon(BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_YELLOW)));
                                // Etiqueta arriba
                                Bitmap depIcon = iconGen.makeIcon(depStop.getString("name"));
                                map.addMarker(new MarkerOptions()
                                        .position(depLoc)
                                        .icon(BitmapDescriptorFactory.fromBitmap(depIcon))
                                        .anchor(iconGen.getAnchorU(), iconGen.getAnchorV() + 1.2f));

                                // – Parada de bajada de este tramo –
                                JSONObject arrStop = td.getJSONObject("arrival_stop");
                                JSONObject arrLocJson = arrStop.getJSONObject("location");
                                LatLng arrLoc = new LatLng(
                                        arrLocJson.getDouble("lat"),
                                        arrLocJson.getDouble("lng")
                                );
                                map.addMarker(new MarkerOptions()
                                        .position(arrLoc)
                                        .icon(BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_YELLOW)));
                                Bitmap arrIcon = iconGen.makeIcon(arrStop.getString("name"));
                                map.addMarker(new MarkerOptions()
                                        .position(arrLoc)
                                        .icon(BitmapDescriptorFactory.fromBitmap(arrIcon))
                                        .anchor(iconGen.getAnchorU(), iconGen.getAnchorV() + 1.2f));

                            } catch (Exception e) {
                                // si falla un tramo, seguimos con el siguiente
                            }
                        }
                    }

                    // 4) Texto de info de ruta + bus
                    StringBuilder info = new StringBuilder();
                    info.append("Distancia: ").append(finalDistText)
                            .append("   Duración: ").append(finalDurText)
                            .append("   H.Llegada: ").append(finalEtaText);

                    tvRouteInfo.setText(info.toString());
                    tvRouteInfo.setVisibility(View.VISIBLE);

                    // Indicador de modo
                    btnModeIndicator.setVisibility(View.VISIBLE);
                    // Botón TalkBack
                    btnTalkToggle.setVisibility(View.VISIBLE);
                    // Botón “Info de viaje”
                    btnInfo.setVisibility(View.VISIBLE);
                    // Botón “Ver mapa bici”
                    btnMap.setVisibility(View.VISIBLE);


                    // 5) Paso a paso + TTS
                    instructions.clear();
                    for (int i = 0; i < finalSteps.length(); i++) {
                        try {
                            String html = finalSteps.getJSONObject(i)
                                    .getString("html_instructions");
                            instructions.add(Html.fromHtml(html,
                                    Html.FROM_HTML_MODE_LEGACY).toString());
                        } catch (Exception ignored) {}
                    }
                    if (!instructions.isEmpty()) {
                        currentStep = 0;
                        btnPrev.setEnabled(false);
                        btnNext.setEnabled(instructions.size() > 1);
                        updateStepDisplay();
                        llInstructionGroup.setVisibility(View.VISIBLE);
                        tvStepIndicator.setVisibility(View.VISIBLE);

                        if (ttsEnabled) {
                            tts.speak(info.toString(),
                                    TextToSpeech.QUEUE_FLUSH, null, "INIT");
                            tts.speak(instructions.get(0),
                                    TextToSpeech.QUEUE_ADD, null, "STEP0");
                        }
                    }

                    // 6) Ajusta cámara a ambos puntos
                    LatLngBounds bounds = new LatLngBounds.Builder()
                            .include(finalOrigin)
                            .include(finalDest)
                            .build();
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Encuentra el primer índice de steps[] que tenga transit_details.
     * Si no hay ninguno, devuelve -1.
     */
    private int findTransitIndex(JSONArray steps) {
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && step.has("transit_details")) {
                return i;
            }
        }
        return -1;
    }


    private void showBikeMapDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bike_map, null);
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseBikeMap);
        WebView web = dialogView.findViewById(R.id.webViewBike);

        // Configuración del WebView
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        // Evita que abra el navegador: fuerza carga interna
        web.setWebViewClient(new WebViewClient());
        web.loadUrl("https://www.vitoria-gasteiz.org/geovitoria/geo?idioma=ES#YWNjaW9uPXNob3cmaWQ9MjE1MTAmbj11bmRlZmluZWQ=");

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Gestionar ciclo de vida mínimamente
        dialog.setOnShowListener(d -> web.onResume());
        dialog.setOnDismissListener(d -> web.onPause());

        dialog.show();
    }

    /**
     * Muestra un diálogo con un WebView cargando
     * el PDF de líneas de autobús y tranvía de Vitoria.
     */
    private void showBusTramMapDialog() {
        // 1) Inflamos exactamente el mismo layout que usabas para bici
        View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_bike_map, null);

        // 2) Cambiamos el título dinámicamente
        TextView tvTitle = dialogView.findViewById(R.id.tvBikeMapTitle);
        tvTitle.setText("Mapa bus/tranvía");

        // 3) Configuramos el WebView para mostrar el PDF
        WebView web = dialogView.findViewById(R.id.webViewBike);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setBuiltInZoomControls(true);
        web.getSettings().setDisplayZoomControls(false);
        // Para que funcione en WebView, lo cargamos vía Google Docs:
        String pdfUrl = "https://www.vitoria-gasteiz.org/http/wb021/contenidosEstaticos/adjuntos/es/74/98/27498.pdf";
        String viewerUrl = "https://docs.google.com/gview?embedded=true&url="
                + Uri.encode(pdfUrl);
        web.loadUrl(viewerUrl);

        // 4) Preparamos el diálogo y su botón de cierre
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        ImageButton btnClose = dialogView.findViewById(R.id.btnCloseBikeMap);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 5) Ciclo de vida del WebView dentro del diálogo
        dialog.setOnShowListener(d -> web.onResume());
        dialog.setOnDismissListener(d -> web.onPause());

        dialog.show();
    }

    private void focusCamera(LatLng origin, LatLng dest) {
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(origin)
                .include(dest)
                .build();
        map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 100)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}

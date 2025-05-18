**UniGo - Universidad Go!**
Aplicación Android de movilidad diseñada para guiar al usuario hasta el campus de Álava (UPV/EHU) de forma intuitiva y accesible.

## 📖 Descripción general

UniGo ofrece rutas y navegaciones en tiempo real a pie, en bicicleta, en autobús y en tranvía, todo integrado dentro de la propia app sin saltar a ninguna otra. Además incluye capas de información ambiental y urbana como calidad del aire, tráfico y previsión meteorológica.

## ✨ Características principales

* **Navegación multimodal**

  * Modo a pie (*walking*)
  * Modo bicicleta (*bicycling*)
  * Modo autobús (*transit - bus*)
  * Modo tranvía (*transit - tram*)

* **Detalle de ruta**

  * Distancia, duración y hora estimada de llegada
  * Paso a paso con instrucciones geolocalizadas
  * Botones "Anterior" y "Siguiente" para navegar por cada paso

* **Text-to-Speech (TalkBack)**

  * Lectura de la información inicial (distancia, duración)
  * Lectura de cada instrucción al cambiar de paso
  * Toggle para activar/desactivar TalkBack en cualquier momento

* **Mapas dinámicos**

  * Google Maps embebido con polilíneas y marcadores
  * Marcador fijo en el campus (Campus Álava UPV/EHU)
  * Marcadores de parada de bus/tranvía con etiquetas visibles

* **Información auxiliar**

  * **Calidad del aire**: capa de heatmap US\_AQI centrada en el campus
  * **Estado del tráfico**: overlay de tráfico vial en tiempo real
  * **Clima y previsión**: clima actual, humedad, estado del cielo e iconos + pronóstico a 3 días

* **Splash screen animado**: video de introduction con logo dibujándose

* **Modo claro/oscuro**: coherente con el tema del sistema

## 🛠 Instalación y configuración

1. Clonar el repositorio:

   ```bash
   git clone https://github.com/Surtur9/unigo-android.git
   ```
2. Abrir en Android Studio y sincronizar Gradle.
3. Añadir tu API\_KEY de Google Maps y APIs asociadas en `AndroidManifest.xml`:

   ```xml
   <meta-data
     android:name="com.google.android.geo.API_KEY"
     android:value="YOUR_GOOGLE_MAPS_API_KEY"/>
   ```
4. Ejecútala en un dispositivo o emulador con servicios de Google Play.

## 🔧 Arquitectura y tecnologías

* Lenguaje: Java
* IDE: Android Studio
* Google Maps Android SDK + Directions API + Air Quality API + Weather API + Traffic tiles
* GTFSHelper: parsers de ficheros GTFS (opcional para mapa de rutas)
* Material Components (dialogs, tarjetas, botones flotantes)
* TextToSpeech para TalkBack

## 📁 Estructura de ficheros

```
app/  
├─ src/main/java/com/example/unigo/  
│   ├─ MainActivity.java          # Pantalla principal con FABs  
│   ├─ MapActivity.java           # Lógica de navegación y mapas  
│   ├─ SplashActivity.java        # Video splash inicial  
│   ├─ SettingsBottomSheet.java   # Ajustes (tema, TalkBack, idioma)  
├─ src/main/res/layout/           # XMLs de interfaces  
├─ src/main/res/drawable/         # Fondos redondeados, shapes, iconos  
```

## 🚀 Uso rápido

1. Abrir la app.
2. Seleccionar medio de transporte (Andar, Bicicleta, Bus, Tranvía).
3. La app solicitará permisos de ubicación y mostrará la ruta en el mapa.
4. Usar los botones de instrucciones paso a paso y activar TalkBack si se desea audio.
5. Consultar calidad del aire, tráfico o clima desde sus respectivos botones.

## 📄 Licencia

Este proyecto está bajo la licencia MIT.

---

> Aplicación pensada para estudiantes y visitantes de la UPV/EHU campus Álava, enfocada en la movilidad sostenible y la accesibilidad.

Autores:
Aritz Blasco /
Asier Cardoso /
Ainhoa García

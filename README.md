# VR Player (Android)

Odtwarzacz wideo 360° (equirectangular, mono) do gogli typu Cardboard.

## Uruchomienie
1. Android Studio (Koala lub nowsze) -> File -> Open -> folder `VRPlayer`.
2. Poczekaj na Gradle Sync (JDK 17).
3. Podłącz telefon z żyroskopem i kliknij Run.

## Funkcje
- Pliki lokalne (system picker) oraz adresy URL (MP4/HLS/DASH przez Media3)
- Śledzenie ruchu głowy (Game Rotation Vector), wyśrodkowanie widoku
- Tryb VR (dwa oczy) + korekcja zniekształceń soczewek (K1/K2 w VrRenderer.kt)
- Tryb "okno 360°" bez gogli
- Dotyk = pauza w VR, długie przytrzymanie = menu

## Architektura
- `HeadTracker` – czujniki -> macierz widoku OpenGL
- `VrRenderer` – SurfaceTexture <- ExoPlayer; fragment shader liczy promień
  dla każdego piksela i próbkuje wideo equirect (+ dystorsja beczkowa)
- `PlayerActivity` – ExoPlayer + GLSurfaceView + overlay Compose
- `MainActivity` – ekran startowy (Compose)

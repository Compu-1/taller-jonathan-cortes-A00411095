# Reporte de ejecuciones – Implementación monolítica (Mandelbrot)

Fecha: 2026-10-15 · Preset: `seahorse` · Java 17 (OpenJDK) · CPU: 16 hilos lógicos (el monolito usa solo 1) · Una ejecución por configuración (sin repeticiones ni warm-up).

Comando base:
```
./gradlew :monolith:runMonolith --args="<ancho> <alto> <maxIter> seahorse output/mono_<ancho>x<alto>_<maxIter>.png"
```

Las imágenes quedan en `monolith/output/` (ruta relativa al módulo). Región: X inicia en -0.75, Y=[0.09, 0.10]; el ancho se ajusta por aspecto.

## Tiempo secuencial T_sec (s)

| Resolución | Píxeles | maxIter=500 | maxIter=1000 | maxIter=2000 | maxIter=5000 | maxIter=10000 |
|---|---|---|---|---|---|---|
| 1280x720 | 921,600 | 2.01 | 3.80 | 7.46 | 17.51 | 34.25 |
| 1920x1080 | 2,073,600 | 4.51 | 8.51 | 16.27 | 39.35 | 77.62 |
| 3840x2160 | 8,294,400 | 18.01 | 34.20 | 65.79 | 156.71 | 308.49 |

## Throughput (Mpíxeles/s)

| Resolución | 500 | 1000 | 2000 | 5000 | 10000 |
|---|---|---|---|---|---|
| 1280x720 | 0.458 | 0.242 | 0.124 | 0.053 | 0.027 |
| 1920x1080 | 0.460 | 0.244 | 0.127 | 0.053 | 0.027 |
| 3840x2160 | 0.461 | 0.243 | 0.126 | 0.053 | 0.027 |

## Escalado respecto a 1280x720 (mismo maxIter)

| Resolución | píxeles ×| 500 | 1000 | 2000 | 5000 | 10000 |
|---|---|---|---|---|---|---|
| 1280x720 | 1.00 | 1.00× | 1.00× | 1.00× | 1.00× | 1.00× |
| 1920x1080 | 2.25 | 2.24× | 2.24× | 2.18× | 2.25× | 2.27× |
| 3840x2160 | 9.00 | 8.95× | 8.99× | 8.82× | 8.95× | 9.01× |

## Observaciones

- El tiempo crece casi linealmente con el número de píxeles (2.25× y 9× píxeles → ~2.2× y ~9× de tiempo) y con `maxIter` (de 500 a 10000 iteraciones, ×20, el tiempo crece ~×17).
- La carga más pesada (3840x2160, 10000 iter) tomó **308.49 s (~5.1 min)**; es la línea base T_sec contra la que comparar la versión distribuida.
- El crecimiento con `maxIter` es algo sublineal porque en esta región muchos píxeles escapan antes del límite; el costo lo dominan los píxeles que llegan a `maxIter`.
- Al ser monohilo, 15 de 16 hilos lógicos quedan sin usar: es el margen que la versión distribuida/paralela puede aprovechar.
- Caveats: una sola medición por celda, sin calentamiento de JIT; el tiempo mide solo el cómputo (no incluye guardar el PNG). Gradle imprimió `./gradlew: line 165: C:\Program: No such file or directory` (ruta con espacios de Java en Windows), sin efecto en los resultados.

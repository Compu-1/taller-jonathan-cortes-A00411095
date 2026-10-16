# Resultados experimentales (parcial) – Master-Worker Mandelbrot

Fecha de las mediciones: 2026-10-15 · Región `seahorse` · 4K (3840x2160) · maxIter 5000 · Master en el PC del cliente (red Wi-Fi/hotspot).
T_sec de referencia (monolito, `docs/reporte_monolito.md`, medido en el PC del Master): **156 710 ms**.
Todas las corridas validadas celda por celda: `VERIFICACION OK` (0 diferencias).
Logs originales (no versionados): `output/logs/` (`master_exp1.log`, `master.log`, `exp1.log`, `exp2_*.log`, `exp3_*.log`).

## Experimento 1 – Granularidad (p=2 workers)
Fuente: `output/resultados_exp1_3840x2160_5000_p2.md|csv`. Una medición por celda.

| Estrategia | Parámetro | Tareas | T_dist (ms) | S | E |
|---|---|---|---|---|---|
| FixedCount | N=2 | 2 | 63 245 | 2.48× | 123.9 % |
| FixedCount | N=4 | 4 | 43 966 | 3.56× | 178.2 % |
| FixedCount | N=16 | 16 | 63 506 | 2.47× | 123.4 % |
| FixedTileSize | 32×32 | 8 160 | 136 833 | 1.15× | 57.3 % |
| FixedTileSize | 64×64 | 2 040 | 83 334 | 1.88× | 94.0 % |
| FixedTileSize | 128×128 | 510 | 67 736 | 2.31× | 115.7 % |
| FixedTileSize | 256×256 | 135 | 68 240 | 2.30× | 114.8 % |

Reparto por worker (Master): 64×64 → W3 y W2 con 1232 tareas en W2 (resumen parcial en log); 128×128 → W3 176 tareas/115 428 ms, W2 334 tareas/96 160 ms; 256×256 → W3 52/118 451 ms, W2 83/91 959 ms.

## Experimento 2 – Hilos de un worker (solo Worker-3, baldosas 128×128, 510 tareas) – REPETIDO 2026-10-16
Worker-3 = PC de 6 núcleos físicos / 12 hilos lógicos (dato del dueño). Master reiniciado y limpio (`output/logs/master_exp2_rep.log`); clientes `exp2b_t<hilos>_r<n>.log`. Todas `VERIFICACION OK`; el resumen del Master mostró siempre solo a Worker-3 con 510 tareas. S = T_sec/T_dist con T_sec = 156 710 ms (otro PC; sirve para comparar entre filas, no como eficiencia absoluta).

| Hilos | T_dist (ms) corridas | Media (mediana en 1 hilo) | S | Cómputo acumulado (ms) | Cómputo por baldosa (ms) |
|---|---|---|---|---|---|
| 1 | 177 361 / 394 068 / 137 286 | 177 361 (mediana) | 0.88× | 159 259 / 374 576 / 114 954 | 312 / 734 / 225 |
| 2 | 66 221 / 64 415 | 65 318 | 2.40× | 110 565 / 111 939 | 217 / 219 |
| 6 | 26 227 / 27 781 | 27 004 | 5.80× | 127 039 / 127 621 | 249 / 250 |
| 12 | 18 682 / 20 343 | 19 513 | 8.03× | 171 441 / 175 941 | 336 / 345 |
| 64 | 18 161 / 17 646 | 17 904 | 8.75× | 842 253 / 941 358 | 1 651 / 1 846 |

Observaciones:
- 1 hilo es muy inestable en este PC (137–394 s; 3 corridas, 2.9× entre extremos); con ≥2 hilos la dispersión es 3–9 %. La corrida de 137 s tiene el mismo costo por baldosa que 2 hilos (≈220 ms), por lo que es probablemente la representativa; las otras dos están degradadas (¿energía/frecuencia?). Se reportan las tres.
- Hasta 6 hilos (núcleos físicos) el cómputo por baldosa casi no cambia (217→250 ms): escalado cercano al ideal frente a la corrida de 137 s (5.1× con 6 hilos).
- De 6 a 12 hilos (hyperthreading) el T_dist mejora 1.38× y el costo por baldosa sube 35 %: los dos hilos de un núcleo comparten sus unidades de ejecución.
- De 12 a 64 hilos el T_dist mejora solo 1.09× (19.5→17.9 s) pero el costo por baldosa sube ~5× (≈340→≈1 750 ms): 64 hilos compiten por 12 hilos lógicos y el SO los alterna (cambio de contexto); el tiempo total no se degrada porque la CPU ya estaba saturada con 12.
- Comparación con el Exp. 3: p=1 con 6 hilos dio 23 252 ms por la mañana y 27 004 ms ahora (16 % de diferencia entre sesiones); ambas se reportan.

### Mediciones descartadas del 2026-10-15 (PC de Worker-3 degradado)
E = S/p con p=1 worker (por eso E>100 %).

| Hilos | Corrida 1 T_dist (ms) | Corrida 2 T_dist (ms) | S (c1 / c2) | Cómputo acumulado c1 / c2 (ms) |
|---|---|---|---|---|
| 1 | 285 995 | — | 0.55× | 210 671 |
| 2 | 216 904 | — | 0.72× | 358 857 |
| 6 | 81 072 | 77 108 | 1.93× / 2.03× | 386 126 / 387 497 |
| 12 | 148 399 (atípica) | 55 756 | 1.06× / 2.81× | 503 013 / 531 382 |
| 64 | 50 986 | 29 122 | 3.07× / 5.38× | 2 915 201 / 648 282 |

Observaciones: 6 hilos es estable (5 % entre corridas); 12 y 64 hilos varían mucho entre corridas (166 % y 75 %); no se observó caída por sobredimensionamiento a 64 hilos. 1 y 2 hilos sin repetir.
Dato sin resolver: núcleos físicos/lógicos reales del PC de Worker-3 (el usuario indicó 6 físicos).

## Experimento 3 – Escalabilidad (6 hilos por worker, baldosas 128×128)
Remedición del 2026-10-16 (Master en 172.20.10.10, log `output/logs/master_p1_remedicion.log`; clientes `exp3_p1_r1|r2.log`, `exp3_p2b_r1|r2.log`). Todas `VERIFICACION OK`.

| p | Corrida 1 T_dist (ms) | Corrida 2 T_dist (ms) | Media (ms) | S_rel = T(1)/T(p) | E_rel = S_rel/p | Reparto de tareas (c1 / c2) |
|---|---|---|---|---|---|---|
| 1 | 23 569 | 22 934 | 23 252 | 1.00× | 100 % | W3: 510 / 510 |
| 2 | 15 282 | 13 889 | 14 586 | 1.59× | 79.7 % | W3 252, W2 258 / W3 261, W2 249 |
| 4 | 7 516 | 7 234 | 7 375 | 3.15× | 78.8 % | W1 277, W2 96, W3 44, W4 93 / W1 267, W2 110, W3 41, W4 92 |

Cómputo acumulado p=4 (c1 / c2): W1 39 564 / 37 192, W2 26 288 / 29 552, W3 37 954 / 37 025, W4 34 988 / 34 740 ms. Log: `master_p1_remedicion.log`, clientes `exp3_p4_r1|r2.log`. Worker-1 corrió en el PC del Master (comparte CPU con Master y cliente); Worker-2/3/4 en PCs remotos.
Cómputo acumulado: p=1 → W3 115 386 / 114 877 ms; p=2 → W3 60 124 / 58 573 ms, W2 51 503 / 52 282 ms. Cómputo por baldosa de Worker-3 ≈ 226 ms (p=1) y ≈ 230 ms (p=2): consistente.
Speedup contra T_sec (156 710 ms, otro PC): p=1 6.65× / 6.83×; p=2 10.25× / 11.28× (no es comparable como eficiencia porque T_sec viene de un PC distinto de los workers; usar S_rel).

**Inconsistencia resuelta:** las mediciones de p=1 del 2026-10-15 (81 072 / 77 108 ms, en Exp. 2) estaban degradadas (~3.4× más lentas que hoy). Causa en el PC de Worker-3 por confirmar con su dueño. Las mediciones de p=2 de ayer (17 232 / 13 632 ms) son consistentes con las de hoy. El Exp. 2 del 15-oct se descartó y se repitió el 16-oct.

### Análisis de Amdahl (S_rel = T(1)/T(p), medias de 2 corridas)
Modelo: 1/S = (1−f) + f/p. Karp-Flatt: e(p) = (1/S − 1/p)/(1 − 1/p).

| p | S_rel | 1/S | e(p) = fracción secuencial estimada |
|---|---|---|---|
| 2 | 1.594 | 0.627 | 0.255 |
| 4 | 3.153 | 0.317 | 0.090 |

Con p=4 se obtiene (1−f) ≈ 0.09 (f ≈ 0.91) y un techo teórico 1/(1−f) ≈ 11×; con p=2 se obtendría (1−f) ≈ 0.25. Que e(p) baje al subir p **no** indica una fracción secuencial real sino que el modelo no se cumple: (i) los nodos son heterogéneos (Worker-1 tomó ~270 de 510 tareas, Worker-3 solo ~42, frente a ~255 en p=2), (ii) T(1) se midió con Worker-3, el nodo más lento, lo que infla S_rel con p=4, (iii) la variación entre corridas de p=2 es ~10 %. Conclusión defendible: la parte estrictamente secuencial (consolidación en el Master, partición, E/S) es pequeña, de orden ≤ 0.1 del tiempo, con la salvedad de la heterogeneidad. Para un valor limpio habría que repetir con nodos de igual potencia (p. ej. Worker-3 solo y luego sumar nodos equivalentes).
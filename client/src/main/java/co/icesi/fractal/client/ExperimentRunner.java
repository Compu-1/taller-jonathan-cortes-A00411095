package co.icesi.fractal.client;

import FractalApp.*;
import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.core.MandelbrotCore;
import com.zeroc.Ice.*;
import java.util.UUID;

/**
 * Harness automatizado para la ejecucion de la matriz experimental.
 * Ejecuta multiples pruebas variando la estrategia y parametros, y emite
 * una tabla de resultados comparativa en formato Markdown lista para el informe.
 */
public class ExperimentRunner {

    public static void main(String[] args) {
        String masterHost = (args.length >= 1) ? args[0] : "localhost";
        int masterPort = (args.length >= 2) ? Integer.parseInt(args[1]) : 10000;
        int width = (args.length >= 3) ? Integer.parseInt(args[2]) : 3840;
        int height = (args.length >= 4) ? Integer.parseInt(args[3]) : 2160;
        int maxIter = (args.length >= 5) ? Integer.parseInt(args[4]) : 5000;
        String presetName = (args.length >= 6) ? args[5] : "seahorse";

        FractalRegion region = FractalRegion.fromName(presetName);
        double xMin = region.getXMin();
        double xMax = region.getXMax();
        double yMin = region.getYMin();
        double yMax = region.getYMax();

        // Ajuste isometrico de aspecto
        if (width > 0 && height > 0) {
            double aspect = (double) width / (double) height;
            double realHeight = yMax - yMin;
            xMax = xMin + realHeight * aspect;
        }

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.setProperty("Ice.MessageSizeMax", "204800");

        try (Communicator communicator = Util.initialize(initData)) {
            String masterEndpoint = String.format("MasterService:tcp -h %s -p %d", masterHost, masterPort);
            MasterServicePrx master = MasterServicePrx.checkedCast(communicator.stringToProxy(masterEndpoint));

            if (master == null) {
                System.err.println("[ERROR] No se pudo conectar al Master.");
                return;
            }

            int p = master.getWorkerCount();
            System.out.println("=================================================");
            System.out.println("     INICIANDO MATRIZ DE EXPERIMENTACIÓN");
            System.out.println("=================================================");
            System.out.printf("Region / Preset: %s (%s)%n", region.getName(), region.getId());
            System.out.printf("Resolucion:      %dx%d | MaxIter: %d | Workers: %d%n%n", width, height, maxIter, p);

            // 1. Linea base monohilo: se reutiliza T_sec del monolito si se pasa como arg 7 (ms)
            long tSec;
            if (args.length >= 7 && Long.parseLong(args[6]) > 0) {
                tSec = Long.parseLong(args[6]);
                System.out.printf("T_sec (reutilizado del monolito) = %d ms (%.2f s)%n%n", tSec, tSec / 1000.0);
            } else {
                System.out.println("Calculando tiempo secuencial monohilo T_sec...");
                long t0Sec = System.currentTimeMillis();
                double dx0 = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
                double dy0 = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;
                for (int y = 0; y < height; y++) {
                    double ci = yMin + y * dy0;
                    for (int x = 0; x < width; x++) {
                        MandelbrotCore.computePoint(xMin + x * dx0, ci, maxIter);
                    }
                }
                tSec = System.currentTimeMillis() - t0Sec;
                System.out.printf("T_sec = %d ms (%.2f s)%n%n", tSec, tSec / 1000.0);
            }

            // Matriz de referencia (paralela, solo para validar celda por celda; no se mide)
            boolean validate = !(args.length >= 8 && args[7].equals("novalidate"));
            int[][] reference = null;
            if (validate) {
                System.out.println("Calculando matriz de referencia para validacion (paralelo local)...");
                reference = new int[height][width];
                final int[][] ref = reference;
                final double fdx = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
                final double fdy = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;
                final double fx = xMin, fy = yMin;
                java.util.stream.IntStream.range(0, height).parallel().forEach(y -> {
                    double ci = fy + y * fdy;
                    for (int x = 0; x < width; x++) {
                        ref[y][x] = MandelbrotCore.computePoint(fx + x * fdx, ci, maxIter);
                    }
                });
            }

            // Casos de la Tabla 1 del enunciado
            // Tipo 0 (FixedCount): N = 2, 4, 16 franjas | Tipo 1 (FixedTileSize): S = 16 (opcional), 32, 64, 128, 256
            boolean withTile16 = args.length >= 9 && args[8].equals("tile16");
            int[][] testCases = withTile16
                ? new int[][] {{0, 2}, {0, 4}, {0, 16}, {1, 16}, {1, 32}, {1, 64}, {1, 128}, {1, 256}}
                : new int[][] {{0, 2}, {0, 4}, {0, 16}, {1, 32}, {1, 64}, {1, 128}, {1, 256}};

            String header = "| Estrategia | Parámetro | Tareas Generadas | T_sec (ms) | T_dist (ms) | Speedup (S) | Eficiencia (E) | Validación |";
            String sep = "| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |";
            System.out.println(header);
            System.out.println(sep);
            StringBuilder md = new StringBuilder(header).append('\n').append(sep).append('\n');
            StringBuilder csv = new StringBuilder("estrategia,parametro,tareas,workers,t_sec_ms,t_dist_ms,speedup,eficiencia_pct,errores\n");

            for (int[] tc : testCases) {
                int stType = tc[0];
                int stParam = tc[1];
                String stName = (stType == 0) ? "FixedCount (Franjas)" : "FixedTileSize (Baldosas)";

                String jobId = "exp-" + UUID.randomUUID().toString().substring(0, 8);
                RenderConfig config = new RenderConfig(
                    jobId, width, height, xMin, xMax, yMin, yMax, maxIter, stType, stParam
                );

                long t0Dist = System.currentTimeMillis();
                int[][] dist = master.renderFractal(config);
                long tDist = System.currentTimeMillis() - t0Dist;

                long errors = -1;
                if (reference != null) {
                    errors = 0;
                    for (int y = 0; y < height; y++) {
                        if (!java.util.Arrays.equals(reference[y], dist[y])) {
                            for (int x = 0; x < width; x++) if (reference[y][x] != dist[y][x]) errors++;
                        }
                    }
                }

                double speedup = (double) tSec / (double) Math.max(1, tDist);
                double efficiency = (p > 0) ? (speedup / p) * 100.0 : 0.0;

                int tasksCount = (stType == 0) ? Math.min(stParam, height)
                    : (int) (Math.ceil((double) width / stParam) * Math.ceil((double) height / stParam));
                String valid = (errors < 0) ? "n/a" : (errors == 0 ? "OK" : "FALLO(" + errors + ")");

                String row = String.format("| %s | %d | %d | %d | %d | %.2fx | %.1f%% | %s |",
                    stName, stParam, tasksCount, tSec, tDist, speedup, efficiency, valid);
                System.out.println(row);
                md.append(row).append('\n');
                csv.append(String.format(java.util.Locale.ROOT, "%s,%d,%d,%d,%d,%d,%.4f,%.2f,%d%n",
                    stName, stParam, tasksCount, p, tSec, tDist, speedup, efficiency, errors));
            }

            try {
                java.nio.file.Path dir = java.nio.file.Paths.get("output");
                java.nio.file.Files.createDirectories(dir);
                String tag = String.format("%dx%d_%d_p%d", width, height, maxIter, p);
                java.nio.file.Files.writeString(dir.resolve("resultados_exp1_" + tag + ".md"), md.toString());
                java.nio.file.Files.writeString(dir.resolve("resultados_exp1_" + tag + ".csv"), csv.toString());
                System.out.println("\nResultados guardados en output/resultados_exp1_" + tag + ".(md|csv)");
            } catch (java.io.IOException e) {
                System.err.println("[WARN] No se pudo guardar resultados: " + e.getMessage());
            }

            System.out.println("\n[FIN] Matriz experimental finalizada.");

        } catch (java.lang.Exception e) {
            System.err.println("[ERROR] Error en el ejecutor de experimentos: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

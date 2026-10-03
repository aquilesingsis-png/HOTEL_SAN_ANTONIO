package untrm.hotel_san_antonio.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genera un PDF con una tabla (hoja A4 horizontal), sin bibliotecas externas: encabezado en cada
 * pagina, filas con texto ajustado, filas alternadas, totales al final y "Pagina x de y".
 * Usa la fuente Helvetica del propio PDF, que cubre tildes y la letra n con tilde.
 */
public final class PdfTabla {

    private static final float ANCHO = 842f;
    private static final float ALTO = 595f;
    private static final float MARGEN = 36f;
    private static final float TAMANO = 7.5f;
    private static final float INTERLINEADO = 9.5f;
    private static final float RELLENO = 3.5f;
    private static final float PIE = 28f;
    private static final int MAX_LINEAS = 3;
    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** Anchos de Helvetica (milesimas de punto) de los caracteres ASCII del 32 al 126. */
    private static final int[] ANCHOS = {
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584};

    private PdfTabla() {
    }

    public static byte[] generar(TablaExportable tabla) throws IOException {
        Dibujo dibujo = new Dibujo(tabla);
        dibujo.componer();
        return dibujo.empaquetar();
    }

    // ------------------------------------------------------------------ medidas

    static float ancho(String texto, float tamano, boolean negrita) {
        float suma = 0;
        for (int i = 0; i < texto.length(); i++) {
            suma += anchoLetra(texto.charAt(i));
        }
        return suma * tamano / 1000f * (negrita ? 1.07f : 1f);
    }

    private static int anchoLetra(char c) {
        if (c >= 32 && c <= 126) {
            return ANCHOS[c - 32];
        }
        // letras con tilde: mismo ancho que la letra base
        String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        char primera = base.charAt(0);
        if (primera >= 32 && primera <= 126) {
            return ANCHOS[primera - 32];
        }
        return 556;
    }

    /** Parte el texto en lineas que caben en "maximo"; si sobra, la ultima termina en puntos suspensivos. */
    static List<String> ajustar(String texto, float maximo, float tamano, boolean negrita) {
        List<String> lineas = new ArrayList<>();
        String actual = "";
        for (String palabra : texto.trim().split("\\s+")) {
            while (ancho(palabra, tamano, negrita) > maximo && palabra.length() > 1) {
                int corte = palabra.length() - 1;
                while (corte > 1 && ancho(palabra.substring(0, corte), tamano, negrita) > maximo) {
                    corte--;
                }
                if (!actual.isEmpty()) {
                    lineas.add(actual);
                    actual = "";
                }
                lineas.add(palabra.substring(0, corte));
                palabra = palabra.substring(corte);
            }
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (ancho(prueba, tamano, negrita) <= maximo) {
                actual = prueba;
            } else {
                lineas.add(actual);
                actual = palabra;
            }
        }
        if (!actual.isEmpty() || lineas.isEmpty()) {
            lineas.add(actual);
        }
        if (lineas.size() > MAX_LINEAS) {
            lineas = new ArrayList<>(lineas.subList(0, MAX_LINEAS));
            String ultima = lineas.get(MAX_LINEAS - 1);
            while (ultima.length() > 1 && ancho(ultima + "…", tamano, negrita) > maximo) {
                ultima = ultima.substring(0, ultima.length() - 1);
            }
            lineas.set(MAX_LINEAS - 1, ultima + "…");
        }
        return lineas;
    }

    /** Pasa un texto a lo que entiende el PDF: bytes de Windows-1252 y escape de ( ) \. */
    static String codificar(String texto) {
        byte[] bytes = texto.getBytes(CP1252);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ dibujo

    private static final class Dibujo {
        private final TablaExportable tabla;
        private final List<StringBuilder> paginas = new ArrayList<>();
        private final float[] xColumna;
        private final float[] anchoColumna;
        private StringBuilder pagina;
        private float y;

        Dibujo(TablaExportable tabla) {
            this.tabla = tabla;
            int n = tabla.encabezados().size();
            float util = ANCHO - 2 * MARGEN;
            double suma = 0;
            for (double p : tabla.pesos()) {
                suma += p;
            }
            xColumna = new float[n];
            anchoColumna = new float[n];
            float x = MARGEN;
            for (int i = 0; i < n; i++) {
                anchoColumna[i] = (float) (tabla.pesos()[i] / suma * util);
                xColumna[i] = x;
                x += anchoColumna[i];
            }
        }

        void componer() {
            nuevaPagina(true);
            int fila = 0;
            for (List<String> datos : tabla.filas()) {
                List<List<String>> celdas = new ArrayList<>();
                int maxLineas = 1;
                for (int i = 0; i < datos.size(); i++) {
                    List<String> lineas = ajustar(datos.get(i), anchoColumna[i] - 2 * RELLENO, TAMANO, false);
                    celdas.add(lineas);
                    maxLineas = Math.max(maxLineas, lineas.size());
                }
                float alto = maxLineas * INTERLINEADO + 2 * RELLENO - 2;
                if (y - alto < MARGEN + PIE) {
                    nuevaPagina(false);
                }
                if (fila % 2 == 1) {
                    rectangulo(MARGEN, y - alto, ANCHO - 2 * MARGEN, alto, 0.98f, 0.96f, 0.93f);
                }
                for (int i = 0; i < celdas.size(); i++) {
                    float ty = y - RELLENO - TAMANO + 1;
                    for (String linea : celdas.get(i)) {
                        float tx = tabla.numericas().contains(i)
                                ? xColumna[i] + anchoColumna[i] - RELLENO - ancho(linea, TAMANO, false)
                                : xColumna[i] + RELLENO;
                        texto(tx, ty, linea, TAMANO, false, 0.15f);
                        ty -= INTERLINEADO;
                    }
                }
                linea(MARGEN, y - alto, ANCHO - MARGEN, y - alto, 0.88f);
                y -= alto;
                fila++;
            }
            if (tabla.filas().isEmpty()) {
                texto(MARGEN + RELLENO, y - 14, "Sin registros para los filtros elegidos.", 9, false, 0.4f);
                y -= 28;
            }
            resumen();
        }

        private void resumen() {
            if (tabla.resumen().isEmpty()) {
                return;
            }
            float alto = tabla.resumen().size() * 14f + 14f;
            if (y - alto - 10 < MARGEN + PIE) {
                nuevaPagina(false);
            }
            y -= 12;
            float ancho = 240f;
            float x = ANCHO - MARGEN - ancho;
            rectangulo(x, y - alto, ancho, alto, 0.96f, 0.90f, 0.78f);
            float ty = y - 15;
            for (String[] par : tabla.resumen()) {
                texto(x + 10, ty, par[0], 9, true, 0.18f);
                texto(x + ancho - 10 - ancho(par[1], 9, true), ty, par[1], 9, true, 0.18f);
                ty -= 14;
            }
            y -= alto;
        }

        private void nuevaPagina(boolean primera) {
            pagina = new StringBuilder();
            paginas.add(pagina);
            y = ALTO - MARGEN;
            texto(MARGEN, y - 12, "HOTEL SAN ANTONIO", 11, true, 0.23f);
            String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT));
            texto(ANCHO - MARGEN - ancho(fecha, 8, false), y - 11, fecha, 8, false, 0.4f);
            y -= 30;
            texto(MARGEN, y - 4, tabla.titulo(), 15, true, 0.1f);
            y -= 22;
            if (primera) {
                for (String linea : tabla.descripcion()) {
                    texto(MARGEN, y - 7, linea, 8.5f, false, 0.35f);
                    y -= 12;
                }
            }
            y -= 6;
            encabezadoTabla();
        }

        private void encabezadoTabla() {
            float alto = INTERLINEADO + 2 * RELLENO;
            rectangulo(MARGEN, y - alto, ANCHO - 2 * MARGEN, alto, 0.42f, 0.29f, 0.12f);
            for (int i = 0; i < tabla.encabezados().size(); i++) {
                String t = ajustar(tabla.encabezados().get(i), anchoColumna[i] - 2 * RELLENO, TAMANO, true).get(0);
                float tx = tabla.numericas().contains(i)
                        ? xColumna[i] + anchoColumna[i] - RELLENO - ancho(t, TAMANO, true)
                        : xColumna[i] + RELLENO;
                texto(tx, y - RELLENO - TAMANO + 1, t, TAMANO, true, 1f);
            }
            y -= alto;
        }

        private void texto(float x, float baseY, String texto, float tamano, boolean negrita, float gris) {
            pagina.append(gris).append(' ').append(gris).append(' ').append(gris).append(" rg\n")
                    .append("BT /").append(negrita ? "F2" : "F1").append(' ').append(tamano).append(" Tf ")
                    .append(x).append(' ').append(baseY).append(" Td (").append(codificar(texto)).append(") Tj ET\n");
        }

        private void rectangulo(float x, float baseY, float ancho, float alto, float r, float g, float b) {
            pagina.append(r).append(' ').append(g).append(' ').append(b).append(" rg\n")
                    .append(x).append(' ').append(baseY).append(' ').append(ancho).append(' ').append(alto)
                    .append(" re f\n");
        }

        private void linea(float x1, float y1, float x2, float y2, float gris) {
            pagina.append(gris).append(" G 0.4 w ").append(x1).append(' ').append(y1).append(" m ")
                    .append(x2).append(' ').append(y2).append(" l S\n");
        }

        byte[] empaquetar() throws IOException {
            int total = paginas.size();
            for (int i = 0; i < total; i++) {
                pagina = paginas.get(i);
                String pie = "Página " + (i + 1) + " de " + total;
                texto((ANCHO - ancho(pie, 8, false)) / 2, MARGEN - 6, pie, 8, false, 0.45f);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            List<Integer> posiciones = new ArrayList<>();
            escribir(salida, "%PDF-1.4\n");
            // 1 catalogo, 2 paginas, 3 y 4 fuentes, luego (pagina, contenido) por cada hoja
            objeto(salida, posiciones, 1, "<< /Type /Catalog /Pages 2 0 R >>");
            StringBuilder kids = new StringBuilder();
            for (int i = 0; i < total; i++) {
                kids.append(5 + 2 * i).append(" 0 R ");
            }
            objeto(salida, posiciones, 2, "<< /Type /Pages /Kids [" + kids + "] /Count " + total + " >>");
            objeto(salida, posiciones, 3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
            objeto(salida, posiciones, 4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
            for (int i = 0; i < total; i++) {
                int idPagina = 5 + 2 * i;
                objeto(salida, posiciones, idPagina, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) ANCHO + " "
                        + (int) ALTO + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (idPagina + 1) + " 0 R >>");
                byte[] contenido = paginas.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
                posiciones.add(salida.size());
                escribir(salida, (idPagina + 1) + " 0 obj\n<< /Length " + contenido.length + " >>\nstream\n");
                salida.write(contenido);
                escribir(salida, "\nendstream\nendobj\n");
            }
            int inicioXref = salida.size();
            int cantidad = posiciones.size() + 1;
            escribir(salida, "xref\n0 " + cantidad + "\n0000000000 65535 f \n");
            for (int posicion : posiciones) {
                escribir(salida, String.format(Locale.ROOT, "%010d 00000 n \n", posicion));
            }
            escribir(salida, "trailer\n<< /Size " + cantidad + " /Root 1 0 R >>\nstartxref\n" + inicioXref + "\n%%EOF\n");
            return salida.toByteArray();
        }

        private void objeto(ByteArrayOutputStream salida, List<Integer> posiciones, int id, String cuerpo) throws IOException {
            posiciones.add(salida.size());
            escribir(salida, id + " 0 obj\n" + cuerpo + "\nendobj\n");
        }

        private void escribir(ByteArrayOutputStream salida, String texto) throws IOException {
            salida.write(texto.getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}

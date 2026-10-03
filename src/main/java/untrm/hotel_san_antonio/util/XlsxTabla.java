package untrm.hotel_san_antonio.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Genera un libro de Excel (.xlsx) real, sin bibliotecas externas: titulo, filtros, encabezado en
 * negrita con la primera fila fija, filtros automaticos, importes como numeros y totales al final.
 */
public final class XlsxTabla {

    private static final int ESTILO_NORMAL = 0;
    private static final int ESTILO_ENCABEZADO = 1;
    private static final int ESTILO_IMPORTE = 2;
    private static final int ESTILO_TITULO = 3;
    private static final int ESTILO_TOTAL_TEXTO = 4;
    private static final int ESTILO_TOTAL_IMPORTE = 5;

    private XlsxTabla() {
    }

    public static byte[] generar(TablaExportable tabla) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida, StandardCharsets.UTF_8)) {
            agregar(zip, "[Content_Types].xml", CONTENT_TYPES);
            agregar(zip, "_rels/.rels", RELS);
            agregar(zip, "xl/workbook.xml", LIBRO);
            agregar(zip, "xl/_rels/workbook.xml.rels", RELS_LIBRO);
            agregar(zip, "xl/styles.xml", ESTILOS);
            agregar(zip, "xl/worksheets/sheet1.xml", hoja(tabla));
        }
        return salida.toByteArray();
    }

    private static void agregar(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String hoja(TablaExportable tabla) {
        int columnas = tabla.encabezados().size();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

        int fila = 1;
        StringBuilder datos = new StringBuilder();
        datos.append(filaTexto(fila++, tabla.titulo(), ESTILO_TITULO));
        for (String linea : tabla.descripcion()) {
            datos.append(filaTexto(fila++, linea, ESTILO_NORMAL));
        }
        fila++; // fila en blanco
        int filaEncabezado = fila;
        datos.append("<row r=\"").append(fila++).append("\">");
        for (int c = 0; c < columnas; c++) {
            datos.append(celdaTexto(c, filaEncabezado, tabla.encabezados().get(c), ESTILO_ENCABEZADO));
        }
        datos.append("</row>");
        for (List<String> valores : tabla.filas()) {
            int actual = fila++;
            datos.append("<row r=\"").append(actual).append("\">");
            for (int c = 0; c < columnas; c++) {
                String valor = c < valores.size() ? valores.get(c) : "";
                if (tabla.numericas().contains(c) && esNumero(valor)) {
                    datos.append("<c r=\"").append(letra(c)).append(actual).append("\" s=\"").append(ESTILO_IMPORTE)
                            .append("\"><v>").append(valor.trim()).append("</v></c>");
                } else {
                    datos.append(celdaTexto(c, actual, valor, ESTILO_NORMAL));
                }
            }
            datos.append("</row>");
        }
        int ultimaFila = fila - 1;
        if (!tabla.resumen().isEmpty()) {
            fila++;
            for (String[] par : tabla.resumen()) {
                int actual = fila++;
                datos.append("<row r=\"").append(actual).append("\">")
                        .append(celdaTexto(Math.max(0, columnas - 2), actual, par[0], ESTILO_TOTAL_TEXTO));
                String numero = par[1].replaceAll("[^0-9.\\-]", "");
                if (esNumero(numero)) {
                    datos.append("<c r=\"").append(letra(columnas - 1)).append(actual).append("\" s=\"")
                            .append(ESTILO_TOTAL_IMPORTE).append("\"><v>").append(numero).append("</v></c>");
                } else {
                    datos.append(celdaTexto(columnas - 1, actual, par[1], ESTILO_TOTAL_TEXTO));
                }
                datos.append("</row>");
            }
        }

        xml.append("<sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"1\"><pane ySplit=\"")
                .append(filaEncabezado).append("\" topLeftCell=\"A").append(filaEncabezado + 1)
                .append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        xml.append("<cols>");
        double suma = 0;
        for (double p : tabla.pesos()) {
            suma += p;
        }
        for (int c = 0; c < columnas; c++) {
            double ancho = Math.max(10, Math.min(60, tabla.pesos()[c] / suma * columnas * 16));
            xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
                    .append(String.format(Locale.ROOT, "%.1f", ancho)).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>").append(datos).append("</sheetData>");
        if (ultimaFila > filaEncabezado) {
            xml.append("<autoFilter ref=\"A").append(filaEncabezado).append(':').append(letra(columnas - 1))
                    .append(ultimaFila).append("\"/>");
        }
        return xml.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
                .append("<pageSetup orientation=\"landscape\" paperSize=\"9\" fitToHeight=\"0\"/></worksheet>").toString();
    }

    private static String filaTexto(int fila, String texto, int estilo) {
        return "<row r=\"" + fila + "\">" + celdaTexto(0, fila, texto, estilo) + "</row>";
    }

    private static String celdaTexto(int columna, int fila, String texto, int estilo) {
        return "<c r=\"" + letra(columna) + fila + "\" t=\"inlineStr\" s=\"" + estilo + "\"><is><t xml:space=\"preserve\">"
                + escapar(texto) + "</t></is></c>";
    }

    private static boolean esNumero(String valor) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        try {
            Double.parseDouble(valor.trim());
            return true;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static String letra(int columna) {
        StringBuilder sb = new StringBuilder();
        int n = columna;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : (texto == null ? "" : texto).toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (c >= 32 || c == '\n' || c == '\t') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String LIBRO = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<sheets><sheet name=\"Caja\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    private static final String RELS_LIBRO = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    /** 0 normal, 1 encabezado, 2 importe, 3 titulo, 4 texto de total, 5 importe de total. */
    private static final String ESTILOS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"#,##0.00\"/></numFmts>"
            + "<fonts count=\"4\">"
            + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"14\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "</fonts>"
            + "<fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF6B4A1E\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF5E6C8\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"6\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
            + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
            + "<xf numFmtId=\"164\" fontId=\"3\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyNumberFormat=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}

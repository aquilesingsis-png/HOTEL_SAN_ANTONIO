package untrm.hotel_san_antonio.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PDF A4 de texto (para un comprobante), sin depender de una impresora ni de bibliotecas externas. */
public final class PdfTexto {

    private PdfTexto() {
    }

    /** Escribe el texto (una linea por salto de linea) en paginas A4 de 48 lineas. */
    public static byte[] crear(String texto) throws IOException {
        Charset codificacion = Charset.forName("windows-1252");
        List<String> lineas = new ArrayList<>();
        for (String linea : texto.split("\\R", -1)) {
            String restante = linea;
            while (restante.length() > 82) {
                int corte = restante.lastIndexOf(' ', 82);
                if (corte < 20) corte = 82;
                lineas.add(restante.substring(0, corte));
                restante = restante.substring(corte).stripLeading();
            }
            lineas.add(restante);
        }
        int paginas = Math.max(1, (lineas.size() + 47) / 48);
        String[] objetos = new String[4 + paginas * 2];
        objetos[1] = "<< /Type /Catalog /Pages 2 0 R >>";
        objetos[3] = "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>";
        StringBuilder hijos = new StringBuilder();
        for (int pagina = 0; pagina < paginas; pagina++) {
            int idPagina = 4 + pagina * 2;
            int idContenido = idPagina + 1;
            hijos.append(idPagina).append(" 0 R ");
            objetos[idPagina] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + idContenido + " 0 R >>";
            StringBuilder contenido = new StringBuilder("BT /F1 11 Tf 48 790 Td 15 TL\n");
            int fin = Math.min(lineas.size(), (pagina + 1) * 48);
            for (int i = pagina * 48; i < fin; i++) {
                String limpia = lineas.get(i).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
                contenido.append('(').append(limpia).append(") Tj T*\n");
            }
            contenido.append("ET\n");
            byte[] bytesContenido = contenido.toString().getBytes(codificacion);
            objetos[idContenido] = "<< /Length " + bytesContenido.length + " >>\nstream\n"
                    + contenido + "endstream";
        }
        objetos[2] = "<< /Type /Pages /Kids [ " + hijos + "] /Count " + paginas + " >>";
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write("%PDF-1.4\n".getBytes(codificacion));
        int[] posiciones = new int[objetos.length];
        for (int i = 1; i < objetos.length; i++) {
            posiciones[i] = salida.size();
            salida.write((i + " 0 obj\n" + objetos[i] + "\nendobj\n").getBytes(codificacion));
        }
        int inicioIndice = salida.size();
        salida.write(("xref\n0 " + objetos.length + "\n0000000000 65535 f \n").getBytes(codificacion));
        for (int i = 1; i < objetos.length; i++) {
            salida.write(String.format(Locale.ROOT, "%010d 00000 n \n", posiciones[i]).getBytes(codificacion));
        }
        salida.write(("trailer\n<< /Size " + objetos.length + " /Root 1 0 R >>\nstartxref\n"
                + inicioIndice + "\n%%EOF\n").getBytes(codificacion));
        return salida.toByteArray();
    }
}

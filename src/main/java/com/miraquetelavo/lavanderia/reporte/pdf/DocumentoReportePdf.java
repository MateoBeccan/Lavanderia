package com.miraquetelavo.lavanderia.reporte.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

final class DocumentoReportePdf implements AutoCloseable {
    record Columna(String titulo, float ancho, boolean importe) {}

    private static final float MARGEN = 36;
    private static final float ANCHO = PDRectangle.A4.getWidth() - 2 * MARGEN;
    private static final float TAM_FILA = 9;
    private static final float INTERLINEADO = 12;
    private static final Color AZUL = new Color(13, 49, 91);
    private static final Color TINTA = new Color(27, 38, 52);
    private static final Color GRIS = new Color(66, 82, 101);
    private static final Color LINEA = new Color(213, 223, 234);
    private static final Color CLARO = new Color(241, 246, 251);
    private static final Charset LATINO = Charset.forName("windows-1252");

    private final PDDocument documento = new PDDocument();
    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final long vence;
    private final String titulo;
    private final Columna[] columnas;
    private PDImageXObject logo;
    private PDPageContentStream contenido;
    private float y;
    private int filas;

    DocumentoReportePdf(String titulo, Columna[] columnas, long vence) {
        this.titulo = titulo;
        this.columnas = columnas;
        this.vence = vence;
    }

    void iniciar(String generado, String periodo, String filtros, List<String> resumen) throws IOException {
        try (var imagen = DocumentoReportePdf.class.getResourceAsStream("/reportes/logo-lavanderia.png")) {
            if (imagen == null) {
                throw new IOException("Falta el logo de la lavandería.");
            }
            try (var entrada = new MemoryCacheImageInputStream(imagen)) {
                var lectores = ImageIO.getImageReaders(entrada);
                if (!lectores.hasNext()) {
                    throw new IOException("El logo de la lavandería no es una imagen válida.");
                }
                var lector = lectores.next();
                try {
                    lector.setInput(entrada);
                    int paso = Math.max(1, Math.max(lector.getWidth(0), lector.getHeight(0)) / 280);
                    var opciones = lector.getDefaultReadParam();
                    opciones.setSourceSubsampling(paso, paso, 0, 0);
                    logo = LosslessFactory.createFromImage(documento, lector.read(0, opciones));
                } finally {
                    lector.dispose();
                }
            }
        }
        documento.getDocumentInformation().setTitle(titulo + " | Mirá que te lavo");
        documento.getDocumentInformation().setAuthor("Mirá que te lavo");
        nuevaPagina();
        parrafo("Generado " + generado, regular, 9, GRIS);
        parrafo("Período consultado: " + periodo, regular, 10, TINTA);
        parrafo("Filtros aplicados: " + filtros, regular, 10, TINTA);
        y -= 10;
        rectangulo(MARGEN, y - 20 - resumen.size() * 15, ANCHO, 24 + resumen.size() * 15, CLARO);
        texto("RESUMEN", MARGEN + 10, y - 11, negrita, 9, AZUL);
        y -= 27;
        for (var indicador : resumen) {
            texto(indicador, MARGEN + 10, y, negrita, 10, TINTA);
            y -= 15;
        }
        y -= 2;
        encabezadoTabla();
    }

    void fila(String... celdas) throws IOException {
        verificarTiempo(vence);
        List<List<String>> lineas = new ArrayList<>(columnas.length);
        for (int i = 0; i < columnas.length; i++) {
            lineas.add(dividir(celdas[i], columnas[i].ancho() - 12, regular, TAM_FILA));
        }
        int alto = 8 + (int) INTERLINEADO * lineas.stream().mapToInt(List::size).max().orElseThrow();
        if (y - alto < 57) {
            nuevaPagina();
            encabezadoTabla();
        }
        if (y - alto < 57) {
            throw LimitesReportePdf.excedido("Una fila contiene demasiado texto para una página.");
        }
        rectangulo(MARGEN, y - alto, ANCHO, 0.5f, LINEA);
        if (filas++ % 2 != 0) {
            rectangulo(MARGEN, y - alto + 0.5f, ANCHO, alto - 0.5f, CLARO);
        }
        float x = MARGEN;
        for (int i = 0; i < columnas.length; i++) {
            var columna = columnas[i];
            float base = y - 13;
            for (var linea : lineas.get(i)) {
                float inicio = columna.importe()
                        ? x + columna.ancho() - 6 - ancho(linea, regular, TAM_FILA)
                        : x + 6;
                texto(linea, inicio, base, regular, TAM_FILA, TINTA);
                base -= INTERLINEADO;
            }
            x += columna.ancho();
        }
        y -= alto;
    }

    byte[] terminar() throws IOException {
        if (filas == 0) {
            y -= 20;
            parrafo("Sin registros para los filtros aplicados.", regular, 10, GRIS);
        }
        cerrarContenido();
        int total = documento.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            verificarTiempo(vence);
            try (var pie = new PDPageContentStream(documento, documento.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                contenido = pie;
                rectangulo(MARGEN, 45, ANCHO, 0.6f, LINEA);
                texto("Documento interno · Sin validez fiscal", MARGEN, 29, regular, 8, GRIS);
                String numero = "Página " + (i + 1) + " de " + total;
                texto(numero, MARGEN + ANCHO - ancho(numero, regular, 8), 29, regular, 8, GRIS);
            } finally {
                contenido = null;
            }
        }
        var salida = new ByteArrayOutputStream();
        documento.save(new OutputStream() {
            private void comprobar(int cantidad) {
                verificarTiempo(vence);
                if (cantidad > LimitesReportePdf.BYTES - salida.size()) {
                    throw LimitesReportePdf.excedido("El PDF supera los 8 MiB.");
                }
            }

            @Override
            public void write(int valor) {
                comprobar(1);
                salida.write(valor);
            }

            @Override
            public void write(byte[] datos, int inicio, int cantidad) {
                comprobar(cantidad);
                salida.write(datos, inicio, cantidad);
            }
        });
        return salida.toByteArray();
    }

    private void nuevaPagina() throws IOException {
        verificarTiempo(vence);
        if (documento.getNumberOfPages() >= LimitesReportePdf.PAGINAS) {
            throw LimitesReportePdf.excedido("El PDF supera las 160 páginas.");
        }
        cerrarContenido();
        var pagina = new PDPage(PDRectangle.A4);
        documento.addPage(pagina);
        contenido = new PDPageContentStream(documento, pagina);
        float superior = PDRectangle.A4.getHeight() - MARGEN;
        float escala = Math.min(64f / logo.getWidth(), 55f / logo.getHeight());
        contenido.drawImage(logo, MARGEN, superior - logo.getHeight() * escala,
                logo.getWidth() * escala, logo.getHeight() * escala);
        texto("MIRÁ QUE TE LAVO", MARGEN + 78, superior - 16, negrita, 10, AZUL);
        texto(titulo, MARGEN + 78, superior - 40, negrita, 21, TINTA);
        rectangulo(MARGEN, superior - 61, ANCHO, 1.5f, AZUL);
        y = superior - 77;
    }

    private void encabezadoTabla() throws IOException {
        rectangulo(MARGEN, y - 22, ANCHO, 22, AZUL);
        float x = MARGEN;
        for (var columna : columnas) {
            texto(columna.titulo(), x + 6, y - 14, negrita, 9, Color.WHITE);
            x += columna.ancho();
        }
        y -= 22;
    }

    private void parrafo(String valor, PDFont fuente, float tam, Color color) throws IOException {
        for (var linea : dividir(valor, ANCHO, fuente, tam)) {
            texto(linea, MARGEN, y, fuente, tam, color);
            y -= 15;
        }
    }

    private List<String> dividir(String valor, float limite, PDFont fuente, float tam) throws IOException {
        if (valor != null && valor.length() > 2_048) {
            throw LimitesReportePdf.excedido("Un campo supera los 2.048 caracteres admitidos por el PDF.");
        }
        var limpia = valor == null || valor.isBlank() ? "-" : Normalizer.normalize(valor, Normalizer.Form.NFC)
                .replaceAll("[\\p{Z}\\s]+", " ").replaceAll("\\p{C}", "").strip();
        String texto = new String(limpia.getBytes(LATINO), LATINO);
        List<String> lineas = new ArrayList<>();
        StringBuilder linea = new StringBuilder();
        float anchoLinea = 0;
        float espacio = ancho(" ", fuente, tam);
        for (String palabra : texto.split(" ")) {
            float anchoPalabra = ancho(palabra, fuente, tam);
            if (!linea.isEmpty() && anchoLinea + espacio + anchoPalabra > limite) {
                lineas.add(linea.toString());
                linea.setLength(0);
                anchoLinea = 0;
            }
            if (!linea.isEmpty()) {
                linea.append(' ');
                anchoLinea += espacio;
            }
            for (int i = 0; i < palabra.length(); i++) {
                String letra = palabra.substring(i, i + 1);
                float anchoLetra = ancho(letra, fuente, tam);
                if (!linea.isEmpty() && anchoLinea + anchoLetra > limite) {
                    lineas.add(linea.toString());
                    linea.setLength(0);
                    anchoLinea = 0;
                }
                linea.append(letra);
                anchoLinea += anchoLetra;
            }
        }
        lineas.add(linea.isEmpty() ? "-" : linea.toString());
        return lineas;
    }

    private float ancho(String texto, PDFont fuente, float tam) throws IOException {
        return fuente.getStringWidth(texto) / 1000 * tam;
    }

    private void texto(String valor, float x, float base, PDFont fuente, float tam, Color color) throws IOException {
        contenido.beginText();
        contenido.setNonStrokingColor(color);
        contenido.setFont(fuente, tam);
        contenido.newLineAtOffset(x, base);
        contenido.showText(valor);
        contenido.endText();
    }

    private void rectangulo(float x, float base, float ancho, float alto, Color color) throws IOException {
        contenido.setNonStrokingColor(color);
        contenido.addRect(x, base, ancho, alto);
        contenido.fill();
    }

    static void verificarTiempo(long vence) {
        if (System.nanoTime() - vence >= 0) {
            throw LimitesReportePdf.excedido("Se agotó el tiempo de generación.");
        }
    }

    private void cerrarContenido() throws IOException {
        if (contenido != null) {
            var actual = contenido;
            contenido = null;
            actual.close();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            cerrarContenido();
        } finally {
            documento.close();
        }
    }
}

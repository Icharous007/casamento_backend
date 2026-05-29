package br.com.casamento.guest.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@ApplicationScoped
public class QrCodeService {

    private static final int SIZE = 400;
    private static final String FORMAT = "PNG";

    /**
     * Generates a QR code PNG for the given URL content.
     * @param content URL or text to encode
     * @return PNG bytes
     */
    public byte[] generateQrCodePng(String content) {
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 2
        );

        try {
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, FORMAT, out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Falha ao gerar QR code: " + e.getMessage(), e);
        }
    }
}

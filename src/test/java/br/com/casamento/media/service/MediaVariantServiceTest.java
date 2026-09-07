package br.com.casamento.media.service;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the "ffmpeg: No such file or directory" bug: video poster
 * extraction must work via the embedded JavaCV/ffmpeg natives, with no OS-level
 * ffmpeg install required.
 */
class MediaVariantServiceTest {

    private final MediaVariantService service = new MediaVariantService();
    private Path videoFile;

    @AfterEach
    void cleanup() throws Exception {
        service.shutdown();
        if (videoFile != null) Files.deleteIfExists(videoFile);
    }

    @Test
    void generateVideoPosterExtractsRealFrameFromMp4() throws Exception {
        videoFile = createSyntheticMp4();

        Optional<MediaVariantService.Variants> variants = service.generateVideoPoster(videoFile);

        assertTrue(variants.isPresent(), "Expected a poster to be extracted from a valid MP4");
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(variants.get().thumbnail()));
        BufferedImage display = ImageIO.read(new ByteArrayInputStream(variants.get().display()));
        assertNotNull(thumb);
        assertNotNull(display);
        assertTrue(thumb.getWidth() <= 400 && thumb.getHeight() <= 400);
    }

    private Path createSyntheticMp4() throws Exception {
        Path file = Files.createTempFile("variant-test-", ".mp4");
        int width = 320;
        int height = 240;
        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(file.toFile(), width, height)) {
            recorder.setFormat("mp4");
            recorder.setVideoCodecName("mpeg4"); // built-in codec, no extra native libs required
            recorder.setFrameRate(10);
            recorder.start();
            Java2DFrameConverter converter = new Java2DFrameConverter();
            for (int i = 0; i < 20; i++) {
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = img.createGraphics();
                g.setColor(i < 5 ? Color.BLACK : new Color(30, 144, 255));
                g.fillRect(0, 0, width, height);
                g.dispose();
                recorder.record(converter.convert(img));
            }
            recorder.stop();
        }
        return file;
    }
}

package br.com.casamento.media.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

/**
 * Runs against a packaged JRE: photo path, content type, and optional video path or --video.
 */
public class MediaVariantRuntimeCheck {
    public static void main(String[] args) throws Exception {
        MediaVariantService service = new MediaVariantService();
        try {
            verify(service.generatePhotoVariants(Path.of(args[0]), args[1]).orElseThrow(
                    () -> new IllegalStateException("Photo conversion failed")));
            if (args.length > 2 && "--video".equals(args[2])) {
                Path video = Files.createTempFile("runtime-video-", ".mp4");
                try {
                    try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(video.toFile(), 64, 48);
                         Java2DFrameConverter converter = new Java2DFrameConverter()) {
                        recorder.setFormat("mp4");
                        recorder.setVideoCodecName("mpeg4");
                        recorder.setFrameRate(10);
                        recorder.start();
                        BufferedImage frame = new BufferedImage(64, 48, BufferedImage.TYPE_3BYTE_BGR);
                        for (int i = 0; i < 10; i++) recorder.record(converter.convert(frame));
                    }
                    verify(service.generateVideoPoster(video).orElseThrow(
                            () -> new IllegalStateException("Video poster extraction failed")));
                } finally {
                    Files.deleteIfExists(video);
                }
            } else if (args.length > 2) {
                verify(service.generateVideoPoster(Path.of(args[2])).orElseThrow(
                        () -> new IllegalStateException("Video poster extraction failed")));
            }
        } finally {
            service.shutdown();
        }
    }

    private static void verify(MediaVariantService.Variants variants) throws Exception {
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(variants.thumbnail()));
        BufferedImage display = ImageIO.read(new ByteArrayInputStream(variants.display()));
        if (thumb == null || display == null || Math.max(thumb.getWidth(), thumb.getHeight()) > 400
                || Math.max(display.getWidth(), display.getHeight()) > 1600) {
            throw new IllegalStateException("Invalid JPEG variants");
        }
        System.out.printf("JPEG variants OK: thumbnail=%dx%d display=%dx%d%n",
                thumb.getWidth(), thumb.getHeight(), display.getWidth(), display.getHeight());
    }
}

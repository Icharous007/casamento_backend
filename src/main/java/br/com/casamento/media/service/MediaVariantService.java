package br.com.casamento.media.service;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.jboss.logging.Logger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Generates lightweight JPEG variants (thumb + display) for gallery media so the
 * frontend never has to download full-resolution originals for grid/viewer rendering.
 * Photos are resized with pure-Java ImageIO (no external dependency). Video posters use
 * JavaCV's embedded ffmpeg natives (no OS install required). HEIC photos fall back to the
 * same bundled ffmpeg binary on a best-effort basis; any failure degrades gracefully
 * (caller keeps serving the original file).
 */
@ApplicationScoped
public class MediaVariantService {

    private static final Logger LOG = Logger.getLogger(MediaVariantService.class);

    static {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR); // silence ffmpeg's verbose native logging
    }

    private static final int THUMB_MAX_DIMENSION = 400;
    private static final int DISPLAY_MAX_DIMENSION = 1600;
    private static final float THUMB_QUALITY = 0.75f;
    private static final float DISPLAY_QUALITY = 0.82f;
    private static final long POSTER_SEEK_MICROS = 500_000L; // ~0.5s in, avoids a black first frame
    private static final Set<String> HEIC_TYPES = Set.of("image/heic", "image/heif");

    private final ExecutorService videoExecutor = Executors.newCachedThreadPool();

    @PreDestroy
    void shutdown() {
        videoExecutor.shutdownNow();
    }

    public record Variants(byte[] thumbnail, byte[] display) {
    }

    public Optional<Variants> generatePhotoVariants(Path filePath, String contentType) {
        try {
            BufferedImage original = readAndOrient(filePath);
            if (original == null && HEIC_TYPES.contains(contentType)) {
                original = readViaFfmpegConversion(filePath);
            }
            if (original == null) {
                return Optional.empty();
            }
            byte[] thumb = resizeToJpeg(original, THUMB_MAX_DIMENSION, THUMB_QUALITY);
            byte[] display = resizeToJpeg(original, DISPLAY_MAX_DIMENSION, DISPLAY_QUALITY);
            return Optional.of(new Variants(thumb, display));
        } catch (Exception e) {
            LOG.warnf(e, "Failed to generate photo variants for %s", filePath);
            return Optional.empty();
        }
    }

    public Optional<Variants> generateVideoPoster(Path filePath) {
        try {
            Future<BufferedImage> future = videoExecutor.submit(() -> grabPosterFrame(filePath));
            BufferedImage poster = future.get(15, TimeUnit.SECONDS);
            if (poster == null) {
                return Optional.empty();
            }
            byte[] thumb = resizeToJpeg(poster, THUMB_MAX_DIMENSION, THUMB_QUALITY);
            byte[] display = resizeToJpeg(poster, DISPLAY_MAX_DIMENSION, DISPLAY_QUALITY);
            return Optional.of(new Variants(thumb, display));
        } catch (TimeoutException e) {
            LOG.warnf("Video poster extraction timed out for %s", filePath);
            return Optional.empty();
        } catch (Exception e) {
            LOG.warnf(e, "Failed to generate video poster for %s", filePath);
            return Optional.empty();
        }
    }

    private BufferedImage grabPosterFrame(Path filePath) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(filePath.toFile())) {
            grabber.start();
            long duration = grabber.getLengthInTime();
            if (duration > 0) {
                grabber.setTimestamp(Math.min(POSTER_SEEK_MICROS, duration - 1));
            }
            Frame frame = grabber.grabImage();
            if (frame == null || frame.image == null) {
                return null;
            }
            return new Java2DFrameConverter().convert(frame);
        }
    }

    private BufferedImage readViaFfmpegConversion(Path filePath) {
        Path converted = null;
        try {
            converted = Files.createTempFile("heic-", ".jpg");
            boolean ok = runFfmpeg(List.of(resolveFfmpegBinary(), "-y", "-i", filePath.toString(), converted.toString()), 20);
            if (!ok || Files.size(converted) == 0) {
                return null;
            }
            return ImageIO.read(converted.toFile());
        } catch (Exception e) {
            LOG.debugf(e, "ffmpeg HEIC conversion unavailable for %s", filePath);
            return null;
        } finally {
            deleteQuietly(converted);
        }
    }

    private String resolveFfmpegBinary() {
        try {
            return Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
        } catch (Exception e) {
            return "ffmpeg"; // fall back to PATH lookup, best effort only
        }
    }

    private boolean runFfmpeg(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }
        return process.exitValue() == 0;
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    // ── Resize ────────────────────────────────────────────────────────────────

    private byte[] resizeToJpeg(BufferedImage src, int maxDimension, float quality) throws IOException {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) maxDimension / Math.max(w, h));
        int targetW = Math.max(1, (int) Math.round(w * scale));
        int targetH = Math.max(1, (int) Math.round(h * scale));

        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, targetW, targetH);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(resized, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // ── EXIF orientation ─────────────────────────────────────────────────────

    private BufferedImage readAndOrient(Path filePath) throws IOException {
        BufferedImage img = ImageIO.read(filePath.toFile());
        if (img == null) {
            return null;
        }
        int orientation = readExifOrientation(filePath);
        return applyOrientation(img, orientation);
    }

    private int readExifOrientation(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] header = is.readNBytes(128 * 1024);
            if (header.length < 4 || (header[0] & 0xFF) != 0xFF || (header[1] & 0xFF) != 0xD8) {
                return 1; // not a JPEG — no EXIF orientation concept
            }
            int offset = 2;
            while (offset + 4 <= header.length) {
                int marker = ((header[offset] & 0xFF) << 8) | (header[offset + 1] & 0xFF);
                if ((marker & 0xFF00) != 0xFF00) break;
                int segLength = ((header[offset + 2] & 0xFF) << 8) | (header[offset + 3] & 0xFF);
                if (marker == 0xFFE1) {
                    int segStart = offset + 4;
                    if (segStart + 6 <= header.length
                            && header[segStart] == 'E' && header[segStart + 1] == 'x'
                            && header[segStart + 2] == 'i' && header[segStart + 3] == 'f') {
                        return parseTiffOrientation(header, segStart + 6);
                    }
                }
                if (marker == 0xFFDA) break; // start of scan — no more markers
                offset += 2 + segLength;
            }
            return 1;
        } catch (IOException e) {
            return 1;
        }
    }

    private int parseTiffOrientation(byte[] data, int tiffStart) {
        if (tiffStart + 8 > data.length) return 1;
        boolean littleEndian = data[tiffStart] == 'I' && data[tiffStart + 1] == 'I';
        int ifdOffset = readInt32(data, tiffStart + 4, littleEndian);
        int ifdStart = tiffStart + ifdOffset;
        if (ifdStart + 2 > data.length) return 1;
        int entryCount = readInt16(data, ifdStart, littleEndian);
        for (int i = 0; i < entryCount; i++) {
            int entryOffset = ifdStart + 2 + i * 12;
            if (entryOffset + 12 > data.length) break;
            int tag = readInt16(data, entryOffset, littleEndian);
            if (tag == 0x0112) {
                return readInt16(data, entryOffset + 8, littleEndian);
            }
        }
        return 1;
    }

    private int readInt16(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return littleEndian ? (b1 << 8) | b0 : (b0 << 8) | b1;
    }

    private int readInt32(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF, b1 = data[offset + 1] & 0xFF,
                b2 = data[offset + 2] & 0xFF, b3 = data[offset + 3] & 0xFF;
        return littleEndian
                ? (b3 << 24) | (b2 << 16) | (b1 << 8) | b0
                : (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private BufferedImage applyOrientation(BufferedImage img, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        boolean swapDims = orientation >= 5;
        BufferedImage rotated = new BufferedImage(swapDims ? h : w, swapDims ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotated.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rotated.getWidth(), rotated.getHeight());
        AffineTransform t = new AffineTransform();
        switch (orientation) {
            case 2 -> {
                t.concatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
                t.concatenate(AffineTransform.getTranslateInstance(-w, 0));
            }
            case 3 -> {
                t.concatenate(AffineTransform.getTranslateInstance(w, h));
                t.concatenate(AffineTransform.getRotateInstance(Math.PI));
            }
            case 4 -> {
                t.concatenate(AffineTransform.getScaleInstance(1.0, -1.0));
                t.concatenate(AffineTransform.getTranslateInstance(0, -h));
            }
            case 5 -> {
                t.concatenate(AffineTransform.getRotateInstance(Math.PI / 2));
                t.concatenate(AffineTransform.getScaleInstance(1.0, -1.0));
            }
            case 6 -> {
                t.concatenate(AffineTransform.getTranslateInstance(h, 0));
                t.concatenate(AffineTransform.getRotateInstance(Math.PI / 2));
            }
            case 7 -> {
                t.concatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
                t.concatenate(AffineTransform.getTranslateInstance(-h, 0));
                t.concatenate(AffineTransform.getTranslateInstance(0, w));
                t.concatenate(AffineTransform.getRotateInstance(3 * Math.PI / 2));
            }
            case 8 -> {
                t.concatenate(AffineTransform.getTranslateInstance(0, w));
                t.concatenate(AffineTransform.getRotateInstance(3 * Math.PI / 2));
            }
            default -> {
            }
        }
        g.drawImage(img, t, null);
        g.dispose();
        return rotated;
    }
}

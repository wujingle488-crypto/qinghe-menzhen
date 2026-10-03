package com.commerce.cs.server.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 问诊附件只按生成的编号存放，不接受调用方传来的路径。 */
@Service
public class MediaFileStore {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final Path root;

    public MediaFileStore() {
        this.root = Path.of("data", "uploads").toAbsolutePath().normalize();
    }

    public Stored saveImage(String contentType, byte[] bytes) throws IOException {
        String type = normalizeImage(contentType, bytes);
        return write(type, extension(type), bytes);
    }

    public Stored saveSpeech(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 44) {
            throw new IllegalArgumentException("录音太短");
        }
        return write("audio/wav", "wav", bytes);
    }

    public Stored require(String id) {
        String safe = safeId(id);
        try (var files = Files.list(root)) {
            Path found = files.filter(path -> path.getFileName().toString().startsWith(safe + "."))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("附件不存在，请重新上传"));
            String name = found.getFileName().toString();
            String ext = name.substring(name.lastIndexOf('.') + 1);
            return new Stored(safe, found, mime(ext));
        } catch (IOException ex) {
            throw new IllegalArgumentException("附件不存在，请重新上传");
        }
    }

    private Stored write(String mime, String ext, byte[] bytes) throws IOException {
        Files.createDirectories(root);
        String id = UUID.randomUUID().toString().replace("-", "");
        Path path = root.resolve(id + "." + ext).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("附件路径无效");
        }
        Files.write(path, bytes);
        return new Stored(id, path, mime);
    }

    private static String normalizeImage(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            throw new IllegalArgumentException("图片是空的");
        }
        if (bytes.length > 8 * 1024 * 1024) {
            throw new IllegalArgumentException("图片不能超过 8MB");
        }
        String sniffed = sniff(bytes);
        String type = sniffed != null ? sniffed : (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT));
        int semi = type.indexOf(';');
        if (semi > 0) {
            type = type.substring(0, semi).trim();
        }
        if (!IMAGE_TYPES.contains(type)) {
            throw new IllegalArgumentException("只接收 jpg、png、gif、webp");
        }
        return type;
    }

    private static String sniff(byte[] bytes) {
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes.length > 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String extension(String mime) {
        return switch (mime) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private static String mime(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "wav" -> "audio/wav";
            default -> "image/jpeg";
        };
    }

    private static String safeId(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) {
            throw new IllegalArgumentException("附件不存在，请重新上传");
        }
        return id;
    }

    public record Stored(String id, Path path, String mime) {
    }
}

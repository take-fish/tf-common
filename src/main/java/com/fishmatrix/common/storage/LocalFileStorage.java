package com.fishmatrix.common.storage;

import com.fishmatrix.common.error.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/**
 * 本地卷存储。文件名使用随机 UUID，原始文件名只用于提取受白名单约束的扩展名。
 */
public class LocalFileStorage implements FileStorage {

    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path root;
    private final long maxFileBytes;

    public LocalFileStorage(String baseDir, long maxFileBytes) {
        this.root = Paths.get(baseDir == null || baseDir.isBlank() ? "./data/files" : baseDir)
                .toAbsolutePath().normalize();
        this.maxFileBytes = maxFileBytes <= 0 ? 50L * 1024 * 1024 : maxFileBytes;
    }

    @Override
    public StoredFile store(InputStream input, String originalFilename, String contentType,
            String directory, long maxBytes) {
        if (input == null) throw ApiException.badRequest("没有收到文件内容");
        long limit = maxBytes <= 0 ? maxFileBytes : Math.min(maxBytes, maxFileBytes);
        Path targetDir = root.resolve(safeDirectory(directory));
        String extension = safeExtension(originalFilename);
        String storageKey = relativeKey(targetDir, extension);
        Path target = resolve(storageKey);
        Path temp = null;

        try {
            Files.createDirectories(targetDir);
            temp = Files.createTempFile(targetDir, ".upload-", ".part");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (OutputStream raw = Files.newOutputStream(temp);
                    DigestOutputStream output = new DigestOutputStream(raw, digest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > limit) {
                        throw ApiException.of(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                                com.fishmatrix.common.error.ErrorCodes.PAYLOAD_TOO_LARGE,
                                "文件超过大小限制");
                    }
                    output.write(buffer, 0, read);
                }
            }
            move(temp, target);
            temp = null;
            return new StoredFile(storageKey, total,
                    HexFormat.of().formatHex(digest.digest()),
                    contentType == null ? "application/octet-stream" : contentType);
        } catch (ApiException exception) {
            deleteQuietly(temp);
            throw exception;
        } catch (NoSuchAlgorithmException exception) {
            deleteQuietly(temp);
            throw ApiException.internal("文件校验失败", exception);
        } catch (IOException exception) {
            deleteQuietly(temp);
            throw ApiException.internal("文件保存失败", exception);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw ApiException.notFound("文件不存在");
        }
        return new FileSystemResource(path);
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            return Files.isRegularFile(resolve(storageKey));
        } catch (ApiException exception) {
            return false;
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // 清理失败不阻塞业务；由存储巡检任务兜底。
        }
    }

    public Path root() {
        return root;
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw ApiException.badRequest("文件标识为空");
        }
        Path path = root.resolve(storageKey).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw ApiException.forbidden("非法的文件路径");
        }
        return path;
    }

    private static String safeDirectory(String directory) {
        if (directory == null || directory.isBlank()) return "";
        String normalized = directory.replace('\\', '/');
        StringBuilder clean = new StringBuilder();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) continue;
            if (!segment.matches("[A-Za-z0-9_-]{1,64}")) {
                throw ApiException.badRequest("文件目录不合法");
            }
            if (!clean.isEmpty()) clean.append('/');
            clean.append(segment);
        }
        return clean.toString();
    }

    private static String safeExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,8}") ? "." + extension : "";
    }

    private String relativeKey(Path directory, String extension) {
        String filename = UUID.randomUUID() + extension;
        return root.relativize(directory.resolve(filename)).toString().replace('\\', '/');
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // ignore
        }
    }
}

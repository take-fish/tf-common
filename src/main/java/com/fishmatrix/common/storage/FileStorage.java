package com.fishmatrix.common.storage;

import java.io.InputStream;
import org.springframework.core.io.Resource;

/**
 * 文件存储抽象。默认实现写本地命名卷；后续可替换为 MinIO/S3 而不改业务代码。
 * 权限永远由数据库记录决定，不能依赖存储路径的可见性。
 */
public interface FileStorage {

    StoredFile store(InputStream input, String originalFilename, String contentType,
            String directory, long maxBytes);

    Resource load(String storageKey);

    boolean exists(String storageKey);

    void delete(String storageKey);
}

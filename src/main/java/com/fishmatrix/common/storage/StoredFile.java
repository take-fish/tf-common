package com.fishmatrix.common.storage;

/**
 * 已落盘文件的元数据。storageKey 是相对存储根目录的稳定标识，绝不由用户文件名拼出。
 */
public record StoredFile(
        String storageKey,
        long size,
        String sha256,
        String contentType) {
}

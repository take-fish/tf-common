package com.fishmatrix.common.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fm.storage")
public class FishStorageProperties {

    private boolean enabled = false;

    /** 本地存储根目录，生产由 Docker 命名卷挂载。 */
    private String baseDir = "./data/files";

    /** 单文件硬上限，业务层仍应做更严格的类型与大小校验。 */
    private long maxFileBytes = 50L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }
}

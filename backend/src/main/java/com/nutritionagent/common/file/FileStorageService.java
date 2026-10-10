package com.nutritionagent.common.file;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;
import com.nutritionagent.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 本地文件存储（PRD 业务约束：上传的图片必须落盘并可访问）。
 *
 * <p>两个刻意的设计：
 * <ul>
 *   <li><b>文件名一律重新生成</b>（UUID + 由魔数推导的扩展名），绝不使用用户提交的
 *       {@code originalFilename}。用户文件名可能含 {@code ../} 或 {@code ..\\}，
 *       直接拼路径就是目录穿越漏洞。</li>
 *   <li><b>按年月分目录</b>，避免单目录堆几十万个文件导致文件系统性能下降。</li>
 * </ul>
 */
@Component
public class FileStorageService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final Path root;
    private final String publicPrefix;

    public FileStorageService(AppProperties properties) {
        this.root = Path.of(properties.getStorage().getRoot()).toAbsolutePath().normalize();
        this.publicPrefix = properties.getStorage().getPublicPrefix();
    }

    /** 存储结果：{@code relativePath} 入库，{@code publicUrl} 返回给前端。 */
    public record Stored(String relativePath, String publicUrl, long size, String sha256) {
    }

    /**
     * 保存上传文件。
     *
     * @param file   已通过 {@link ImageValidator} 校验的文件
     * @param bucket 业务目录名，例如 {@code recognition} / {@code food}
     */
    public Stored store(MultipartFile file, String bucket, String extension) {
        LocalDate today = LocalDate.now();
        String relative = bucket + "/" + today.format(YEAR) + "/" + today.format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolveSafely(relative);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存文件失败：" + e.getMessage());
        }
        return new Stored(relative, publicUrlOf(relative), file.getSize(), sha256(file));
    }

    /** 相对路径 → 对外可访问的 URL。 */
    public String publicUrlOf(String relativePath) {
        String prefix = publicPrefix.endsWith("/") ? publicPrefix.substring(0, publicPrefix.length() - 1) : publicPrefix;
        return prefix + "/" + relativePath;
    }

    /**
     * 把相对路径解析为绝对路径，并<b>强制</b>它仍位于存储根目录内。
     *
     * <p>这是防目录穿越的最后一道闸：即使某个调用方传进了 {@code ../../etc/passwd}，
     * 这里也会拒绝，而不是去读那个文件。
     */
    public Path resolveSafely(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "文件路径不能为空");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            // 明确拒绝并记录，不静默改写路径——静默改写会掩盖攻击尝试
            throw new BusinessException(ErrorCode.FORBIDDEN, "非法的文件路径");
        }
        return resolved;
    }

    /** 文件是否存在（供文件访问接口区分 404 与 403）。 */
    public boolean exists(String relativePath) {
        return Files.isRegularFile(resolveSafely(relativePath));
    }

    public Path root() {
        return root;
    }

    private static String sha256(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            // 哈希只用于去重与审计，算不出来不影响主流程，返回 null 而不是让上传失败
            return null;
        }
    }
}

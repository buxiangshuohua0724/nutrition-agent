package com.nutritionagent.common.file;

import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 上传图片的校验（PRD 12.4 安全验收）。
 *
 * <p>关键是<b>不能只信扩展名或 Content-Type</b>：两者都是客户端可控的字符串，
 * 把 `evil.jsp` 改名成 `a.png` 并声明 `Content-Type: image/png` 就能骗过只看这两个的校验。
 * 因此这里读文件的<b>前若干字节</b>，按文件头魔数（magic number）判断真实格式。
 *
 * <p>大小上限 10MB 与允许的类型（jpeg/png/webp）是契约 4.5 的硬约束。
 */
@Component
public class ImageValidator {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");

    /** 识别结果。{@code mimeType} 与 {@code extension} 一律由魔数推导，不采信客户端声明。 */
    public record Detected(String mimeType, String extension) {
    }

    /**
     * 校验并识别真实格式。
     *
     * @throws BusinessException 文件为空/超大 → 41301；类型不支持或魔数不符 → 41501
     */
    public Detected validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择要上传的图片文件");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "图片大小不能超过 " + (MAX_SIZE_BYTES / 1024 / 1024) + "MB");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declared.isEmpty() && !ALLOWED_MIME.contains(declared)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "只支持 JPG、PNG、WebP 格式的图片");
        }

        byte[] head = readHead(file);
        Detected detected = detectByMagic(head);
        if (detected == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "文件内容不是有效的图片（或格式不受支持）");
        }
        return detected;
    }

    private byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(16);
        } catch (IOException e) {
            // 读不到内容通常意味着上传被中断，按参数问题返回而不是 500
            throw new BusinessException(ErrorCode.PARAM_INVALID, "读取上传文件失败：" + e.getMessage());
        }
    }

    /** 按魔数判定格式；不认识的返回 null（由调用方转成 41501）。 */
    static Detected detectByMagic(byte[] head) {
        if (head == null || head.length < 12) {
            return null;
        }
        // JPEG: FF D8 FF
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return new Detected("image/jpeg", "jpg");
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G'
                && (head[4] & 0xFF) == 0x0D && (head[5] & 0xFF) == 0x0A
                && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A) {
            return new Detected("image/png", "png");
        }
        // WebP: "RIFF" .... "WEBP"
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return new Detected("image/webp", "webp");
        }
        return null;
    }
}

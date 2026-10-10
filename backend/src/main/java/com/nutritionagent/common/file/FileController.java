package com.nutritionagent.common.file;

import com.nutritionagent.common.api.ApiPaths;
import com.nutritionagent.common.api.ErrorCode;
import com.nutritionagent.common.exception.BusinessException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 静态文件访问（契约 4.8）——食品图片与识别图片。
 *
 * <p>刻意<b>不用</b> Spring 的 {@code addResourceHandlers}：那条路径把整个存储目录
 * 暴露成静态根，安全边界依赖框架的路径规范化行为，一旦配置写宽（例如误配成
 * {@code file:./data/uploads/}）就很难发现。这里显式取路径、显式经
 * {@link FileStorageService#resolveSafely} 校验后再读，越界一律 40301。
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/files")
public class FileController {

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/**")
    public ResponseEntity<Resource> read(HttpServletRequest request) {
        String full = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String prefix = ApiPaths.V1 + "/files/";
        String relative = full != null && full.startsWith(prefix) ? full.substring(prefix.length()) : "";
        if (relative.isBlank()) {
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "文件不存在");
        }

        Path path = storage.resolveSafely(relative);
        if (!Files.isRegularFile(path)) {
            // 与"路径越界"不同：路径合法但文件不存在，返回 404 语义而不是 403
            throw new BusinessException(ErrorCode.FOOD_NOT_FOUND, "文件不存在");
        }

        MediaType mediaType = mediaTypeOf(relative);
        return ResponseEntity.ok()
                .contentType(mediaType)
                // 图片内容不可变（文件名含随机 UUID），可放心长缓存
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(new FileSystemResource(path));
    }

    private static MediaType mediaTypeOf(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}

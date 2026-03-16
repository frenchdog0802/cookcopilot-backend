package com.cookplanner.controller;

import com.cookplanner.common.ApiResponse;
import com.cookplanner.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final CloudinaryService cloudinaryService;

    @PostMapping("/image")
    @SuppressWarnings("unchecked")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadImage(@RequestParam("image") MultipartFile file) throws Exception {
        Map<String, Object> result = cloudinaryService.uploadImage(file);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "image_url", (String) result.get("secure_url"),
                "public_id", (String) result.get("public_id")
        )));
    }

    @DeleteMapping("/image/{publicId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteImage(@PathVariable String publicId) throws Exception {
        cloudinaryService.deleteImage(publicId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("message", "Image deleted")));
    }
}

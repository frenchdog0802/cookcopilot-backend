package com.lardermind.controller;

import com.lardermind.common.ApiResponse;
import com.lardermind.service.CloudinaryService;
import com.lardermind.dto.*;
import lombok.RequiredArgsConstructor;
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
    public ApiResponse<UploadImageResponse> uploadImage(@RequestParam("image") MultipartFile file) throws Exception {
        Map<String, Object> result = cloudinaryService.uploadImage(file);
        UploadImageResponse response = new UploadImageResponse(
                (String) result.get("secure_url"),
                (String) result.get("public_id")
        );
        return ApiResponse.success(response);
    }

    @DeleteMapping("/image/{publicId}")
    public ApiResponse<DeleteImageResponse> deleteImage(@PathVariable String publicId) throws Exception {
        cloudinaryService.deleteImage(publicId);
        return ApiResponse.success(new DeleteImageResponse("Image deleted"));
    }
}

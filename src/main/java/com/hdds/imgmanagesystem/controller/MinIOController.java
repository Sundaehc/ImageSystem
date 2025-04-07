package com.hdds.imgmanagesystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hdds.imgmanagesystem.common.BaseResponse;
import com.hdds.imgmanagesystem.common.ErrorCode;
import com.hdds.imgmanagesystem.common.ResultUtils;
import com.hdds.imgmanagesystem.exception.BusinessException;
import com.hdds.imgmanagesystem.model.dto.tag.DownLoadRequest;
import com.hdds.imgmanagesystem.model.entity.Images;
import com.hdds.imgmanagesystem.service.ImagesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/images")
@Slf4j
public class MinIOController {

    @Resource
    private ImagesService imagesService;

    @Resource(name = "uploadExecutorService")
    private ExecutorService executorService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "/upload")
    public BaseResponse<Images> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam("tagId") int tagId,
            @RequestParam("productId") String productId) {
        try {
            return ResultUtils.success(imagesService.uploadImage(file, tagId, productId));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "/batch/upload")
    public BaseResponse<List<Images>> uploadImages(@RequestPart("file") MultipartFile[] file,
                                                   @RequestParam("tagId") int tagId,
                                                   @RequestParam("productId") String productId) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<CompletableFuture<Images>> futures = Arrays.stream(file)
                .map(f -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return imagesService.uploadImage(f, tagId, productId);
                    } catch (Exception e) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR);
                    }
                }, executorService))
                .collect(Collectors.toList());

        List<Images> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        stopWatch.stop();
        log.info("上传图片耗时 {} 秒, 一共上传 {} 张图片", stopWatch.getTotalTimeSeconds(), results.size());
        return ResultUtils.success(results);
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadImage(String fileName) throws Exception {
        if (fileName == null || fileName.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<Images> queryWrapper = new QueryWrapper<Images>().eq("fileName", fileName);
        Images image = imagesService.getOne(queryWrapper);
        if (image == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return imagesService.downloadImage(fileName);
    }

    /**
     * 批量下载
     * @param downLoadRequest
     * @param response
     * @throws Exception
     */
    @PostMapping("/batch/download")
    public void batchDownload(@RequestBody DownLoadRequest downLoadRequest, HttpServletResponse response) throws Exception {
        String productId = downLoadRequest.getProductId();
        int tagId = downLoadRequest.getTagId();
        if (productId == null || productId.isEmpty() || tagId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            imagesService.batchDownload(tagId, productId ,response);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, e.getMessage());
        }
        stopWatch.stop();
        log.info("下载图片耗时 {} 秒", stopWatch.getTotalTimeSeconds());
    }
}

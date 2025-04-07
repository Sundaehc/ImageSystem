package com.hdds.imgmanagesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hdds.imgmanagesystem.common.ErrorCode;
import com.hdds.imgmanagesystem.config.MinioConfig;
import com.hdds.imgmanagesystem.exception.BusinessException;
import com.hdds.imgmanagesystem.mapper.ImagesMapper;
import com.hdds.imgmanagesystem.mapper.TagsMapper;
import com.hdds.imgmanagesystem.model.entity.Images;
import com.hdds.imgmanagesystem.model.entity.Tags;
import com.hdds.imgmanagesystem.service.ImagesService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.util.IOUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.util.*;

import java.util.concurrent.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * @author Administrator
 * @description 针对表【images】的数据库操作Service实现
 * @createDate 2025-03-24 17:36:56
 */
@Service
@Slf4j
public class ImagesServiceImpl extends ServiceImpl<ImagesMapper, Images>
        implements ImagesService {

    @Resource
    private ImagesMapper imagesMapper;

    @Resource
    private TagsMapper tagsMapper;

    @Resource
    private MinioClient minioClient;

    @Resource
    private MinioConfig minioConfig;

    @Override
    public List<Images> findByTagId(int tagId) {
        if (tagId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<Images> imagesQueryWrapper = new QueryWrapper<>();
        imagesQueryWrapper.eq("tagId", tagId);
        List<Images> images = imagesMapper.selectList(imagesQueryWrapper);
        return images;
    }

    /**
     * 图片上传
     *
     * @param file
     * @param tagId
     * @param productId
     * @return
     */
    @Override
    public Images uploadImage(MultipartFile file, int tagId, String productId) throws Exception {
        if (tagId <= 0 || productId == null || productId.equals("")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Tags tags = tagsMapper.selectById(tagId);
        if (tags == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 生成唯一对象名称
        String objectName = UUID.randomUUID() + "-" + file.getOriginalFilename();
        // 上传到Minio
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());

        // 如果之前已经存在修改之前的版本
        QueryWrapper<Images> queryWrapper = new QueryWrapper<Images>().eq("tagId", tagId).eq("productId", productId);
        List<Images> existImages = imagesMapper.selectList(queryWrapper);
        for (Images images : existImages) {
            images.setIsLatest(0);
            imagesMapper.updateById(images);
        }
        String originaName = file.getOriginalFilename();
        String sortOrder = removeImageExtension(originaName);
        Images image = new Images();
        image.setFileName(objectName);
        image.setProductId(productId);
        image.setTagId(tagId);
        image.setMinioPath(getPolicyUrl(objectName, Method.GET));
        image.setSortOrder(sortOrder);
        imagesMapper.insert(image);
        return image;
    }

    @Override
    public List<Images> batchUploadImage(List<MultipartFile> files, int tagId, String productId) throws Exception {
        List<Images> result = new ArrayList<>();
        for (MultipartFile file : files) {
            result.add(uploadImage(file, tagId, productId));
        }
        return result;
    }

    @Override
    public ResponseEntity<byte[]> downloadImage(String fileName) throws Exception {
        ResponseEntity<byte[]> responseEntity = null;
        InputStream in = null;
        ByteArrayOutputStream out = null;
        try {
            in = minioClient.getObject(GetObjectArgs.builder().bucket(minioConfig.getBucketName()).object(fileName).build());
            out = new ByteArrayOutputStream();
            IOUtils.copy(in, out);
            //封装返回值
            byte[] bytes = out.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            try {
                headers.add("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            headers.setContentLength(bytes.length);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setAccessControlExposeHeaders(Arrays.asList("*"));
            responseEntity = new ResponseEntity<byte[]>(bytes, headers, 200);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return responseEntity;
    }

    /**
     * 批量下载
     * @param tagId
     * @param productId
     * @param response
     * @throws Exception
     */
    @Override
    public void batchDownload(Integer tagId, String productId, HttpServletResponse response) throws Exception {
        if (tagId == null || tagId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的标签ID");
        }
        if (StringUtils.isBlank(productId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "商品货号不能为空");
        }
        // 验证标签是否存在
        Tags tag = tagsMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签不存在");
        }
        QueryWrapper<Images> queryWrapper = new QueryWrapper<Images>().eq("productId", productId);
        List<Images> exist = imagesMapper.selectList(queryWrapper);
        if (exist == null || exist.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "商品货号不存在");
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"images_" + System.currentTimeMillis() + ".zip\"");

        try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(response.getOutputStream(), 16384))) {
            // 查询图片列表 - 优化查询，只取需要的字段
            List<Images> images = imagesMapper.selectList(
                    new QueryWrapper<Images>()
                            .eq("tagId", tagId)
                            .eq("productId", productId)
                            .select("id", "fileName")
            );

            // 优化ZIP压缩设置 - 速度优先
            zipOut.setLevel(Deflater.BEST_SPEED);
            zipOut.setMethod(ZipOutputStream.DEFLATED);

            // 计算最佳线程数 - 考虑CPU和IO平衡
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int optimalThreads = Math.min(availableProcessors * 2, images.size());
            int maxThreads = Math.min(optimalThreads, 20); // 上限20个线程

            // 创建自定义线程池
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    maxThreads, maxThreads,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(images.size()),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            // 使用更大的缓冲区大小
            int bufferSize = 8 * 1024 * 1024; // 8MB

            // 使用CompletableFuture更好地处理结果和异常
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 创建一个共享的缓冲区重用池
            ArrayBlockingQueue<byte[]> bufferPool = new ArrayBlockingQueue<>(maxThreads);
            for (int i = 0; i < maxThreads; i++) {
                bufferPool.add(new byte[bufferSize]);
            }

            try {
                for (Images image : images) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        byte[] buffer = null;
                        try {
                            // 从池中获取缓冲区
                            buffer = bufferPool.take();

                            // 从MinIO下载并添加到ZIP
                            try (InputStream is = minioClient.getObject(
                                    GetObjectArgs.builder()
                                            .bucket(minioConfig.getBucketName())
                                            .object(image.getFileName())
                                            .build());
                                 BufferedInputStream bis = new BufferedInputStream(is, bufferSize)) {

                                ZipEntry zipEntry = new ZipEntry(image.getFileName());

                                // 同步访问ZipOutputStream
                                synchronized (zipOut) {
                                    zipOut.putNextEntry(zipEntry);

                                    int bytesRead;
                                    while ((bytesRead = bis.read(buffer)) != -1) {
                                        zipOut.write(buffer, 0, bytesRead);
                                    }

                                    zipOut.closeEntry();
                                }
                            }
                        } catch (Exception e) {
                            log.error("处理图片失败: {}", image.getFileName(), e);
                            throw new CompletionException(e);
                        } finally {
                            // 归还缓冲区到池中
                            if (buffer != null) {
                                bufferPool.offer(buffer);
                            }
                        }
                    }, executor);

                    futures.add(future);
                }

                // 等待所有任务完成，设置合理超时
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(30, TimeUnit.MINUTES);
            } finally {
                executor.shutdown();
            }

            zipOut.finish();
        }
    }


//    /**
//     * 获取Images
//     * @param tagId
//     * @param productId
//     * @return
//     */
//    private List<Images> getImages(Integer tagId, String productId) {
//        QueryWrapper<Images> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq("tagId", tagId)
//                .eq("productId", productId).select("id","fileName");
//
//        List<Images> images = imagesMapper.selectList(queryWrapper);
//        if (images == null || images.isEmpty()) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到相关图片");
//        }
//        return images;
//    }

    private void addToZip(ZipOutputStream zipOut, Images image) throws Exception {
        // 使用较大的缓冲区
        int bufferSize = 8 * 1024 * 1024; // 8MB buffer

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(image.getFileName())
                        .build());
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream, bufferSize)) {

            synchronized (zipOut) {
                ZipEntry zipEntry = new ZipEntry(image.getFileName());
                zipOut.putNextEntry(zipEntry);
            }

            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                synchronized (zipOut) {
                    zipOut.write(buffer, 0, bytesRead);
                }
            }
            synchronized (zipOut) {
                zipOut.closeEntry();
            }
        } catch (Exception e) {
            log.error("文件下载失败: {}", image.getFileName(), e);
            throw e;
        }
    }

    public String removeImageExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
            return filename.substring(0, filename.lastIndexOf("."));
        }

        if (filename.toLowerCase().endsWith(".png")) {
            return filename.substring(0, filename.lastIndexOf("."));
        }
        return filename;
    }

    /**
     * @param objectName 对象名称
     * @param method     方法
     * @Description 获取上传文件的url
     */
    public String getPolicyUrl(String objectName, Method method) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException e) {
            e.printStackTrace();
        } catch (InsufficientDataException e) {
            e.printStackTrace();
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidResponseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (XmlParserException e) {
            e.printStackTrace();
        } catch (ServerException e) {
            e.printStackTrace();
        }
        return null;
    }
}






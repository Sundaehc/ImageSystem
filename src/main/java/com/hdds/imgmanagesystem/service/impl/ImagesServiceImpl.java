package com.hdds.imgmanagesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hdds.imgmanagesystem.common.BaseResponse;
import com.hdds.imgmanagesystem.common.ErrorCode;
import com.hdds.imgmanagesystem.config.MinioConfig;
import com.hdds.imgmanagesystem.exception.BusinessException;
import com.hdds.imgmanagesystem.mapper.ImagesMapper;
import com.hdds.imgmanagesystem.mapper.TagsMapper;
import com.hdds.imgmanagesystem.model.entity.Images;
import com.hdds.imgmanagesystem.model.entity.Tags;
import com.hdds.imgmanagesystem.service.ImagesService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import io.minio.http.Method;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * @author Administrator
 * @description 针对表【images】的数据库操作Service实现
 * @createDate 2025-03-24 17:36:56
 */
@Service
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
        String sortOrder = originaName.replace(".jpg", "");
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
        if (exist == null || exist.size() == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"商品货号不存在");
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"images_" + System.currentTimeMillis() + ".zip\"");

        // 创建ZIP输出流
        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {
            // 查询图片列表
            List<Images> images = getImages(tagId, productId);

            // 遍历下载并压缩
            for (Images image : images) {
                addToZip(zipOut, image);
            }
            zipOut.finish();
        }
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

    private List<Images> getImages(Integer tagId, String productId) {
        QueryWrapper<Images> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("tagId", tagId)
                .eq("productId", productId);

        List<Images> images = imagesMapper.selectList(queryWrapper);
        if (images == null || images.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到相关图片");
        }
        return images;
    }

    private void addToZip(ZipOutputStream zipOut, Images image) throws Exception {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(image.getFileName())
                        .build())) {

            ZipEntry zipEntry = new ZipEntry(image.getFileName());
            zipOut.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                zipOut.write(buffer, 0, bytesRead);
            }
            zipOut.closeEntry();
        } catch (Exception e) {
            log.error("文件处理失败: {}" + image.getFileName(), e);
            throw new RuntimeException("文件处理失败: " + image.getFileName());
        }
    }

}





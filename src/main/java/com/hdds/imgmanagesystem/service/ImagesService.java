package com.hdds.imgmanagesystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hdds.imgmanagesystem.common.BaseResponse;
import com.hdds.imgmanagesystem.model.entity.Images;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;

/**
* @author Administrator
* @description 针对表【images】的数据库操作Service
* @createDate 2025-03-24 17:36:56
*/
public interface ImagesService extends IService<Images> {
        List<Images> findByTagId(int tagId);

        Images uploadImage (MultipartFile file, int tagId, String productId) throws Exception;

        List<Images> batchUploadImage (List<MultipartFile> files, int tagId, String productId) throws Exception;

        ResponseEntity<byte[]> downloadImage(String fileName) throws Exception;

        void batchDownload(Integer tagId, String productId, HttpServletResponse response) throws Exception;

}

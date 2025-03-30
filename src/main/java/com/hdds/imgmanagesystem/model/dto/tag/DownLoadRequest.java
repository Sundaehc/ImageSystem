package com.hdds.imgmanagesystem.model.dto.tag;

import lombok.Data;

import java.io.Serializable;

/**
 *  通用下载请求
 */
@Data
public class DownLoadRequest implements Serializable {

    private String productId;

    private Integer tagId;

    private static final long serialVersionUID = 1L;
}

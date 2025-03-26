package com.hdds.imgmanagesystem.model.dto.tag;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签创建请求
 */
@Data
public class TagAddRequest implements Serializable {

    private String tagName;

    private int parentId;

    private static final long serialVersionUID = 1L;
}

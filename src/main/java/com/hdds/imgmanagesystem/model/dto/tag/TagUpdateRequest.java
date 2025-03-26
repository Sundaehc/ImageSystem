package com.hdds.imgmanagesystem.model.dto.tag;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签更新请求
 */
@Data
public class TagUpdateRequest implements Serializable {

    private int id;

    private String tagName;

    private int parentId;

    private static final long serialVersionUID = 1L;
}

package com.hdds.imgmanagesystem.model.dto.tag;

import com.hdds.imgmanagesystem.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 标签查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TagQueryRequest extends PageRequest implements Serializable {

    private String tagName;

    private static final long serialVersionUID = 1L;
}

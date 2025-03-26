package com.hdds.imgmanagesystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hdds.imgmanagesystem.model.dto.tag.TagDTO;
import com.hdds.imgmanagesystem.model.entity.Tags;

import java.util.List;

/**
* @author Administrator
* @description 针对表【tags】的数据库操作Service
* @createDate 2025-03-24 17:36:56
*/
public interface TagsService extends IService<Tags> {

    void validTags(Tags tags, Boolean add);

    List<TagDTO> getTagTree();
}

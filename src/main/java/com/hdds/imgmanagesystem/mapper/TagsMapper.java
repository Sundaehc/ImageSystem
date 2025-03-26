package com.hdds.imgmanagesystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hdds.imgmanagesystem.model.entity.Tags;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author Administrator
* @description 针对表【tags】的数据库操作Mapper
* @createDate 2025-03-24 17:29:41
* @Entity generator.entity.Tags
*/
public interface TagsMapper extends BaseMapper<Tags> {
    List<Tags> findRootTags();

    List<Tags> findChildTags(@Param("parentIds") List<Integer> parentIds);
}





package com.hdds.imgmanagesystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hdds.imgmanagesystem.common.ErrorCode;
import com.hdds.imgmanagesystem.exception.BusinessException;
import com.hdds.imgmanagesystem.mapper.TagsMapper;
import com.hdds.imgmanagesystem.model.dto.tag.TagDTO;
import com.hdds.imgmanagesystem.model.entity.Tags;
import com.hdds.imgmanagesystem.service.TagsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.xml.ws.ResponseWrapper;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author Administrator
* @description 针对表【tags】的数据库操作Service实现
* @createDate 2025-03-24 17:36:56
*/
@Service
public class TagsServiceImpl extends ServiceImpl<TagsMapper, Tags>
    implements TagsService {

    @Resource
    private TagsMapper tagsMapper;

    @Override
    public void validTags(Tags tags, Boolean add) {
        if ( tags == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String tag = tags.getTagName();
        if (add) {
            if (StringUtils.isEmpty(tag)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
        }
    }

    @Override
    public List<TagDTO> getTagTree() {
        List<Tags> rootTags = tagsMapper.findRootTags();
        // 2. 递归查询所有子节点
        Map<Integer, TagDTO> dtoMap = new HashMap<>();
        Queue<Tags> queue = new LinkedList<>(rootTags);

        while (!queue.isEmpty()) {
            Tags current = queue.poll();

            // 转换DTO
            TagDTO dto = convertToDTO(current);
            dtoMap.put(current.getId(), dto);

            // 查询子节点
            List<Tags> children = tagsMapper.findChildTags(
                    Collections.singletonList(current.getId())
            );

            children.forEach(child -> {
                TagDTO childDto = convertToDTO(child);
                dto.getChildren().add(childDto);
                dtoMap.put(child.getId(), childDto);
                queue.offer(child);
            });
        }

        // 3. 构建树结构
        return rootTags.stream()
                .map(t -> dtoMap.get(t.getId()))
                .collect(Collectors.toList());
    }

    private TagDTO convertToDTO(Tags tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setTagName(tag.getTagName());
        if (tag.getId() != null) {
            dto.setParentId(tag.getParentId());
        }
        return dto;
    }
}





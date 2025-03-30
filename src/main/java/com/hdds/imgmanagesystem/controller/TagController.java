package com.hdds.imgmanagesystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hdds.imgmanagesystem.common.BaseResponse;
import com.hdds.imgmanagesystem.common.DeleteRequest;
import com.hdds.imgmanagesystem.common.ErrorCode;
import com.hdds.imgmanagesystem.common.ResultUtils;
import com.hdds.imgmanagesystem.constant.CommonConstant;
import com.hdds.imgmanagesystem.exception.BusinessException;
import com.hdds.imgmanagesystem.mapper.TagsMapper;
import com.hdds.imgmanagesystem.model.dto.tag.TagAddRequest;
import com.hdds.imgmanagesystem.model.dto.tag.TagDTO;
import com.hdds.imgmanagesystem.model.dto.tag.TagQueryRequest;
import com.hdds.imgmanagesystem.model.dto.tag.TagUpdateRequest;
import com.hdds.imgmanagesystem.model.entity.Tags;
import com.hdds.imgmanagesystem.service.TagsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 标签接口
 */
@RestController
@RequestMapping("/tag")
@Slf4j
public class TagController {

    @Resource
    private TagsService tagService;


    // region 增删改查

    /**
     * 创建
     *
     * @param tagAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTag(@RequestBody TagAddRequest tagAddRequest, HttpServletRequest request) {
        if (tagAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Tags tags = new Tags();
        BeanUtils.copyProperties(tagAddRequest, tags);
        // 校验
        tagService.validTags(tags, true);
        boolean result = tagService.save(tags);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        long newTagsId = tags.getId();
        return ResultUtils.success(newTagsId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTags(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        Tags oldTags = tagService.getById(id);
        if (oldTags == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (oldTags.getParentId() != null && oldTags.getParentId() == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"请先删除子标签");
        }
        boolean result = tagService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 更新
     *
     * @param tagUpdateRequest
     * @return
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTags(@RequestBody TagUpdateRequest tagUpdateRequest) {
        if (tagUpdateRequest == null || tagUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Tags tags = new Tags();
        BeanUtils.copyProperties(tagUpdateRequest, tags);
        // 参数校验
        tagService.validTags(tags, false);
        long id = tagUpdateRequest.getId();
        // 判断是否存在
        Tags oldTags = tagService.getById(id);
        if (oldTags == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        int parentId = tagUpdateRequest.getParentId();
        Tags oldParentTags = tagService.getById(parentId);
        if (oldParentTags == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        boolean result = tagService.updateById(tags);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Tags> getTagsById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Tags tag = tagService.getById(id);
        return ResultUtils.success(tag);
    }

    /**
     * 获取列表（仅总管理员可使用）
     *
     * @param tagQueryRequest
     * @return
     */
    @GetMapping("/list")
    public BaseResponse<List<Tags>> listTags(TagQueryRequest tagQueryRequest) {
        Tags tagsQuery = new Tags();
        if (tagsQuery != null) {
            BeanUtils.copyProperties(tagQueryRequest, tagsQuery);
        }
        QueryWrapper<Tags> queryWrapper = new QueryWrapper<>(tagsQuery);
        List<Tags> tagsList = tagService.list(queryWrapper);
        return ResultUtils.success(tagsList);
    }

    /**
     * 分页获取列表
     *
     * @param tagQueryRequest
     * @param request
     * @return
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<Tags>> listTagsByPage(TagQueryRequest tagQueryRequest, HttpServletRequest request) {
        if (tagQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Tags tagQuery = new Tags();
        BeanUtils.copyProperties(tagQueryRequest, tagQuery);
        long current = tagQueryRequest.getCurrent();
        long size = tagQueryRequest.getPageSize();
        String sortField = tagQueryRequest.getSortField();
        String sortOrder = tagQueryRequest.getSortOrder();
        // 限制爬虫
        if (size > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<Tags> queryWrapper = new QueryWrapper<>(tagQuery);
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC), sortField);
        Page<Tags>tagsPage = tagService.page(new Page<>(current, size), queryWrapper);
        return ResultUtils.success(tagsPage);
    }


    @GetMapping("/tree")
    public BaseResponse<?> getTreeTags() {
        try {
            List<TagDTO> tagTree = tagService.getTagTree();
            return ResultUtils.success(tagTree);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
    }
}

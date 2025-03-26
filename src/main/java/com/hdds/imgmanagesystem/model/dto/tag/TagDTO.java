package com.hdds.imgmanagesystem.model.dto.tag;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
public class TagDTO {

    private Integer id;

    private String tagName;

    private Integer parentId;

    private Date createdAt;

    private List<TagDTO> children = new ArrayList<>();

    public void addChild(TagDTO child) {
        this.children.add(child);
    }
}

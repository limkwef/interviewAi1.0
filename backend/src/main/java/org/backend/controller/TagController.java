package org.backend.controller;

import org.backend.common.Result;
import org.backend.entity.Tag;
import org.backend.mapper.TagMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagMapper tagMapper;

    public TagController(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @GetMapping
    public Result<List<Tag>> list() {
        List<Tag> tags = tagMapper.findAll();
        return Result.success(tags);
    }
}

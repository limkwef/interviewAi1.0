package org.backend.controller;

import org.backend.common.Result;
import org.backend.entity.Resume;
import org.backend.vo.ResumeData;
import org.backend.service.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 简历管理接口
 */
@RestController
@RequestMapping("/api/resume")
public class ResumeController extends BaseController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * 上传简历文件（PDF/TXT）
     */
    @PostMapping("/upload")
    public Result<Resume> upload(HttpServletRequest request,
                                 @RequestParam("file") MultipartFile file) {
        Long userId = getUserIdFromToken(request);
        Resume resume = resumeService.uploadResume(userId, file);
        return Result.success("上传成功，正在解析", resume);
    }

    /**
     * 在线填写创建简历
     */
    @PostMapping("/create")
    public Result<Resume> create(HttpServletRequest request, @RequestBody ResumeData data) {
        Long userId = getUserIdFromToken(request);
        Resume resume = resumeService.createFromTemplate(userId, data);
        return Result.success("创建成功", resume);
    }

    /**
     * 简历列表
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(HttpServletRequest request,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = resumeService.getResumeList(userId, page, size);
        return Result.success(data);
    }

    /**
     * 简历详情
     */
    @GetMapping("/{id}")
    public Result<Resume> detail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        Resume resume = resumeService.getResumeDetail(userId, id);
        return Result.success(resume);
    }

    /**
     * 激活简历
     */
    @PutMapping("/{id}/activate")
    public Result<Void> activate(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        resumeService.activateResume(userId, id);
        return Result.success("激活成功", null);
    }

    /**
     * 删除简历
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        resumeService.deleteResume(userId, id);
        return Result.success("删除成功", null);
    }

    /**
     * 查询解析状态（轮询）
     */
    @GetMapping("/{id}/status")
    public Result<Map<String, Object>> status(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        Map<String, Object> data = resumeService.getParseStatus(userId, id);
        return Result.success(data);
    }

    /**
     * 重新解析简历
     */
    @PostMapping("/{id}/reparse")
    public Result<Void> reparse(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserIdFromToken(request);
        resumeService.reparseResume(userId, id);
        return Result.success("重新解析已启动", null);
    }
}

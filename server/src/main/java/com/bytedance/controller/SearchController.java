package com.bytedance.controller;

import com.bytedance.common.Result;
import com.bytedance.es.MessageDocument;
import com.bytedance.service.MessageSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Slf4j
public class SearchController {

    private final MessageSearchService messageSearchService;

    public SearchController(MessageSearchService messageSearchService) {
        this.messageSearchService = messageSearchService;
    }

    /**
     * 会话内消息全文搜索
     * GET /api/search/messages?conversationId=8&keyword=hello&page=0&size=20
     */
    @GetMapping("/messages")
    public Result<List<MessageDocument>> searchInConversation(
            @RequestParam Long conversationId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<MessageDocument> results = messageSearchService.search(conversationId, keyword.trim(), page, size);
            return Result.success(results);
        } catch (Exception e) {
            log.error("搜索服务异常", e);
            return Result.fail(50001, "搜索服务暂不可用");
        }
    }
}

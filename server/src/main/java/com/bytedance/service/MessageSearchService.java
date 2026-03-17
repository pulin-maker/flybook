package com.bytedance.service;

import com.bytedance.es.MessageDocument;
import com.bytedance.es.MessageDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final MessageDocumentRepository messageDocumentRepository;

    public MessageSearchService(ElasticsearchOperations elasticsearchOperations,
                                MessageDocumentRepository messageDocumentRepository) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.messageDocumentRepository = messageDocumentRepository;
    }

    /**
     * 索引一条新消息
     */
    public void indexMessage(MessageDocument doc) {
        messageDocumentRepository.save(doc);
    }

    /**
     * 更新消息（编辑场景）
     */
    public void updateMessage(Long messageId, String newContent) {
        messageDocumentRepository.findById(messageId).ifPresent(doc -> {
            doc.setIsEdited(true);
            doc.setEditedContent(newContent);
            messageDocumentRepository.save(doc);
        });
    }

    /**
     * 标记消息为已撤回
     */
    public void revokeMessage(Long messageId) {
        messageDocumentRepository.findById(messageId).ifPresent(doc -> {
            doc.setIsRevoked(true);
            doc.setContent("[已撤回]");
            messageDocumentRepository.save(doc);
        });
    }

    /**
     * 会话内全文搜索
     */
    public List<MessageDocument> search(Long conversationId, String keyword, int page, int size) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("conversationId", conversationId))
                .must(QueryBuilders.boolQuery()
                        .should(QueryBuilders.matchQuery("content", keyword))
                        .should(QueryBuilders.matchQuery("editedContent", keyword))
                        .minimumShouldMatch(1))
                .mustNot(QueryBuilders.termQuery("isRevoked", true));

        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime")))
                .build();

        SearchHits<MessageDocument> hits = elasticsearchOperations.search(searchQuery, MessageDocument.class);
        return hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }
}

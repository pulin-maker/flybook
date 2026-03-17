package com.bytedance.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MessageDocumentRepository extends ElasticsearchRepository<MessageDocument, Long> {
}

package com.bytedance.modules.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "flybook_messages", createIndex = true)
@Setting(shards = 2, replicas = 0)
public class MessageDocument {

    @Id
    private Long messageId;

    @Field(type = FieldType.Long)
    private Long conversationId;

    @Field(type = FieldType.Long)
    private Long senderId;

    @Field(type = FieldType.Long)
    private Long seq;

    @Field(type = FieldType.Integer)
    private Integer msgType;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "standard"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword, ignoreAbove = 256)
            }
    )
    private String content;

    @Field(type = FieldType.Keyword)
    private String mentions;

    @Field(type = FieldType.Long)
    private Long quoteId;

    @Field(type = FieldType.Boolean)
    private Boolean isRevoked;

    @Field(type = FieldType.Boolean)
    private Boolean isEdited;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String editedContent;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis,
            pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS||uuuu-MM-dd'T'HH:mm:ss||epoch_millis")
    private LocalDateTime createdTime;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis,
            pattern = "uuuu-MM-dd'T'HH:mm:ss.SSS||uuuu-MM-dd'T'HH:mm:ss||epoch_millis")
    private LocalDateTime editTime;
}

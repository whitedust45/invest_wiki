package com.personal.investment.platform.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyMapper {
  @Insert("""
      INSERT INTO platform_db.idempotency_record
        (idempotency_record_id, owner_user_id, http_method, canonical_path, idempotency_key,
         request_hash, status, processing_token, response_status, response_json, response_reference,
         created_at, updated_at)
      VALUES
        (#{idempotencyRecordId}, #{ownerUserId}, #{method}, #{path}, #{key}, #{requestHash},
         #{status}, NULL, NULL, NULL, NULL, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertProcessing(Row record);

  @Select("""
      SELECT idempotency_record_id AS idempotencyRecordId, owner_user_id AS ownerUserId,
             http_method AS method, canonical_path AS path, idempotency_key AS `key`,
             request_hash AS requestHash, status, response_status AS responseStatus,
             response_json AS responseJson
      FROM platform_db.idempotency_record
      WHERE owner_user_id = #{ownerUserId}
        AND http_method = #{method}
        AND canonical_path = #{path}
        AND idempotency_key = #{key}
      LIMIT 1
      """)
  Row find(@Param("ownerUserId") String ownerUserId, @Param("method") String method,
      @Param("path") String path, @Param("key") String key);

  @Update("""
      UPDATE platform_db.idempotency_record
      SET status = 'SUCCEEDED', response_status = #{responseStatus}, response_json = #{responseJson},
          updated_at = UTC_TIMESTAMP(3)
      WHERE idempotency_record_id = #{idempotencyRecordId}
        AND status = 'PROCESSING'
      """)
  int markSucceeded(@Param("idempotencyRecordId") String idempotencyRecordId,
      @Param("responseStatus") int responseStatus,
      @Param("responseJson") String responseJson);

  record Row(String idempotencyRecordId, String ownerUserId, String method, String path, String key,
             byte[] requestHash, String status, Integer responseStatus, String responseJson) {
  }
}

package com.personal.investment.identity.infrastructure;

import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdentityMapper {
  @Select("""
      SELECT u.user_id AS userId, u.permission_version AS permissionVersion
      FROM identity_db.iam_wechat_identity identity_record
      JOIN identity_db.iam_user u ON u.user_id = identity_record.user_id
      WHERE identity_record.openid_hmac = #{openidHmac}
        AND identity_record.status = 'ACTIVE'
        AND u.status = 'ACTIVE'
      LIMIT 1
      """)
  Optional<ExistingUser> findActiveUserByOpenIdHmac(@Param("openidHmac") byte[] openidHmac);

  @Select("SELECT COUNT(*) FROM identity_db.iam_user WHERE status = 'ACTIVE'")
  long countActiveAdministrators();

  @Select("SELECT GET_LOCK('investment:bootstrap-admin', 5)")
  int acquireBootstrapLock();

  @Select("SELECT RELEASE_LOCK('investment:bootstrap-admin')")
  int releaseBootstrapLock();

  @Insert("""
      INSERT INTO identity_db.iam_user
        (user_id, status, permission_version, created_at, updated_at, version)
      VALUES (#{userId}, 'ACTIVE', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 0)
      """)
  int insertUser(@Param("userId") String userId);

  @Insert("""
      INSERT INTO identity_db.iam_wechat_identity
        (wechat_identity_id, user_id, openid_hmac, hmac_key_version, status, created_at, updated_at)
      VALUES (#{wechatIdentityId}, #{userId}, #{openidHmac}, 1, 'ACTIVE', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
      """)
  int insertWechatIdentity(@Param("wechatIdentityId") String wechatIdentityId,
      @Param("userId") String userId, @Param("openidHmac") byte[] openidHmac);

  @Insert("""
      INSERT INTO identity_db.iam_login_audit
        (login_audit_id, user_id, wechat_identity_id, login_result, ip_hash, trace_id, failure_code, created_at)
      VALUES (#{auditId}, #{userId}, #{wechatIdentityId}, #{result}, NULL, #{traceId}, #{failureCode}, UTC_TIMESTAMP(3))
      """)
  int insertLoginAudit(@Param("auditId") String auditId, @Param("userId") String userId,
      @Param("wechatIdentityId") String wechatIdentityId, @Param("result") String result,
      @Param("traceId") String traceId, @Param("failureCode") String failureCode);

  record ExistingUser(String userId, long permissionVersion) {
  }
}

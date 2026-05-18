package com.fffg.cex.auth.mapper;

import com.fffg.cex.account.VO.AccountVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthAccountMapper {

    /**
     * 根据用户名查询账户（含密码哈希）
     */
    @Select("select id as accountId, username as userName, status, password_hash as passwordHash, " +
            "created_at as createdAt from account where username = #{username}")
    AccountWithPassword getAccountByUsername(String username);

    /**
     * 创建账户（含密码）
     */
    @Insert("insert into account (username, password_hash, status, created_at, updated_at) " +
            "values (#{username}, #{passwordHash}, 1, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createAccountWithPassword(CreateAccountWithPassword request);

    /**
     * 根据ID查询基础账户信息
     */
    @Select("select id as accountId, username as userName, status, created_at as createdAt " +
            "from account where id = #{id}")
    AccountVO getAccountById(Long id);

    /**
     * 更新密码
     */
    @Update("update account set password_hash = #{passwordHash}, updated_at = now() where id = #{accountId}")
    int updatePassword(Long accountId, String passwordHash);

    class AccountWithPassword extends AccountVO {
        private String passwordHash;

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }

    class CreateAccountWithPassword {
        private Long id;
        private String username;
        private String passwordHash;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}

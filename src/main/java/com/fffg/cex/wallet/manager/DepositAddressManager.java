package com.fffg.cex.wallet.manager;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充值地址管理器
 * <p>
 * 先用内存缓存模拟，后续可以建 deposit_address 表持久化。
 * 同一账户、同一币种、同一链的充值地址固定不变。
 */
@Slf4j
@Component
public class DepositAddressManager {

    private final ConcurrentHashMap<String, String> addressCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("充值地址管理器初始化完成（内存模式）");
    }

    /**
     * 获取或创建充值地址
     *
     * @param accountId   账户 ID
     * @param assetSymbol 币种
     * @param chain       链名称
     * @return 充值地址
     */
    public String getOrCreateAddress(Long accountId, String assetSymbol, String chain) {
        String key = buildKey(accountId, assetSymbol, chain);
        return addressCache.computeIfAbsent(key, k -> generateAddress(accountId, assetSymbol, chain));
    }

    /**
     * 生成充值地址
     * <p>
     * 模拟场景下按 accountId + assetSymbol + chain 进行 SHA-256 哈希取前 40 位作为地址。
     */
    private String generateAddress(Long accountId, String assetSymbol, String chain) {
        String raw = accountId + ":" + assetSymbol.toUpperCase() + ":" + chain.toUpperCase();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(raw.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String address = "0x" + hexString.substring(0, 40);
            log.debug("生成充值地址: accountId={}, assetSymbol={}, chain={}, address={}",
                    accountId, assetSymbol, chain, address);
            return address;
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            return "0x" + raw.hashCode();
        }
    }

    private String buildKey(Long accountId, String assetSymbol, String chain) {
        return accountId + ":" + assetSymbol.toUpperCase() + ":" + chain.toUpperCase();
    }
}

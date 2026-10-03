package com.commerce.cs.domain.entity;

import com.commerce.cs.domain.SessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "cs_session")
public class ChatSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "shop_id")
    private Long shopId;

    @Column(name = "shop_name", length = 128)
    private String shopName;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 256)
    private String productName;

    @Column(name = "product_image", length = 512)
    private String productImage;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "last_message", length = 500)
    private String lastMessage;

    @Column(name = "unread_merchant")
    private Integer unreadMerchant = 0;

    @Column(name = "unread_buyer")
    private Integer unreadBuyer = 0;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SessionStatus status;

    public Long getId() { return id; }
    public Long getBuyerId() { return buyerId; }
    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }
    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductImage() { return productImage; }
    public void setProductImage(String productImage) { this.productImage = productImage; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public int getUnreadMerchant() { return unreadMerchant == null ? 0 : unreadMerchant; }
    public void setUnreadMerchant(int unreadMerchant) { this.unreadMerchant = unreadMerchant; }
    public int getUnreadBuyer() { return unreadBuyer == null ? 0 : unreadBuyer; }
    public void setUnreadBuyer(int unreadBuyer) { this.unreadBuyer = unreadBuyer; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
}

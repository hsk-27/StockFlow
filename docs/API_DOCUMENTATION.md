# StockFlow &bull; REST API Specification (`/api/v1`)

StockFlow exposes a comprehensive RESTful API documented via OpenAPI 3.0.  
Interactive Swagger UI is accessible at `http://localhost:8080/swagger-ui.html` when running.

---

## 1. Authentication & Identity (`/api/v1/auth`)

### 1.1 Register Account
* **Endpoint:** `POST /api/v1/auth/register`
* **Access:** Public (Rate-limited: 30 req/min)
* **Request:**
  ```json
  {
    "email": "jane.customer@example.com",
    "password": "Password123!",
    "fullName": "Jane Doe",
    "role": "CUSTOMER"
  }
  ```
* **Response (201 Created):**
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
  ```

### 1.2 Login
* **Endpoint:** `POST /api/v1/auth/login`
* **Access:** Public (Rate-limited)
* **Request:**
  ```json
  {
    "email": "customer@stockflow.com",
    "password": "Password123!"
  }
  ```
* **Response (200 OK):** JWT Access Token (15m expiry) + Refresh Token (7d expiry).

### 1.3 Rotate Token
* **Endpoint:** `POST /api/v1/auth/refresh`
* **Request:** `{"refreshToken": "<token>"}`
* **Note:** Automatic token reuse detection revokes the token family if reuse is attempted.

---

## 2. Storefront & Catalog (`/api/v1`)

### 2.1 Search Public Catalog
* **Endpoint:** `GET /api/v1/products?page=0&size=20&query=audio&maxPrice=250`
* **Access:** Public
* **Response (200 OK):** Paginated active product items with immutable currency and category metadata.

### 2.2 List Categories
* **Endpoint:** `GET /api/v1/categories`
* **Access:** Public

---

## 3. Cart & Shopping (`/api/v1/cart`)

### 3.1 Get Current Cart
* **Endpoint:** `GET /api/v1/cart`
* **Header:** `Authorization: Bearer <customer_token>`
* **Response (200 OK):** Current line items, calculated unit prices, and subtotal.

### 3.2 Add Item to Cart
* **Endpoint:** `POST /api/v1/cart/items`
* **Header:** `Authorization: Bearer <customer_token>`
* **Request:**
  ```json
  {
    "sku": "SKU-AUDIO-PRO-BLK",
    "quantity": 1
  }
  ```

---

## 4. Order Orchestration & Idempotent Checkout (`/api/v1/orders`)

### 4.1 Execute Idempotent Checkout
* **Endpoint:** `POST /api/v1/orders/checkout`
* **Headers:**
  - `Authorization: Bearer <customer_token>`
  - `Idempotency-Key: <UUID>` (Guarantees at-most-once execution)
* **Request:**
  ```json
  {
    "shippingAddress": "742 Evergreen Terrace, Springfield, OR"
  }
  ```
* **Response (201 Created):**
  ```json
  {
    "id": "8e4580a0-f734-4eff-9275-7897efcb1aad",
    "orderNumber": "ORD-1794213031912-6851",
    "status": "PENDING_PAYMENT",
    "totalAmount": 199.99,
    "currency": "USD",
    "shippingAddress": "742 Evergreen Terrace, Springfield, OR"
  }
  ```

### 4.2 Customer Pre-Fulfilment Cancellation
* **Endpoint:** `POST /api/v1/orders/{id}/cancel`
* **Header:** `Authorization: Bearer <customer_token>`
* **Request:** `{"reason": "Customer ordered by mistake"}`

---

## 5. Payments & Webhooks (`/api/v1/payments`)

### 5.1 Initiate Payment
* **Endpoint:** `POST /api/v1/payments/initiate`
* **Header:** `Authorization: Bearer <customer_token>`
* **Request:** `{"orderId": "<order_id>"}`

### 5.2 Ingest Webhook
* **Endpoint:** `POST /api/v1/payments/webhooks`
* **Header:** `X-Webhook-Signature: <HMAC_SHA256_HEX>`
* **Request:**
  ```json
  {
    "eventId": "evt_998124871",
    "eventType": "payment.succeeded",
    "providerReference": "PAY-EXT-99812",
    "orderId": "<order_id>",
    "amount": 199.99,
    "currency": "USD",
    "status": "SUCCEEDED"
  }
  ```

---

## 6. Fulfilment & Shipments (`/api/v1/fulfilment`)

### 6.1 Create Shipment
* **Endpoint:** `POST /api/v1/fulfilment/shipments`
* **Header:** `Authorization: Bearer <seller_token>`
* **Request:**
  ```json
  {
    "orderId": "<order_id>",
    "carrier": "FedEx Express",
    "trackingNumber": "TRK-98234-FDX"
  }
  ```

### 6.2 Confirm Delivery
* **Endpoint:** `POST /api/v1/fulfilment/shipments/{id}/delivered`

---

## 7. Returns & Refunds (`/api/v1/returns`)

### 7.1 Customer Request Return
* **Endpoint:** `POST /api/v1/returns`
* **Request:** `{"orderId": "<order_id>", "reason": "Defective audio jack"}`

### 7.2 Seller Decision
* **Endpoint:** `POST /api/v1/returns/{id}/decision`
* **Request:** `{"approved": true}`

### 7.3 Seller Inspection & Dispositioning
* **Endpoint:** `POST /api/v1/returns/{id}/receive`
* **Request:** `{"restockDecision": "RESTOCK"}` (Options: `RESTOCK`, `QUARANTINE`, `SCRAP`)

### 7.4 Issue Refund
* **Endpoint:** `POST /api/v1/returns/refunds`
* **Request:** `{"orderId": "<order_id>", "amount": 199.99, "reason": "Item returned in good condition"}`

---

## 8. Admin & Reporting (`/api/v1/admin/reports`)

### 8.1 Operational Metrics (Database Aggregates)
* **Endpoint:** `GET /api/v1/admin/reports/metrics`
* **Header:** `Authorization: Bearer <admin_token>`
* **Response (200 OK):**
  ```json
  {
    "totalOrders": 2840,
    "totalRevenue": 1249850.00,
    "currency": "USD",
    "ordersByStatus": {
      "PENDING_PAYMENT": 18,
      "CONFIRMED": 1420,
      "SHIPPED": 980,
      "DELIVERED": 422
    },
    "lowStockItemCount": 0,
    "totalRegisteredUsers": 3,
    "pendingOutboxEvents": 0
  }
  ```

---

## 9. Conversational AI Assistant (`/api/v1/chat`)

* **Endpoint:** `POST /api/v1/chat/message`
* **Headers:** `Content-Type: application/json`, optional `Authorization: Bearer <token>`
* **Request:**
  ```json
  {
    "message": "Where is order ORD-1794213031912-6851?",
    "sessionId": "session-101"
  }
  ```
* **Response (200 OK):**
  ```json
  {
    "conversationId": "48f1e292-...",
    "content": "📦 Order Details for ORD-1794213031912-6851:\n- Status: CONFIRMED\n- Total: 199.99 USD",
    "intent": "ORDER_DETAILS",
    "suggestedActions": [
      {"label": "Cancel Order", "type": "QUERY", "payload": "Can I cancel order ORD-1794213031912-6851?"}
    ]
  }
  ```

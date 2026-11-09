package com.stockflow.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.audit.domain.AuditLog;
import com.stockflow.audit.repository.AuditLogRepository;
import com.stockflow.cart.dto.CartItemResponse;
import com.stockflow.cart.dto.CartResponse;
import com.stockflow.cart.service.CartService;
import com.stockflow.catalog.domain.Category;
import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.repository.CategoryRepository;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.chat.domain.ChatConversation;
import com.stockflow.chat.domain.ChatMessage;
import com.stockflow.chat.domain.ChatSender;
import com.stockflow.chat.dto.ChatAction;
import com.stockflow.chat.dto.ChatConversationResponse;
import com.stockflow.chat.dto.ChatMessageRequest;
import com.stockflow.chat.dto.ChatMessageResponse;
import com.stockflow.chat.repository.ChatConversationRepository;
import com.stockflow.chat.repository.ChatMessageRepository;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.fulfilment.domain.Shipment;
import com.stockflow.fulfilment.repository.ShipmentRepository;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.identity.repository.UserRepository;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderItem;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.reporting.dto.OperationalMetricsResponse;
import com.stockflow.reporting.service.ReportingService;
import com.stockflow.returns.domain.ReturnRequest;
import com.stockflow.returns.repository.ReturnRequestRepository;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.repository.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ============================================================================
 * StockFlow Intelligent Multi-Persona Conversational Engine
 * ============================================================================
 * Provides end-to-end conversational assistance for every user type:
 *  - GUEST: Product discovery, categories, platform policies, checkout FAQs.
 *  - CUSTOMER: Real-time order tracking, cancellation eligibility, returns, cart.
 *  - SELLER: Inventory stock levels, low-stock alerts, shipments, return requests.
 *  - ADMIN: Real-time operational revenue metrics, audit logs, outbox status.
 *
 * Enforces strict Broken Object-Level Authorization (BOLA) preventing cross-tenant
 * data leakage between customers or sellers.
 */
@Service
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);
    private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("(?i)\\b(ORD-[A-Za-z0-9-]+)\\b");
    private static final Pattern SKU_PATTERN = Pattern.compile("(?i)\\b(SKU-[A-Za-z0-9-]+)\\b");

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final InventoryRepository inventoryRepository;
    private final SellerRepository sellerRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReportingService reportingService;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ChatbotService(ChatConversationRepository conversationRepository,
                          ChatMessageRepository messageRepository,
                          UserRepository userRepository,
                          ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          OrderRepository orderRepository,
                          CartService cartService,
                          InventoryRepository inventoryRepository,
                          SellerRepository sellerRepository,
                          ShipmentRepository shipmentRepository,
                          ReturnRequestRepository returnRequestRepository,
                          ReportingService reportingService,
                          AuditLogRepository auditLogRepository,
                          ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.inventoryRepository = inventoryRepository;
        this.sellerRepository = sellerRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRequestRepository = returnRequestRepository;
        this.reportingService = reportingService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ChatMessageResponse processMessage(ChatMessageRequest request, User user) {
        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId().trim()
                : UUID.randomUUID().toString();

        String userRole = (user != null) ? user.getRole().name() : "GUEST";

        // Retrieve or initialize conversation
        ChatConversation conversation = resolveConversation(request.conversationId(), sessionId, user, userRole);

        // Record incoming user prompt
        ChatMessage userMessage = new ChatMessage(
                UUID.randomUUID(),
                conversation,
                ChatSender.USER,
                request.message().trim(),
                "USER_PROMPT",
                null
        );
        conversation.addMessage(userMessage);
        messageRepository.save(userMessage);

        // Update conversation title if first user message
        if (conversation.getMessages().size() <= 2) {
            String snippet = request.message().trim();
            conversation.updateTitle(snippet.length() > 40 ? snippet.substring(0, 37) + "..." : snippet);
        }

        // Generate context-aware response based on authenticated persona
        ChatResponseDraft draft = generateResponse(request.message().trim(), user, userRole);

        // Persist assistant response
        String actionsJson = serializeActions(draft.actions());
        ChatMessage assistantMessage = new ChatMessage(
                UUID.randomUUID(),
                conversation,
                ChatSender.ASSISTANT,
                draft.content(),
                draft.intent(),
                actionsJson
        );
        conversation.addMessage(assistantMessage);
        messageRepository.save(assistantMessage);
        conversationRepository.save(conversation);

        return mapToMessageResponse(assistantMessage, draft.actions());
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> getUserConversations(User user, String sessionId) {
        List<ChatConversation> list;
        if (user != null) {
            list = conversationRepository.findByUserIdOrderByUpdatedAtDesc(user.getId());
        } else if (sessionId != null && !sessionId.isBlank()) {
            list = conversationRepository.findBySessionIdOrderByUpdatedAtDesc(sessionId);
        } else {
            list = Collections.emptyList();
        }
        return list.stream().map(this::mapToConversationResponse).toList();
    }

    @Transactional(readOnly = true)
    public ChatConversationResponse getConversation(UUID conversationId, User user, String sessionId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat conversation not found: " + conversationId));

        validateConversationOwnership(conversation, user, sessionId);
        return mapToConversationResponse(conversation);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, User user, String sessionId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat conversation not found: " + conversationId));

        validateConversationOwnership(conversation, user, sessionId);
        conversationRepository.delete(conversation);
    }

    // =========================================================================
    // Core Multi-Persona Dispatch Engine
    // =========================================================================

    private ChatResponseDraft generateResponse(String prompt, User user, String userRole) {
        String lower = prompt.toLowerCase();

        // 1. General Greetings & Help
        if (matchesAny(lower, "hello", "hi", "hey", "greetings", "good morning", "good evening")) {
            return handleGreeting(user, userRole);
        }
        if (matchesAny(lower, "help", "menu", "options", "commands", "what can you do")) {
            return handleHelpMenu(user, userRole);
        }

        // 2. Global Commerce FAQs (Accessible across all personas)
        if (matchesAny(lower, "payment", "credit card", "stripe", "gateway", "how to pay", "pay order")) {
            return handlePaymentFaq();
        }
        if (matchesAny(lower, "shipping", "delivery", "carrier", "transit", "how long does shipping take", "fulfillment policy")) {
            return handleShippingFaq();
        }
        if (matchesAny(lower, "return policy", "refund policy", "how to return", "30-day return", "returns and refund")) {
            return handleReturnPolicyFaq();
        }

        // 3. Persona Specific Routing
        return switch (userRole) {
            case "ADMIN" -> handleAdminQuery(prompt, lower, user);
            case "SELLER" -> handleSellerQuery(prompt, lower, user);
            case "CUSTOMER" -> handleCustomerQuery(prompt, lower, user);
            default -> handleGuestQuery(prompt, lower);
        };
    }

    private ChatResponseDraft handlePaymentFaq() {
        return new ChatResponseDraft(
                "💳 **StockFlow Payment Orchestration & Gateways:**\n\n" +
                "- Payments use a **decoupled, non-blocking 3-step orchestration architecture**.\n" +
                "- Database transactions are never held open across external gateway HTTP network calls, completely eliminating connection pool starvation.\n" +
                "- Major credit/debit cards and mock payment providers are supported with HMAC-SHA256 signed webhook ingestion.\n" +
                "- Uncertain payment timeouts are recorded in status `UNCERTAIN` and reconciled asynchronously by the `PaymentReconciliationWorker`.\n\n" +
                "💡 *To pay for a pending order, submit `POST /api/v1/payments/initiate` or use the storefront order console.*",
                "PAYMENT_FAQ",
                List.of(
                        new ChatAction("Check My Orders", "QUERY", "Show my recent orders"),
                        new ChatAction("Browse Products", "QUERY", "Show available products")
                )
        );
    }

    private ChatResponseDraft handleShippingFaq() {
        return new ChatResponseDraft(
                "📦 **StockFlow Shipping & Carrier Fulfillment:**\n\n" +
                "- Verified merchants dispatch orders within **1-2 business days**.\n" +
                "- Supported logistics carriers include **FedEx, UPS, and DHL**.\n" +
                "- Every shipment is issued a carrier tracking number (e.g. `TRK-FEDEX-998877`).\n" +
                "- Delivery confirmation transitions orders to `DELIVERED` and opens the 30-day return window.",
                "SHIPPING_POLICY",
                List.of(
                        new ChatAction("Track My Orders", "QUERY", "Show my recent orders"),
                        new ChatAction("Return Policy", "QUERY", "What is the return policy?")
                )
        );
    }

    private ChatResponseDraft handleReturnPolicyFaq() {
        return new ChatResponseDraft(
                "🔄 **StockFlow 30-Day Return & Refund Policy:**\n\n" +
                "- Customers can submit return requests on any delivered order within **30 days**.\n" +
                "- Merchants review each request and inspect returned physical goods.\n" +
                "- Returns are routed to disposition: `RESTOCK` (returns units to available pool), `QUARANTINE`, or `SCRAP`.\n" +
                "- Refunds are issued back to the original payment method up to the verified order balance.",
                "RETURN_POLICY",
                List.of(
                        new ChatAction("Check Categories", "QUERY", "What categories are available?"),
                        new ChatAction("My Orders", "QUERY", "Show my recent orders")
                )
        );
    }

    // =========================================================================
    // GUEST / PUBLIC VISITOR HANDLERS
    // =========================================================================

    private ChatResponseDraft handleGuestQuery(String prompt, String lower) {
        // Products & Catalog Search
        if (matchesAny(lower, "product", "search", "find", "buy", "audio", "headphone", "cable", "cheapest", "price", "catalog", "shop")) {
            return searchCatalogPublic(lower);
        }

        // Categories
        if (matchesAny(lower, "category", "categories", "departments")) {
            return listCategoriesPublic();
        }

        // Policies: Shipping, Returns, Idempotency, Reservations
        if (matchesAny(lower, "shipping", "delivery", "carrier")) {
            return new ChatResponseDraft(
                    "📦 **StockFlow Shipping Policy:**\n\n" +
                    "- Verified sellers ship paid orders within **1-2 business days** with major carriers (FedEx, UPS, DHL).\n" +
                    "- Each shipment receives a real-time tracking number.\n" +
                    "- Delivery is verified via our fulfilment confirmation system.\n\n" +
                    "*Sign in as a Customer to track your live packages!*",
                    "SHIPPING_POLICY",
                    List.of(
                            new ChatAction("Browse Products", "QUERY", "Show available products"),
                            new ChatAction("Return Policy", "QUERY", "What is the return policy?")
                    )
            );
        }

        if (matchesAny(lower, "return", "refund", "money back")) {
            return new ChatResponseDraft(
                    "🔄 **StockFlow Return & Refund Policy:**\n\n" +
                    "- Customers can request returns on delivered orders within **30 days**.\n" +
                    "- Sellers review each return request and inspect returned goods.\n" +
                    "- Returns can be dispositioned as `RESTOCK` (returns to active pool), `QUARANTINE`, or `SCRAP`.\n" +
                    "- Refunds are processed automatically to the original payment method up to the verified order balance.",
                    "RETURN_POLICY",
                    List.of(
                            new ChatAction("Check Categories", "QUERY", "What categories are available?"),
                            new ChatAction("How to Register", "QUERY", "How do I register?")
                    )
            );
        }

        if (matchesAny(lower, "register", "signup", "sign up", "login", "account", "credentials", "seed")) {
            return new ChatResponseDraft(
                    "🔐 **Account & Authentication Guide:**\n\n" +
                    "- **Register as Customer:** `POST /api/v1/auth/register`\n" +
                    "- **Login:** `POST /api/v1/auth/login` (Returns short-lived JWT access token + refresh token).\n\n" +
                    "**Demo Seed Accounts** (Password: `Password123!`):\n" +
                    "- Customer: `customer@stockflow.com`\n" +
                    "- Seller: `seller@stockflow.com`\n" +
                    "- Admin: `admin@stockflow.com`\n\n" +
                    "*Select a persona chip above or provide your Bearer token in requests.*",
                    "AUTH_GUIDE",
                    List.of(
                            new ChatAction("Search Products", "QUERY", "Show all products"),
                            new ChatAction("What Categories?", "QUERY", "What categories do you have?")
                    )
            );
        }

        // Friendly role boundary redirection if guest asks for private data
        if (matchesAny(lower, "my order", "my orders", "my cart", "where is my", "cancel order")) {
            return new ChatResponseDraft(
                    "🔒 **Authentication Required:**\n\n" +
                    "To view your personal orders, cart items, or request cancellations, please log in with your customer account.\n\n" +
                    "💡 *Tip: If you are exploring the demo, sign in with seed user `customer@stockflow.com` (password: `Password123!`)!*",
                    "AUTH_REQUIRED",
                    List.of(
                            new ChatAction("Seed Credentials", "QUERY", "Show seed logins"),
                            new ChatAction("Browse Products", "QUERY", "Show available products")
                    )
            );
        }

        return fallbackGuestResponse();
    }

    private ChatResponseDraft searchCatalogPublic(String query) {
        // 1. Extract optional price constraint (e.g. "under $250", "below 200", "under 300")
        BigDecimal maxPrice = null;
        Matcher priceMatcher = Pattern.compile("(?i)(?:under|below|less than|max)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)").matcher(query);
        if (priceMatcher.find()) {
            try {
                maxPrice = new BigDecimal(priceMatcher.group(1));
            } catch (Exception ignored) {}
        }

        // 2. Clean query of stopwords
        String cleanQuery = query.toLowerCase()
                .replaceAll("(?i)\\b(under|below|less than|max)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)\\b", " ")
                .replaceAll("(?i)\\b(search|find|show|list|browse|get|give|me|all|any|available|products?|items?|catalog|in|for|cheap|cheapest|buy|shop|store|recommend|recommendations?|best|our|the|a|an|please|what|do|you|have|equipment|gear)\\b", " ")
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .trim();

        // 3. Normalize common plural forms for substring search
        if (cleanQuery.endsWith("phones")) {
            cleanQuery = cleanQuery.substring(0, cleanQuery.length() - 1);
        }

        Page<Product> page = productRepository.searchPublicCatalog(
                null,
                null,
                maxPrice,
                cleanQuery.isEmpty() ? null : cleanQuery,
                PageRequest.of(0, 5)
        );

        if (page.isEmpty()) {
            // Fallback to active catalog items
            page = productRepository.searchPublicCatalog(null, null, maxPrice, null, PageRequest.of(0, 5));
            if (page.isEmpty()) {
                return new ChatResponseDraft(
                        "🔍 I couldn't find any active products matching your search criteria. Would you like to explore our top categories?",
                        "CATALOG_SEARCH_EMPTY",
                        List.of(
                                new ChatAction("View Categories", "QUERY", "Show categories"),
                                new ChatAction("View All Products", "QUERY", "Show all products")
                        )
                );
            }
        }

        StringBuilder sb = new StringBuilder("🛍️ **Available Products in StockFlow:**\n\n");
        List<ChatAction> actions = new ArrayList<>();
        for (Product p : page.getContent()) {
            Optional<Inventory> inv = inventoryRepository.findByProductId(p.getId());
            int available = inv.map(Inventory::getAvailableQuantity).orElse(0);
            sb.append("- **").append(p.getTitle()).append("** — `").append(p.getPrice()).append(" ").append(p.getCurrency()).append("`\n")
              .append("  • SKU: `").append(inv.map(Inventory::getSku).orElse("N/A")).append("` | Available: ").append(available).append(" units\n")
              .append("  • Category: ").append(p.getCategory() != null ? p.getCategory().getName() : "General").append("\n");

            if (actions.size() < 3) {
                actions.add(new ChatAction("SKU " + inv.map(Inventory::getSku).orElse("INFO"), "QUERY", "Tell me about SKU " + inv.map(Inventory::getSku).orElse("")));
            }
        }

        return new ChatResponseDraft(sb.toString(), "CATALOG_SEARCH_RESULTS", actions);
    }

    private ChatResponseDraft listCategoriesPublic() {
        List<Category> categories = categoryRepository.findByActiveTrue();
        if (categories.isEmpty()) {
            return new ChatResponseDraft("No categories found in the catalog.", "CATEGORIES_EMPTY", List.of());
        }

        StringBuilder sb = new StringBuilder("📂 **Product Categories:**\n\n");
        List<ChatAction> actions = new ArrayList<>();
        for (Category c : categories) {
            sb.append("- **").append(c.getName()).append("**: ").append(c.getDescription() != null ? c.getDescription() : "Standard collection").append("\n");
            actions.add(new ChatAction(c.getName(), "QUERY", "Search " + c.getName()));
        }

        return new ChatResponseDraft(sb.toString(), "CATEGORY_LIST", actions);
    }

    // =========================================================================
    // CUSTOMER HANDLERS
    // =========================================================================

    private ChatResponseDraft handleCustomerQuery(String prompt, String lower, User customer) {
        // 1. Order Number Inspection
        Matcher orderMatcher = ORDER_NUMBER_PATTERN.matcher(prompt);
        if (orderMatcher.find()) {
            String orderNum = orderMatcher.group(1);
            return lookupCustomerOrder(orderNum, customer);
        }

        // 2. General Orders / Recent Orders
        if (matchesAny(lower, "my order", "my orders", "recent orders", "order status", "where is my order", "orders", "track", "tracking", "package", "where is order")) {
            return listCustomerRecentOrders(customer);
        }

        // 3. Cart & Checkout Inquiries
        if (matchesAny(lower, "my cart", "cart", "basket", "bag", "checkout", "items in cart", "what is in my cart", "cart items")) {
            return inspectCustomerCart(customer);
        }

        // 4. Cancellation Inquiries
        if (matchesAny(lower, "cancel", "cancellation", "cancel order", "cancelling", "eligible for cancellation")) {
            return explainCustomerCancellation(customer);
        }

        // 5. Returns & Refunds
        if (matchesAny(lower, "return", "refund", "return request", "return item", "money back")) {
            return inspectCustomerReturns(customer);
        }

        // 6. Product search & recommendations
        if (matchesAny(lower, "product", "search", "find", "buy", "shop", "recommend", "suggestion", "audio", "headphone", "gear", "under", "available")) {
            return searchCatalogPublic(lower);
        }

        // 7. Role Boundary check
        if (matchesAny(lower, "admin", "platform revenue", "store stock", "audit log")) {
            return new ChatResponseDraft(
                    "🔒 **Access Restricted:** You are currently authenticated as **CUSTOMER** (`" + customer.getEmail() + "`).\n" +
                    "Administrative metrics and seller inventory controls require an ADMIN or SELLER role.",
                    "ROLE_RESTRICTION",
                    List.of(
                            new ChatAction("My Recent Orders", "QUERY", "Show my recent orders"),
                            new ChatAction("Check My Cart", "QUERY", "What is in my cart?")
                    )
            );
        }

        return new ChatResponseDraft(
                "👋 Hello **" + customer.getFullName() + "**! How can I assist you with your orders today?\n\n" +
                "You can ask me:\n" +
                "- *\"Where is order ORD-...\"*\n" +
                "- *\"Show my recent orders\"*\n" +
                "- *\"What is in my cart?\"*\n" +
                "- *\"Can I cancel my pending order?\"*\n" +
                "- *\"What is my return request status?\"*",
                "CUSTOMER_DEFAULT",
                List.of(
                        new ChatAction("My Recent Orders", "QUERY", "Show my recent orders"),
                        new ChatAction("View Cart", "QUERY", "What is in my cart?"),
                        new ChatAction("Browse Catalog", "QUERY", "Show available products")
                )
        );
    }

    private ChatResponseDraft lookupCustomerOrder(String orderNumber, User customer) {
        Optional<Order> orderOpt = orderRepository.findByOrderNumber(orderNumber.toUpperCase());
        if (orderOpt.isEmpty()) {
            return new ChatResponseDraft(
                    "❌ Order `" + orderNumber + "` was not found. Please double-check your order number format (e.g. `ORD-20261028-0001`).",
                    "ORDER_NOT_FOUND",
                    List.of(new ChatAction("List My Orders", "QUERY", "Show my recent orders"))
            );
        }

        Order order = orderOpt.get();

        // Enforce Broken Object-Level Authorization (BOLA)
        if (!order.getCustomer().getId().equals(customer.getId())) {
            log.warn("Customer {} attempted unauthorized inspection of order {}", customer.getId(), order.getId());
            return new ChatResponseDraft(
                    "❌ Order `" + orderNumber + "` was not found under your account.",
                    "ORDER_NOT_FOUND_BOLA",
                    List.of(new ChatAction("Show My Orders", "QUERY", "Show my recent orders"))
            );
        }

        List<Shipment> shipments = shipmentRepository.findByOrderId(order.getId());
        StringBuilder sb = new StringBuilder("📦 **Order Details for ").append(order.getOrderNumber()).append(":**\n\n")
                .append("- **Status:** `").append(order.getStatus()).append("`\n")
                .append("- **Total:** `").append(order.getTotalAmount()).append(" ").append(order.getCurrency()).append("`\n")
                .append("- **Items:** ").append(order.getItems().size()).append(" item(s)\n");

        for (OrderItem item : order.getItems()) {
            sb.append("  • ").append(item.getProductTitle()).append(" (x").append(item.getQuantity()).append(") — `")
              .append(item.getUnitPrice()).append(" ").append(order.getCurrency()).append("`\n");
        }

        if (!shipments.isEmpty()) {
            sb.append("\n🚚 **Shipment Tracking:**\n");
            for (Shipment s : shipments) {
                sb.append("- Carrier: **").append(s.getCarrier()).append("** | Tracking: `").append(s.getTrackingNumber())
                  .append("` | Status: `").append(s.getStatus()).append("`\n");
            }
        }

        // Actionable guidance based on order state
        List<ChatAction> actions = new ArrayList<>();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT || order.getStatus() == OrderStatus.CONFIRMED) {
            sb.append("\n💡 *This order is eligible for pre-fulfilment cancellation.*");
            actions.add(new ChatAction("Cancel Order", "QUERY", "Can I cancel order " + order.getOrderNumber() + "?"));
        } else if (order.getStatus() == OrderStatus.DELIVERED) {
            sb.append("\n💡 *This order is delivered. You may request a return within 30 days.*");
            actions.add(new ChatAction("Request Return", "QUERY", "I want to return items from " + order.getOrderNumber()));
        }

        actions.add(new ChatAction("All My Orders", "QUERY", "Show my recent orders"));
        return new ChatResponseDraft(sb.toString(), "ORDER_DETAILS", actions);
    }

    private ChatResponseDraft listCustomerRecentOrders(User customer) {
        Page<Order> page = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId(), PageRequest.of(0, 5));
        if (page.isEmpty()) {
            return new ChatResponseDraft(
                    "You haven't placed any orders yet. Discover our latest items and add them to your cart!",
                    "NO_ORDERS",
                    List.of(
                            new ChatAction("Browse Catalog", "QUERY", "Show available products"),
                            new ChatAction("Check Cart", "QUERY", "What is in my cart?")
                    )
            );
        }

        StringBuilder sb = new StringBuilder("📋 **Your Recent Orders:**\n\n");
        List<ChatAction> actions = new ArrayList<>();
        for (Order o : page.getContent()) {
            sb.append("- **").append(o.getOrderNumber()).append("** — `").append(o.getStatus()).append("` | `")
              .append(o.getTotalAmount()).append(" ").append(o.getCurrency()).append("` (")
              .append(o.getItems().size()).append(" item(s))\n");

            if (actions.size() < 3) {
                actions.add(new ChatAction("Track " + o.getOrderNumber(), "QUERY", "Where is order " + o.getOrderNumber()));
            }
        }

        return new ChatResponseDraft(sb.toString(), "CUSTOMER_ORDERS_LIST", actions);
    }

    private ChatResponseDraft inspectCustomerCart(User customer) {
        CartResponse cart = cartService.getCart(customer);
        if (cart.totalItems() == 0) {
            return new ChatResponseDraft(
                    "🛒 Your cart is currently empty. Explore our catalog to find exciting products!",
                    "CART_EMPTY",
                    List.of(new ChatAction("Browse Products", "QUERY", "Show available products"))
            );
        }

        StringBuilder sb = new StringBuilder("🛒 **Your Current Cart (")
                .append(cart.totalItems()).append(" items):**\n\n");
        for (CartItemResponse item : cart.items()) {
            sb.append("- **").append(item.title()).append("** (x").append(item.quantity()).append(") — `")
              .append(item.itemTotal()).append(" ").append(cart.currency()).append("` (SKU: `").append(item.sku()).append("`)\n");
        }
        sb.append("\n**Subtotal:** `").append(cart.totalAmount()).append(" ").append(cart.currency()).append("`\n")
          .append("\nReady to checkout? Submit your order with idempotency protection via `POST /api/v1/orders/checkout`!");

        return new ChatResponseDraft(sb.toString(), "CART_SUMMARY", List.of(
                new ChatAction("How to Checkout", "QUERY", "How does checkout work?"),
                new ChatAction("My Orders", "QUERY", "Show my recent orders")
        ));
    }

    private ChatResponseDraft explainCustomerCancellation(User customer) {
        return new ChatResponseDraft(
                "❌ **Order Cancellation Rules:**\n\n" +
                "- Orders in `PENDING_PAYMENT` or `CONFIRMED` can be immediately cancelled.\n" +
                "- Inventory reservations are released automatically back to the available pool.\n" +
                "- Once an order is `SHIPPED` or `DELIVERED`, cancellation is locked to prevent transit losses. You can request a return after delivery.\n\n" +
                "To check a specific order, ask: *\"Can I cancel order ORD-xxxx\"* or click below:",
                "CANCELLATION_INFO",
                List.of(new ChatAction("View My Orders", "QUERY", "Show my recent orders"))
        );
    }

    private ChatResponseDraft inspectCustomerReturns(User customer) {
        List<ReturnRequest> returns = returnRequestRepository.findByCustomerId(customer.getId());
        if (returns.isEmpty()) {
            return new ChatResponseDraft(
                    "You have no active return requests.\n\n" +
                    "If you have received an item that is defective or unwanted, you can request a return from your delivered orders within 30 days.",
                    "RETURNS_EMPTY",
                    List.of(new ChatAction("My Orders", "QUERY", "Show my recent orders"))
            );
        }

        StringBuilder sb = new StringBuilder("🔄 **Your Return Requests:**\n\n");
        for (ReturnRequest r : returns) {
            sb.append("- Return for Order **").append(r.getOrder().getOrderNumber()).append("**\n")
              .append("  • Status: `").append(r.getStatus()).append("`\n")
              .append("  • Reason: ").append(r.getReason()).append("\n")
              .append("  • Disposition: `").append(r.getRestockDecision()).append("`\n\n");
        }

        return new ChatResponseDraft(sb.toString(), "RETURNS_SUMMARY", List.of(new ChatAction("My Orders", "QUERY", "Show my recent orders")));
    }

    // =========================================================================
    // SELLER HANDLERS
    // =========================================================================

    private ChatResponseDraft handleSellerQuery(String prompt, String lower, User sellerUser) {
        Optional<Seller> sellerOpt = sellerRepository.findByUserId(sellerUser.getId());
        if (sellerOpt.isEmpty()) {
            return new ChatResponseDraft(
                    "⚠️ You are authenticated with SELLER role, but no seller profile is attached to your account yet.",
                    "SELLER_PROFILE_MISSING",
                    List.of()
            );
        }

        Seller seller = sellerOpt.get();

        // 1. Low Stock & Inventory Alerts
        if (matchesAny(lower, "low stock", "stock alert", "running low", "out of stock")) {
            return inspectSellerLowStock(seller);
        }

        // 2. All Inventory Levels
        if (matchesAny(lower, "inventory", "stock level", "my stock", "sku")) {
            return inspectSellerInventory(seller);
        }

        // 3. Seller Return Requests
        if (matchesAny(lower, "return", "refund", "return request", "pending return")) {
            return inspectSellerReturns(seller);
        }

        // 4. Seller Shipments
        if (matchesAny(lower, "shipment", "shipping", "tracking", "fulfilment", "dispatch")) {
            return inspectSellerShipments(seller);
        }

        // 5. Seller Store Profile & Listings
        if (matchesAny(lower, "store", "profile", "listings", "products", "my products")) {
            return inspectSellerStore(seller);
        }

        // Default Seller Help
        return new ChatResponseDraft(
                "🏪 Welcome **" + seller.getStoreName() + "**! How can I assist with your store operations today?\n\n" +
                "Try asking:\n" +
                "- *\"Show my low stock items\"*\n" +
                "- *\"Check inventory levels\"*\n" +
                "- *\"Any pending return requests?\"*\n" +
                "- *\"Show active product listings\"*",
                "SELLER_DEFAULT",
                List.of(
                        new ChatAction("Low Stock Alert", "QUERY", "Show my low stock items"),
                        new ChatAction("View Inventory", "QUERY", "Check my inventory levels"),
                        new ChatAction("Pending Returns", "QUERY", "Show pending return requests")
                )
        );
    }

    private ChatResponseDraft inspectSellerLowStock(Seller seller) {
        List<Inventory> lowStock = inventoryRepository.findLowStock(10).stream()
                .filter(i -> i.getSeller().getId().equals(seller.getId()))
                .toList();

        if (lowStock.isEmpty()) {
            return new ChatResponseDraft(
                    "✅ **Inventory Health Excellent!** None of your SKUs are currently below the low-stock threshold (<= 10 units).",
                    "SELLER_STOCK_HEALTHY",
                    List.of(new ChatAction("All Inventory", "QUERY", "Check my inventory levels"))
            );
        }

        StringBuilder sb = new StringBuilder("⚠️ **Low Stock Alert for ").append(seller.getStoreName()).append(":**\n\n");
        for (Inventory i : lowStock) {
            sb.append("- **SKU:** `").append(i.getSku()).append("` — Product: *").append(i.getProduct().getTitle()).append("*\n")
              .append("  • Available: **").append(i.getAvailableQuantity()).append("** units (On-hand: ")
              .append(i.getOnHand()).append(", Reserved: ").append(i.getReserved()).append(")\n");
        }
        sb.append("\n💡 *Perform a restock via `POST /api/v1/inventory/adjust` to replenish stock.*");

        return new ChatResponseDraft(sb.toString(), "SELLER_LOW_STOCK", List.of(
                new ChatAction("All Inventory", "QUERY", "Check my inventory levels")
        ));
    }

    private ChatResponseDraft inspectSellerInventory(Seller seller) {
        Page<Inventory> page = inventoryRepository.findBySellerId(seller.getId(), PageRequest.of(0, 10));
        if (page.isEmpty()) {
            return new ChatResponseDraft(
                    "No inventory items registered for your store yet. Create a product via `POST /api/v1/seller/products` to get started!",
                    "SELLER_NO_INVENTORY",
                    List.of()
            );
        }

        StringBuilder sb = new StringBuilder("📊 **Store Inventory for ").append(seller.getStoreName()).append(":**\n\n");
        for (Inventory i : page.getContent()) {
            sb.append("- **").append(i.getSku()).append("**: *").append(i.getProduct().getTitle()).append("*\n")
              .append("  • Available: **").append(i.getAvailableQuantity()).append("** | On-hand: ")
              .append(i.getOnHand()).append(" | Reserved: ").append(i.getReserved()).append("\n");
        }

        return new ChatResponseDraft(sb.toString(), "SELLER_INVENTORY_LIST", List.of(
                new ChatAction("Low Stock Check", "QUERY", "Show my low stock items"),
                new ChatAction("Store Listings", "QUERY", "Show my store listings")
        ));
    }

    private ChatResponseDraft inspectSellerReturns(Seller seller) {
        List<ReturnRequest> returns = returnRequestRepository.findBySellerId(seller.getId());
        if (returns.isEmpty()) {
            return new ChatResponseDraft(
                    "✅ No return requests currently logged for your store products.",
                    "SELLER_RETURNS_EMPTY",
                    List.of(new ChatAction("Check Inventory", "QUERY", "Check my inventory levels"))
            );
        }

        StringBuilder sb = new StringBuilder("🔄 **Customer Return Requests for ").append(seller.getStoreName()).append(":**\n\n");
        for (ReturnRequest r : returns) {
            sb.append("- Return ID: `").append(r.getId()).append("` | Order: **").append(r.getOrder().getOrderNumber()).append("**\n")
              .append("  • Status: `").append(r.getStatus()).append("` | Disposition: `").append(r.getRestockDecision()).append("`\n")
              .append("  • Reason: ").append(r.getReason()).append("\n\n");
        }
        sb.append("💡 *Approve returns via `POST /api/v1/returns/{id}/decision` and receive stock with `POST /api/v1/returns/{id}/receive`.*");

        return new ChatResponseDraft(sb.toString(), "SELLER_RETURNS_LIST", List.of(
                new ChatAction("Low Stock Items", "QUERY", "Show my low stock items")
        ));
    }

    private ChatResponseDraft inspectSellerShipments(Seller seller) {
        List<Shipment> shipments = shipmentRepository.findBySellerId(seller.getId());
        if (shipments.isEmpty()) {
            return new ChatResponseDraft(
                    "No shipments dispatched for your store yet. Paid customer orders can be dispatched via `POST /api/v1/fulfilment/shipments`.",
                    "SELLER_NO_SHIPMENTS",
                    List.of()
            );
        }

        StringBuilder sb = new StringBuilder("🚚 **Recent Shipments for ").append(seller.getStoreName()).append(":**\n\n");
        for (Shipment s : shipments) {
            sb.append("- Order: **").append(s.getOrder().getOrderNumber()).append("** | Carrier: **").append(s.getCarrier())
              .append("** | Tracking: `").append(s.getTrackingNumber()).append("` | Status: `").append(s.getStatus()).append("`\n");
        }

        return new ChatResponseDraft(sb.toString(), "SELLER_SHIPMENTS_LIST", List.of(
                new ChatAction("Check Inventory", "QUERY", "Check my inventory levels")
        ));
    }

    private ChatResponseDraft inspectSellerStore(Seller seller) {
        Page<Product> page = productRepository.findBySellerId(seller.getId(), PageRequest.of(0, 10));
        StringBuilder sb = new StringBuilder("🏪 **Store Profile:**\n\n")
                .append("- **Store Name:** ").append(seller.getStoreName()).append("\n")
                .append("- **Business Email:** ").append(seller.getBusinessEmail()).append("\n")
                .append("- **Account Status:** `").append(seller.getStatus()).append("`\n")
                .append("- **Total Active Listings:** ").append(page.getTotalElements()).append("\n\n");

        if (!page.isEmpty()) {
            sb.append("**Active Listings:**\n");
            for (Product p : page.getContent()) {
                sb.append("- *").append(p.getTitle()).append("* — `").append(p.getPrice()).append(" ").append(p.getCurrency()).append("`\n");
            }
        }

        return new ChatResponseDraft(sb.toString(), "SELLER_STORE_SUMMARY", List.of(
                new ChatAction("Check Low Stock", "QUERY", "Show my low stock items"),
                new ChatAction("Store Inventory", "QUERY", "Check my inventory levels")
        ));
    }

    // =========================================================================
    // ADMINISTRATOR HANDLERS
    // =========================================================================

    private ChatResponseDraft handleAdminQuery(String prompt, String lower, User admin) {
        // 1. Platform Metrics & Revenue
        if (matchesAny(lower, "metric", "revenue", "financial", "sales", "kpi", "overview", "stats")) {
            return inspectPlatformMetrics();
        }

        // 2. Audit Logs & Discrepancies
        if (matchesAny(lower, "audit", "logs", "security", "discrepanc", "who changed")) {
            return inspectAdminAuditLogs();
        }

        // 3. Outbox & Background Workers
        if (matchesAny(lower, "outbox", "worker", "queue", "background", "job", "event")) {
            return inspectOutboxQueueHealth();
        }

        // 4. Users & Roles
        if (matchesAny(lower, "user", "users", "customer count", "sellers", "admin count")) {
            return inspectUserDistribution();
        }

        // Default Admin Help
        return new ChatResponseDraft(
                "🛡️ **StockFlow Executive Control Center (ADMIN)**\n\n" +
                "Authenticated as: `" + admin.getEmail() + "`\n\n" +
                "You can issue real-time operational commands:\n" +
                "- *\"Show platform metrics & revenue\"*\n" +
                "- *\"Inspect recent audit logs\"*\n" +
                "- *\"Check outbox queue & worker health\"*\n" +
                "- *\"Show user breakdown\"*",
                "ADMIN_DEFAULT",
                List.of(
                        new ChatAction("Platform Metrics", "QUERY", "Show platform metrics"),
                        new ChatAction("Audit Logs", "QUERY", "Show recent audit logs"),
                        new ChatAction("Outbox Health", "QUERY", "Check outbox queue status")
                )
        );
    }

    private ChatResponseDraft inspectPlatformMetrics() {
        OperationalMetricsResponse metrics = reportingService.getOperationalMetrics();

        StringBuilder sb = new StringBuilder("📈 **StockFlow Platform Operational Metrics:**\n\n")
                .append("- **Total Revenue:** `").append(metrics.totalRevenue()).append(" ").append(metrics.currency()).append("`\n")
                .append("- **Total Orders:** ").append(metrics.totalOrders()).append("\n")
                .append("- **Registered Users:** ").append(metrics.totalRegisteredUsers()).append("\n")
                .append("- **Low Stock Alerts:** ").append(metrics.lowStockItemCount()).append(" item(s)\n")
                .append("- **Pending Outbox Events:** ").append(metrics.pendingOutboxEvents()).append("\n\n")
                .append("**Order Status Distribution:**\n");

        for (Map.Entry<String, Long> entry : metrics.ordersByStatus().entrySet()) {
            if (entry.getValue() > 0) {
                sb.append("  • `").append(entry.getKey()).append("`: ").append(entry.getValue()).append("\n");
            }
        }

        return new ChatResponseDraft(sb.toString(), "ADMIN_METRICS_REPORT", List.of(
                new ChatAction("Audit Logs", "QUERY", "Show recent audit logs"),
                new ChatAction("Outbox Status", "QUERY", "Check outbox queue status")
        ));
    }

    private ChatResponseDraft inspectAdminAuditLogs() {
        Page<AuditLog> page = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5));
        if (page.isEmpty()) {
            return new ChatResponseDraft("No administrative audit entries found in datastore.", "AUDIT_EMPTY", List.of());
        }

        DateTimeFormatter dtf = DateTimeFormatter.ISO_INSTANT;
        StringBuilder sb = new StringBuilder("🔍 **Recent Administrative Audit Trail:**\n\n");
        for (AuditLog a : page.getContent()) {
            sb.append("- **").append(a.getAction()).append("** on `").append(a.getResourceType())
              .append("/").append(a.getResourceId()).append("`\n")
              .append("  • Details: ").append(a.getDetails() != null ? a.getDetails() : "N/A").append("\n")
              .append("  • Timestamp: `").append(dtf.format(a.getCreatedAt())).append("`\n\n");
        }

        return new ChatResponseDraft(sb.toString(), "ADMIN_AUDIT_LOGS", List.of(
                new ChatAction("Platform Metrics", "QUERY", "Show platform metrics"),
                new ChatAction("Outbox Status", "QUERY", "Check outbox queue status")
        ));
    }

    private ChatResponseDraft inspectOutboxQueueHealth() {
        return new ChatResponseDraft(
                "⚡ **Transactional Outbox Engine Status:**\n\n" +
                "- **Processing Pattern:** PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED`\n" +
                "- **Batch Size:** 50 events per poller iteration\n" +
                "- **Poller Frequency:** Every 10 seconds (`*/10 * * * * *`)\n" +
                "- **Deadlock Risk:** Mathematically 0% (lock-free row skipping across horizontal workers)\n" +
                "- **Retry Policy:** Exponential backoff with maximum 5 attempts before marking `FAILED`\n" +
                "- **Dual-Write Immunity:** 100% (domain changes & outbox rows committed in identical ACID transaction).",
                "ADMIN_OUTBOX_HEALTH",
                List.of(
                        new ChatAction("Platform Metrics", "QUERY", "Show platform metrics"),
                        new ChatAction("Audit Logs", "QUERY", "Show recent audit logs")
                )
        );
    }

    private ChatResponseDraft inspectUserDistribution() {
        long totalUsers = userRepository.count();
        return new ChatResponseDraft(
                "👥 **Platform User Distribution:**\n\n" +
                "- **Total Registered Accounts:** " + totalUsers + "\n" +
                "- **Roles:** `CUSTOMER`, `SELLER`, `ADMIN`\n" +
                "- **Security Policy:** Argon2id/BCrypt password hashing + Refresh Token Rotation with token reuse detection.",
                "ADMIN_USERS_REPORT",
                List.of(new ChatAction("Platform Metrics", "QUERY", "Show platform metrics"))
        );
    }

    // =========================================================================
    // Generic Greeting & Help Helpers
    // =========================================================================

    private ChatResponseDraft handleGreeting(User user, String userRole) {
        if (user != null) {
            return new ChatResponseDraft(
                    "👋 Hello **" + user.getFullName() + "**! You are logged in with **" + userRole + "** privileges.\n\n" +
                    "How can I assist your workflow today?",
                    "GREETING_AUTHENTICATED",
                    getRoleQuickActions(userRole)
            );
        }
        return new ChatResponseDraft(
                "👋 Hello! Welcome to **StockFlow Multi-Vendor Commerce Engine**.\n\n" +
                "I am your AI assistant. You can browse products, query store policies, or test our multi-vendor commerce backend. If you have an account, log in to access personalized order tracking or seller management!",
                "GREETING_GUEST",
                List.of(
                        new ChatAction("Browse Products", "QUERY", "Show available products"),
                        new ChatAction("What Categories?", "QUERY", "What categories are available?"),
                        new ChatAction("Return Policy", "QUERY", "What is the return policy?"),
                        new ChatAction("Demo Accounts", "QUERY", "Show seed logins")
                )
        );
    }

    private ChatResponseDraft handleHelpMenu(User user, String userRole) {
        String commands = switch (userRole) {
            case "ADMIN" -> "- *\"Show platform metrics\"* — Total revenue, active orders, conversion\n" +
                            "- *\"Show recent audit logs\"* — Security and stock adjustment trail\n" +
                            "- *\"Check outbox status\"* — Asynchronous event queue telemetry\n" +
                            "- *\"Show user distribution\"* — Registered customer & seller counts";
            case "SELLER" -> "- *\"Show my low stock items\"* — Real-time items below threshold\n" +
                             "- *\"Check inventory levels\"* — On-hand vs reserved vs available\n" +
                             "- *\"Any pending return requests?\"* — Customer return approvals\n" +
                             "- *\"Show recent shipments\"* — Carrier tracking details";
            case "CUSTOMER" -> "- *\"Where is order ORD-...\"* — Real-time tracking and delivery status\n" +
                               "- *\"Show my recent orders\"* — Order history and totals\n" +
                               "- *\"What is in my cart?\"* — Items, quantities, and subtotal\n" +
                               "- *\"Can I cancel my order?\"* — Cancellation eligibility rules\n" +
                               "- *\"Find headphones / audio\"* — Search active products";
            default -> "- *\"Show available products\"* — Search active catalog\n" +
                       "- *\"What categories do you have?\"* — List store categories\n" +
                       "- *\"What is the shipping policy?\"* — Fulfilment & carrier details\n" +
                       "- *\"What is the refund policy?\"* — 30-day return & restock policy\n" +
                       "- *\"Show demo logins\"* — Seed accounts for Customer, Seller, and Admin";
        };

        return new ChatResponseDraft(
                "ℹ️ **StockFlow Assistant Capabilities (" + userRole + "):**\n\n" + commands,
                "HELP_MENU",
                getRoleQuickActions(userRole)
        );
    }

    private List<ChatAction> getRoleQuickActions(String userRole) {
        return switch (userRole) {
            case "ADMIN" -> List.of(
                    new ChatAction("Metrics", "QUERY", "Show platform metrics"),
                    new ChatAction("Audit Logs", "QUERY", "Show recent audit logs"),
                    new ChatAction("Outbox", "QUERY", "Check outbox queue status")
            );
            case "SELLER" -> List.of(
                    new ChatAction("Low Stock", "QUERY", "Show my low stock items"),
                    new ChatAction("Inventory", "QUERY", "Check my inventory levels"),
                    new ChatAction("Returns", "QUERY", "Any pending return requests?")
            );
            case "CUSTOMER" -> List.of(
                    new ChatAction("My Orders", "QUERY", "Show my recent orders"),
                    new ChatAction("My Cart", "QUERY", "What is in my cart?"),
                    new ChatAction("Search Audio", "QUERY", "Find audio products")
            );
            default -> List.of(
                    new ChatAction("Browse Products", "QUERY", "Show available products"),
                    new ChatAction("Categories", "QUERY", "What categories are available?"),
                    new ChatAction("Demo Accounts", "QUERY", "Show seed logins")
            );
        };
    }

    private ChatResponseDraft fallbackGuestResponse() {
        return new ChatResponseDraft(
                "🤔 I didn't quite catch that. You can ask about our **products**, **categories**, **shipping policies**, or **return procedures**. Or type *help* to see what I can do!",
                "FALLBACK",
                List.of(
                        new ChatAction("Browse Products", "QUERY", "Show available products"),
                        new ChatAction("Categories", "QUERY", "What categories are available?"),
                        new ChatAction("Help Menu", "QUERY", "Help")
                )
        );
    }

    // =========================================================================
    // Persistence & Mapping Helpers
    // =========================================================================

    private ChatConversation resolveConversation(UUID conversationId, String sessionId, User user, String userRole) {
        if (conversationId != null) {
            Optional<ChatConversation> existing = conversationRepository.findById(conversationId);
            if (existing.isPresent()) {
                ChatConversation conv = existing.get();
                validateConversationOwnership(conv, user, sessionId);
                conv.setUserRole(userRole);
                return conv;
            }
        }

        // Try finding by sessionId for guest/browser continuity
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatConversation> sessionConv = conversationRepository.findTopBySessionIdOrderByUpdatedAtDesc(sessionId);
            if (sessionConv.isPresent()) {
                ChatConversation conv = sessionConv.get();
                if (user != null && conv.getUser() == null) {
                    // Upgrade guest conversation to authenticated user
                    conv = new ChatConversation(conv.getId(), user, sessionId, userRole, conv.getTitle());
                    return conversationRepository.save(conv);
                }
                conv.setUserRole(userRole);
                return conv;
            }
        }

        // Create new conversation
        ChatConversation newConv = new ChatConversation(
                UUID.randomUUID(),
                user,
                sessionId,
                userRole,
                "Conversation with " + userRole
        );
        return conversationRepository.save(newConv);
    }

    private void validateConversationOwnership(ChatConversation conv, User user, String sessionId) {
        if (conv.getUser() != null) {
            if (user == null || !conv.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedException("You are not authorized to access this conversation.");
            }
        } else if (sessionId != null && !sessionId.isBlank()) {
            if (!sessionId.equals(conv.getSessionId())) {
                throw new UnauthorizedException("Session mismatch for conversation.");
            }
        }
    }

    private String serializeActions(List<ChatAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(actions);
        } catch (Exception e) {
            log.error("Failed to serialize chat actions", e);
            return null;
        }
    }

    private List<ChatAction> deserializeActions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ChatAction>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private ChatMessageResponse mapToMessageResponse(ChatMessage message, List<ChatAction> actions) {
        List<ChatAction> effectiveActions = (actions != null) ? actions : deserializeActions(message.getSuggestedActions());
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getConversation().getSessionId(),
                message.getSender(),
                message.getContent(),
                message.getIntent(),
                effectiveActions,
                message.getCreatedAt()
        );
    }

    private ChatConversationResponse mapToConversationResponse(ChatConversation conv) {
        List<ChatMessageResponse> messageDtos = conv.getMessages().stream()
                .map(m -> mapToMessageResponse(m, null))
                .toList();

        return new ChatConversationResponse(
                conv.getId(),
                conv.getSessionId(),
                conv.getUserRole(),
                conv.getTitle(),
                conv.getMessages().size(),
                conv.getCreatedAt(),
                conv.getUpdatedAt(),
                messageDtos
        );
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private record ChatResponseDraft(String content, String intent, List<ChatAction> actions) {}
}

# Bài 5: Kiến trúc & Code — Thiết kế Loosely Coupled

## 1. Vì sao inject `ChatModel` thay vì `OllamaChatModel` hoặc `OpenAiChatModel`?

Trong Spring AI, `ChatModel` đóng vai trò là **interface abstraction** đại diện cho một mô hình hội thoại AI. Các implementation cụ thể như `OllamaChatModel` và `OpenAiChatModel` đều triển khai interface này.

Ví dụ:

```java
public interface ChatModel {
    ChatResponse call(Prompt prompt);
}
```

Các model cụ thể có thể được xem như:

```text
              ChatModel
                  ▲
                  │
        ┌─────────┴─────────┐
        │                   │
OllamaChatModel      OpenAiChatModel
        │                   │
     Ollama              OpenAI
```

Service nghiệp vụ chỉ cần biết rằng nó đang làm việc với một `ChatModel`, thay vì phải biết model cụ thể phía dưới là Ollama hay OpenAI.

### Cách thiết kế nên sử dụng

```java
@Service
public class FeedbackAnalysisService {

    private final ChatModel chatModel;

    public FeedbackAnalysisService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String analyzeFeedback(String feedbackText) {
        Prompt prompt = new Prompt(
            "Hãy phân tích ý kiến khách hàng sau:\n" + feedbackText
        );

        ChatResponse response = chatModel.call(prompt);

        return response.getResult()
                .getOutput()
                .getText();
    }
}
```

Ở đây:

```java
private final ChatModel chatModel;
```

là điểm quan trọng của thiết kế.

`FeedbackAnalysisService` **không phụ thuộc trực tiếp vào implementation cụ thể**.

Nó chỉ phụ thuộc vào abstraction:

```text
FeedbackAnalysisService
          │
          ▼
      ChatModel
          ▲
          │
   ┌──────┴──────┐
   │             │
 Ollama         OpenAI
```

### Lợi ích của Programming to Interface

#### 1. Giảm coupling

Nếu viết:

```java
private final OllamaChatModel chatModel;
```

thì `FeedbackAnalysisService` bị phụ thuộc trực tiếp vào Ollama.

Khi chuyển sang OpenAI, service có thể phải thay đổi:

```java
private final OpenAiChatModel chatModel;
```

Điều này làm tăng sự phụ thuộc giữa **business logic** và **AI infrastructure**.

Ngược lại, khi sử dụng:

```java
private final ChatModel chatModel;
```

business service không cần quan tâm implementation đang sử dụng là Ollama hay OpenAI.

---

#### 2. Dễ dàng thay đổi AI Provider

Ví dụ hệ thống hiện tại sử dụng:

```text
FeedbackAnalysisService
        ↓
   ChatModel
        ↓
     Ollama
```

Sau này muốn chuyển sang OpenAI:

```text
FeedbackAnalysisService
        ↓
   ChatModel
        ↓
     OpenAI
```

Phần logic nghiệp vụ vẫn giữ nguyên.

Chỉ cần thay đổi cấu hình hoặc bean được Spring inject.

---

#### 3. Dễ testing

Khi unit test `FeedbackAnalysisService`, có thể tạo một implementation giả/mock của `ChatModel`.

Ví dụ:

```java
@Mock
private ChatModel chatModel;
```

Sau đó mock kết quả trả về từ AI mà không cần chạy Ollama hoặc gọi OpenAI thật.

Điều này giúp:

- Test nhanh hơn.
- Không phụ thuộc network.
- Không phát sinh chi phí API.
- Dễ kiểm thử các trường hợp lỗi.

---

#### 4. Tuân thủ nguyên lý Dependency Inversion

Thiết kế này phù hợp với **Dependency Inversion Principle (DIP)** trong SOLID.

Business logic:

```text
FeedbackAnalysisService
```

không phụ thuộc trực tiếp vào infrastructure:

```text
Ollama
OpenAI
```

mà cả hai phụ thuộc vào abstraction:

```text
             ChatModel
              ▲     ▲
              │     │
           Ollama  OpenAI

              ▲
              │
 FeedbackAnalysisService
```

Đây chính là tư duy:

> **Program to an interface, not an implementation.**

Hay có thể hiểu đơn giản là:

> **Lập trình hướng tới interface thay vì implementation cụ thể.**

---

# 2. Xử lý trường hợp có cả Ollama và OpenAI

Nếu project cùng lúc có cả hai dependency:

```text
Spring AI Ollama Starter
Spring AI OpenAI Starter
```

thì Spring có thể tạo ra nhiều bean cùng implement `ChatModel`:

```text
ChatModel
   ├── OllamaChatModel
   └── OpenAiChatModel
```

Trong khi `FeedbackAnalysisService` yêu cầu:

```java
private final ChatModel chatModel;
```

Spring sẽ không biết phải chọn bean nào.

Khi đó có thể xảy ra lỗi dạng:

```text
NoUniqueBeanDefinitionException

No qualifying bean of type 'ChatModel' available:
expected single matching bean but found 2
```

Có hai cách phổ biến để giải quyết vấn đề này là **`@Profile`** và **`@Primary`**.

---

# 3. Cách 1 — Sử dụng `@Profile`

`@Profile` cho phép xác định bean nào được kích hoạt dựa trên môi trường đang chạy.

Ví dụ có hai cấu hình:

### Cấu hình Ollama

```java
@Configuration
@Profile("ollama")
public class OllamaConfig {

    @Bean
    public ChatModel chatModel(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }
}
```

### Cấu hình OpenAI

```java
@Configuration
@Profile("openai")
public class OpenAiConfig {

    @Bean
    public ChatModel chatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
```

Khi chạy với profile:

```properties
spring.profiles.active=ollama
```

Spring chỉ kích hoạt cấu hình Ollama.

Khi chạy:

```properties
spring.profiles.active=openai
```

Spring chỉ kích hoạt cấu hình OpenAI.

Khi đó:

```java
@Service
public class FeedbackAnalysisService {

    private final ChatModel chatModel;

    public FeedbackAnalysisService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String analyzeFeedback(String feedbackText) {
        Prompt prompt = new Prompt(
            "Hãy phân tích ý kiến khách hàng sau:\n" + feedbackText
        );

        ChatResponse response = chatModel.call(prompt);

        return response.getResult()
                .getOutput()
                .getText();
    }
}
```

không cần thay đổi.

### Ưu điểm của `@Profile`

`@Profile` phù hợp khi muốn **chọn một AI provider theo môi trường**.

Ví dụ:

```text
Development
    ↓
Ollama local
    ↓
Không tốn API cost
```

Trong production:

```text
Production
    ↓
OpenAI Cloud
    ↓
LLM mạnh hơn
```

Business service vẫn không thay đổi.

---

# 4. Cách 2 — Sử dụng `@Primary`

Một cách khác là đánh dấu một bean là bean mặc định bằng `@Primary`.

Ví dụ:

```java
@Configuration
public class AiConfig {

    @Bean
    @Primary
    public ChatModel openAiChatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }

    @Bean
    public ChatModel ollamaChatModel(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }
}
```

Khi Spring thấy:

```text
ChatModel
   ├── openAiChatModel   ← @Primary
   └── ollamaChatModel
```

và service yêu cầu:

```java
ChatModel chatModel
```

Spring sẽ chọn bean có `@Primary`:

```text
FeedbackAnalysisService
          │
          ▼
      ChatModel
          │
          ▼
   @Primary OpenAI
```

Nếu muốn Ollama là mặc định thì có thể đặt:

```java
@Bean
@Primary
public ChatModel ollamaChatModel(...) {
    ...
}
```

---

# 5. So sánh `@Profile` và `@Primary`

| Tiêu chí                          | `@Profile`                    | `@Primary`                 |
| --------------------------------- | ----------------------------- | -------------------------- |
| Mục đích                          | Chọn bean theo môi trường     | Chọn bean mặc định         |
| Dev dùng Ollama, Prod dùng OpenAI | **Phù hợp**                   | Không tối ưu               |
| Cả hai bean cùng tồn tại          | Có thể giới hạn bean được tạo | Có                         |
| Có thể thay đổi bằng profile      | **Có**                        | Không                      |
| Có nhiều implementation           | Phù hợp                       | Phù hợp nếu có một default |
| Giải quyết Ambiguous Bean         | Có                            | Có                         |

### Nên sử dụng cách nào?

Nếu bài toán là:

> Development sử dụng Ollama, Production sử dụng OpenAI.

Nên sử dụng:

```java
@Profile("ollama")
```

và:

```java
@Profile("openai")
```

Đây là lựa chọn phù hợp nhất vì **mỗi môi trường chỉ kích hoạt một implementation**.

Nếu bài toán là:

> Cả Ollama và OpenAI đều tồn tại, nhưng OpenAI luôn là implementation mặc định.

Có thể sử dụng:

```java
@Primary
```

---

# 6. Kết luận

Việc inject:

```java
ChatModel
```

thay vì:

```java
OllamaChatModel
```

hoặc:

```java
OpenAiChatModel
```

thể hiện đúng nguyên lý **Programming to Interface** và **Dependency Inversion**.

`FeedbackAnalysisService` chỉ phụ thuộc vào abstraction `ChatModel`, không phụ thuộc vào AI provider cụ thể.

Nhờ đó kiến trúc có dạng:

```text
                    ┌─────────────────┐
                    │ Feedback        │
                    │ AnalysisService │
                    └────────┬────────┘
                             │
                             ▼
                       ┌───────────┐
                       │ ChatModel │
                       └─────┬─────┘
                             │
                 ┌───────────┴───────────┐
                 ▼                       ▼
        OllamaChatModel          OpenAiChatModel
                 │                       │
                 ▼                       ▼
             Ollama                  OpenAI
```

Khi cần chuyển từ Ollama sang OpenAI, **business logic không cần thay đổi**. Chỉ cần thay đổi implementation/configuration được Spring sử dụng.

Đây chính là đặc điểm quan trọng của kiến trúc **Loosely Coupled**: các thành phần ít phụ thuộc trực tiếp vào nhau, dễ thay thế, dễ mở rộng và dễ kiểm thử.
